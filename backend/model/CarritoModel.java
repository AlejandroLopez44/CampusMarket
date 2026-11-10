package model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import model.ProductoModel.Producto;
import model.UsuarioModel.Usuario;

/**
 * =====================================================================
 * CarritoModel — Capa MODEL del módulo Carrito de Compras.
 * ---------------------------------------------------------------------
 * HU-04 Agregar a Carrito · HU-08 Consultar Carrito.
 *
 * Responsabilidad única (SRP): modelar el carrito persistente del estudiante
 * como una LISTA de ítems (producto + cantidad) y saber calcular sus propios
 * subtotales/total en Bs. (el cálculo es un comportamiento del objeto,
 * no del controlador → alta cohesión).
 *
 * SOLID: DIP vía {@link CarritoRepository}.
 * =====================================================================
 */
public final class CarritoModel {

    private CarritoModel() { }

    /** Límite de unidades por ítem: evita pedidos absurdos desde la API. */
    public static final int MAX_UNIDADES_POR_ITEM = 99;

    // -----------------------------------------------------------------
    // ENTIDADES JPA → tablas "carrito" y "carrito_item"
    // -----------------------------------------------------------------

    @Entity(name = "Carrito")
    @Table(name = "carrito")
    public static class Carrito {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        /** Relación 1:1 con el dueño del carrito. LAZY = no carga el usuario si no se necesita. */
        @OneToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "usuario_id", nullable = false, unique = true)
        private Usuario usuario;

        /**
         * Lista de ítems. cascade=ALL + orphanRemoval: los ítems "viven" dentro del
         * carrito (composición UML); guardar el carrito guarda sus ítems.
         */
        @OneToMany(mappedBy = "carrito", cascade = CascadeType.ALL, orphanRemoval = true)
        @OrderBy("id ASC")
        private List<CarritoItem> items = new ArrayList<>();

        @Column(name = "fecha_actualizacion", nullable = false)
        private LocalDateTime fechaActualizacion = LocalDateTime.now();

        protected Carrito() { } // Requerido por JPA

        public Carrito(Usuario usuario) { this.usuario = usuario; }

        public Long getId() { return id; }
        public Usuario getUsuario() { return usuario; }
        public List<CarritoItem> getItems() { return items; }

        /** Busca el ítem de un producto dentro de la lista (si existe). */
        public Optional<CarritoItem> buscarItem(Long productoId) {
            return items.stream().filter(i -> i.getProducto().getId().equals(productoId)).findFirst();
        }

        /** Cantidad del producto que YA está en el carrito (0 si no está). */
        public int cantidadDe(Long productoId) {
            return buscarItem(productoId).map(CarritoItem::getCantidad).orElse(0);
        }

        /**
         * Agrega un producto: si ya estaba en la lista suma la cantidad,
         * si no, crea un nuevo ítem. Así se respeta UNIQUE(carrito_id, producto_id).
         */
        public void agregar(Producto producto, int cantidad) {
            buscarItem(producto.getId()).ifPresentOrElse(
                    item -> item.setCantidad(item.getCantidad() + cantidad),
                    () -> items.add(new CarritoItem(this, producto, cantidad)));
            fechaActualizacion = LocalDateTime.now();
        }

        /** Vacía el carrito (tras convertirse en orden de compra). */
        public void vaciar() {
            items.clear();
            fechaActualizacion = LocalDateTime.now();
        }

        /** Total acumulado en Bs. = Σ (precio × cantidad). */
        public BigDecimal total() {
            return items.stream().map(CarritoItem::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        /** Número total de unidades (lo que muestra el ícono/badge del carrito). */
        public int cantidadTotal() {
            return items.stream().mapToInt(CarritoItem::getCantidad).sum();
        }
    }

    @Entity(name = "CarritoItem")
    @Table(name = "carrito_item")
    public static class CarritoItem {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "carrito_id", nullable = false)
        private Carrito carrito;

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "producto_id", nullable = false)
        private Producto producto;

        @Column(nullable = false)
        private Integer cantidad;

        protected CarritoItem() { }

        CarritoItem(Carrito carrito, Producto producto, int cantidad) {
            this.carrito = carrito;
            this.producto = producto;
            this.cantidad = cantidad;
        }

        public Long getId() { return id; }
        public Producto getProducto() { return producto; }
        public Integer getCantidad() { return cantidad; }
        public void setCantidad(Integer cantidad) { this.cantidad = cantidad; }

        /** Subtotal del ítem en Bs. (precio vigente del catálogo × cantidad). */
        public BigDecimal subtotal() {
            return producto.getPrecio().multiply(BigDecimal.valueOf(cantidad));
        }
    }

    // -----------------------------------------------------------------
    // DTOs
    // -----------------------------------------------------------------

    /** Petición "Agregar al carrito": producto seleccionado + cantidad deseada. */
    public record AgregarItemRequest(
            @NotNull(message = "El producto es obligatorio") Long productoId,
            @NotNull(message = "La cantidad es obligatoria")
            @Min(value = 1, message = "La cantidad mínima es 1")
            @Max(value = MAX_UNIDADES_POR_ITEM, message = "La cantidad máxima por producto es 99")
            Integer cantidad
    ) { }

    /**
     * Respuesta de la verificación de inventario (paso previo a la confirmación).
     * Si {@code disponible} es false, el frontend bloquea la acción y muestra
     * el {@code mensaje} con el stock actual.
     */
    public record DisponibilidadResponse(Long productoId, String nombre, int cantidadSolicitada,
                                         int cantidadEnCarrito, int stockActual, boolean disponible,
                                         BigDecimal subtotalBs, String mensaje) { }

    /** Un renglón del carrito ya calculado. */
    public record ItemCarritoResponse(Long productoId, String codigo, String nombre, String imagenUrl,
                                      BigDecimal precioUnitario, int cantidad, BigDecimal subtotal) {
        public static ItemCarritoResponse desde(CarritoItem i) {
            Producto p = i.getProducto();
            return new ItemCarritoResponse(p.getId(), p.getCodigo(), p.getNombre(), p.getImagenUrl(),
                    p.getPrecio(), i.getCantidad(), i.subtotal());
        }
    }

    /** Carrito completo: ítems, unidades (badge) y total acumulado en Bs. */
    public record CarritoResponse(List<ItemCarritoResponse> items, int cantidadTotal,
                                  BigDecimal total, String moneda, boolean vacio) {
        public static CarritoResponse desde(Carrito c) {
            List<ItemCarritoResponse> items = c.getItems().stream().map(ItemCarritoResponse::desde).toList();
            return new CarritoResponse(items, c.cantidadTotal(), c.total(), "Bs.", items.isEmpty());
        }

        public static CarritoResponse sinItems() {
            return new CarritoResponse(List.of(), 0, BigDecimal.ZERO, "Bs.", true);
        }
    }

    // -----------------------------------------------------------------
    // REPOSITORIO
    // -----------------------------------------------------------------

    public interface CarritoRepository extends JpaRepository<Carrito, Long> {

        /**
         * Carga el carrito con sus ítems y productos en UNA sola consulta
         * (JOIN FETCH) para evitar el problema N+1 → respuesta < 2 s.
         */
        @Query("""
               SELECT DISTINCT c FROM Carrito c
               LEFT JOIN FETCH c.items i
               LEFT JOIN FETCH i.producto
               WHERE c.usuario.id = :usuarioId
               """)
        Optional<Carrito> buscarPorUsuario(@Param("usuarioId") Long usuarioId);

        /**
         * Solo los ids de los productos del carrito (consulta liviana).
         * Se usa para BLOQUEAR esos productos antes de generar la orden (HU-05).
         */
        @Query("SELECT i.producto.id FROM CarritoItem i WHERE i.carrito.usuario.id = :usuarioId")
        List<Long> idsDeProductosEnCarrito(@Param("usuarioId") Long usuarioId);
    }
}
