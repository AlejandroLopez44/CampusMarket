package model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

import model.ProductoModel.Producto;
import model.UsuarioModel.Usuario;

/**
 * =====================================================================
 * ResenaModel — Capa MODEL del módulo Reseñas.
 * ---------------------------------------------------------------------
 * HU-01 Consultar Reseña · HU-10 Insertar Reseña.
 *
 * Responsabilidad única (SRP): datos de una reseña y su contrato de acceso.
 * Reglas reflejadas aquí: texto con límite de {@value #MAX_CARACTERES}
 * caracteres y calificación de 1 a 5. La regla "1 reseña por estudiante y
 * producto" se valida en el servicio y además con UNIQUE en la BD.
 *
 * SOLID: DIP vía {@link ResenaRepository}.
 * =====================================================================
 */
public final class ResenaModel {

    private ResenaModel() { }

    /** Límite de caracteres del comentario (restricción del Brief). */
    public static final int MAX_CARACTERES = 500;

    /** Mensaje del estado vacío exigido por el diagrama "Consultar reseña". */
    public static final String MENSAJE_SIN_RESENAS = "No hay reseñas disponibles para este producto";

    // -----------------------------------------------------------------
    // ENTIDAD JPA → tabla "resena"
    // -----------------------------------------------------------------

    @Entity(name = "Resena")
    @Table(name = "resena",
           uniqueConstraints = @UniqueConstraint(columnNames = {"producto_id", "usuario_id"}))
    public static class Resena {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "producto_id", nullable = false)
        private Producto producto;

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "usuario_id", nullable = false)
        private Usuario usuario;

        @Column(nullable = false, length = MAX_CARACTERES)
        private String texto;

        @Column(nullable = false)
        private Short calificacion;

        @Column(nullable = false)
        private LocalDateTime fecha = LocalDateTime.now();

        protected Resena() { }

        public Resena(Producto producto, Usuario usuario, String texto, int calificacion) {
            this.producto = producto;
            this.usuario = usuario;
            this.texto = texto;
            this.calificacion = (short) calificacion;
        }

        public Long getId() { return id; }
        public Producto getProducto() { return producto; }
        public Usuario getUsuario() { return usuario; }
        public String getTexto() { return texto; }
        public Short getCalificacion() { return calificacion; }
        public LocalDateTime getFecha() { return fecha; }
    }

    // -----------------------------------------------------------------
    // DTOs
    // -----------------------------------------------------------------

    /** Datos que envía el estudiante al publicar (HU-10). */
    public record ResenaRequest(
            @NotBlank(message = "El comentario no puede estar vacío")
            @Size(max = MAX_CARACTERES, message = "El comentario admite máximo 500 caracteres")
            String texto,

            @NotNull(message = "La calificación es obligatoria")
            @Min(value = 1, message = "La calificación mínima es 1")
            @Max(value = 5, message = "La calificación máxima es 5")
            Integer calificacion
    ) { }

    /** Una reseña tal como se muestra: autor, fecha y texto (HU-01). */
    public record ResenaResponse(Long id, String autor, LocalDateTime fecha, String texto, int calificacion) {
        public static ResenaResponse desde(Resena r) {
            return new ResenaResponse(r.getId(), r.getUsuario().getNombre(), r.getFecha(),
                    r.getTexto(), r.getCalificacion());
        }
    }

    /**
     * Respuesta de "Consultar reseñas". Si la lista está vacía, {@code mensaje}
     * contiene el texto del estado vacío; así la vista no "inventa" textos.
     */
    public record ListaResenasResponse(Long productoId, String producto, List<ResenaResponse> resenas,
                                       boolean vacia, String mensaje) { }

    // -----------------------------------------------------------------
    // REPOSITORIO
    // -----------------------------------------------------------------

    public interface ResenaRepository extends JpaRepository<Resena, Long> {

        /** Reseñas de un producto con su autor (JOIN FETCH), más recientes primero. */
        @Query("""
               SELECT r FROM Resena r JOIN FETCH r.usuario
               WHERE r.producto.id = :productoId
               ORDER BY r.fecha DESC
               """)
        List<Resena> buscarPorProducto(@Param("productoId") Long productoId);

        /** Regla "máximo 1 reseña por estudiante por producto". */
        boolean existsByProductoIdAndUsuarioId(Long productoId, Long usuarioId);
    }
}
