package model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * =====================================================================
 * ProductoModel — Capa MODEL del módulo Producto.
 * ---------------------------------------------------------------------
 * HU-03 Agregar Producto (Admin) · HU-06 Consultar Producto.
 *
 * Responsabilidad única (SRP): describir los datos del catálogo (entidad,
 * DTOs y repositorio). Los precios son SIEMPRE BigDecimal en Bolívares:
 * nunca double, porque double introduce errores de redondeo en dinero.
 *
 * SOLID: DIP — la lógica (control) depende de {@link ProductoRepository},
 * no de una implementación concreta de base de datos.
 * =====================================================================
 */
public final class ProductoModel {

    private ProductoModel() { }

    // -----------------------------------------------------------------
    // ENTIDAD JPA → tabla "producto"
    // -----------------------------------------------------------------

    @Entity(name = "Producto")
    @Table(name = "producto")
    public static class Producto {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Column(nullable = false, unique = true, length = 30)
        private String codigo;

        @Column(nullable = false, length = 120)
        private String nombre;

        @Column(length = 500)
        private String descripcion;

        @Column(nullable = false, length = 50)
        private String categoria;

        /** Precio unitario en Bs. (NUMERIC(12,2)). */
        @Column(nullable = false, precision = 12, scale = 2)
        private BigDecimal precio;

        @Column(nullable = false)
        private Integer stock;

        @Column(name = "imagen_url", nullable = false, length = 500)
        private String imagenUrl;

        @Column(name = "fecha_creacion", nullable = false)
        private LocalDateTime fechaCreacion = LocalDateTime.now();

        public Long getId() { return id; }
        public String getCodigo() { return codigo; }
        public void setCodigo(String codigo) { this.codigo = codigo; }
        public String getNombre() { return nombre; }
        public void setNombre(String nombre) { this.nombre = nombre; }
        public String getDescripcion() { return descripcion; }
        public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
        public String getCategoria() { return categoria; }
        public void setCategoria(String categoria) { this.categoria = categoria; }
        public BigDecimal getPrecio() { return precio; }
        public void setPrecio(BigDecimal precio) { this.precio = precio; }
        public Integer getStock() { return stock; }
        public void setStock(Integer stock) { this.stock = stock; }
        public String getImagenUrl() { return imagenUrl; }
        public void setImagenUrl(String imagenUrl) { this.imagenUrl = imagenUrl; }

        /**
         * Regla de inventario encapsulada en la propia entidad (POO: "Tell, don't ask").
         * @param cantidad unidades solicitadas
         * @return true si el stock actual alcanza para la cantidad pedida
         */
        public boolean tieneStock(int cantidad) {
            return stock != null && cantidad > 0 && stock >= cantidad;
        }

        /**
         * Descuenta unidades del inventario (al registrar una orden).
         * Lanza excepción si se intentara dejar el stock en negativo:
         * la entidad protege su propia consistencia.
         */
        public void descontarStock(int cantidad) {
            if (!tieneStock(cantidad)) {
                throw new IllegalStateException("Stock insuficiente de " + nombre);
            }
            this.stock -= cantidad;
        }
    }

    // -----------------------------------------------------------------
    // DTOs
    // -----------------------------------------------------------------

    /**
     * Formulario "Registro de nuevo producto" (HU-03).
     * Reglas: precio > 0, stock > 0, código obligatorio e imagen obligatoria.
     * La unicidad del código se valida en el servicio (requiere consultar la BD).
     */
    public record ProductoRequest(
            @NotBlank(message = "El código es obligatorio")
            @Size(max = 30, message = "El código admite máximo 30 caracteres")
            @Pattern(regexp = "^[A-Za-z0-9-]+$", message = "El código solo admite letras, números y guiones")
            String codigo,

            @NotBlank(message = "El nombre es obligatorio")
            @Size(max = 120, message = "El nombre admite máximo 120 caracteres")
            String nombre,

            @Size(max = 500, message = "La descripción admite máximo 500 caracteres")
            String descripcion,

            @NotBlank(message = "La categoría es obligatoria")
            String categoria,

            @NotNull(message = "El precio es obligatorio")
            @DecimalMin(value = "0.01", message = "El precio debe ser mayor a 0 Bs.")
            @Digits(integer = 10, fraction = 2, message = "El precio admite máximo 2 decimales")
            BigDecimal precio,

            @NotNull(message = "El stock es obligatorio")
            @Positive(message = "El stock debe ser mayor a 0")
            Integer stock,

            @NotBlank(message = "La imagen del producto es obligatoria")
            @Pattern(regexp = "^https?://.+", message = "La imagen debe ser una URL http(s) válida")
            String imagenUrl
    ) { }

    /** Representación pública de un producto en el catálogo y en el detalle (HU-06). */
    public record ProductoResponse(Long id, String codigo, String nombre, String descripcion,
                                   String categoria, BigDecimal precio, Integer stock,
                                   String imagenUrl, String moneda) {
        public static ProductoResponse desde(Producto p) {
            return new ProductoResponse(p.getId(), p.getCodigo(), p.getNombre(), p.getDescripcion(),
                    p.getCategoria(), p.getPrecio(), p.getStock(), p.getImagenUrl(), "Bs.");
        }
    }

    // -----------------------------------------------------------------
    // REPOSITORIO
    // -----------------------------------------------------------------

    public interface ProductoRepository extends JpaRepository<Producto, Long> {

        boolean existsByCodigoIgnoreCase(String codigo);

        /**
         * Búsqueda del catálogo con filtros opcionales (nulos = sin filtro).
         * El índice GIN pg_trgm sobre "nombre" mantiene la respuesta < 2 s.
         */
        @Query("""
               SELECT p FROM Producto p
               WHERE (:texto IS NULL OR LOWER(p.nombre) LIKE LOWER(CONCAT('%', CAST(:texto AS string), '%')))
                 AND (:categoria IS NULL OR p.categoria = :categoria)
               ORDER BY p.nombre
               """)
        List<Producto> buscar(@Param("texto") String texto, @Param("categoria") String categoria);

        /**
         * Lee productos con BLOQUEO PESIMISTA (SELECT ... FOR UPDATE).
         * Se usa al generar una compra: evita que dos estudiantes compren
         * la última unidad al mismo tiempo (condición de carrera).
         */
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("SELECT p FROM Producto p WHERE p.id IN :ids ORDER BY p.id")
        List<Producto> bloquearPorIds(@Param("ids") Collection<Long> ids);
    }
}
