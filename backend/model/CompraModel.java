package model;

import jakarta.persistence.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import model.ProductoModel.Producto;
import model.UsuarioModel.Usuario;

/**
 * =====================================================================
 * CompraModel — Capa MODEL del módulo Compras (Órdenes).
 * ---------------------------------------------------------------------
 * HU-05 Generar Compra · HU-09 Consultar Compras.
 *
 * Responsabilidad única (SRP): representar una orden de compra y su detalle.
 * El detalle guarda una COPIA del precio unitario: el historial no cambia
 * aunque el administrador modifique luego el catálogo.
 *
 * SOLID: DIP vía {@link CompraRepository}; OCP — el estado es un enum, se
 * pueden añadir estados (p. ej. ENVIADA) sin tocar la lógica existente.
 * =====================================================================
 */
public final class CompraModel {

    private CompraModel() { }

    /** Ciclo de vida de una orden. Sprint 1 solo crea órdenes PENDIENTE_PAGO. */
    public enum EstadoCompra {
        PENDIENTE_PAGO("Pendiente de Pago"), PAGADA("Pagada"), CANCELADA("Cancelada");

        private final String etiqueta;
        EstadoCompra(String etiqueta) { this.etiqueta = etiqueta; }
        /** Texto legible para la interfaz. */
        public String etiqueta() { return etiqueta; }
    }

    // -----------------------------------------------------------------
    // ENTIDADES JPA → tablas "compra" y "compra_detalle"
    // -----------------------------------------------------------------

    @Entity(name = "Compra")
    @Table(name = "compra")
    public static class Compra {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Column(name = "numero_orden", unique = true, length = 30)
        private String numeroOrden;

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "usuario_id", nullable = false)
        private Usuario usuario;

        @Column(nullable = false)
        private LocalDateTime fecha = LocalDateTime.now();

        @Enumerated(EnumType.STRING)
        @Column(nullable = false, length = 20)
        private EstadoCompra estado = EstadoCompra.PENDIENTE_PAGO;

        @Column(nullable = false, precision = 12, scale = 2)
        private BigDecimal total = BigDecimal.ZERO;

        @OneToMany(mappedBy = "compra", cascade = CascadeType.ALL, orphanRemoval = true)
        @OrderBy("id ASC")
        private List<CompraDetalle> detalles = new ArrayList<>();

        protected Compra() { }

        public Compra(Usuario usuario) { this.usuario = usuario; }

        /**
         * Añade un renglón copiando el precio vigente y recalcula el total.
         * El total se mantiene consistente dentro del propio objeto.
         */
        public void agregarDetalle(Producto producto, int cantidad) {
            CompraDetalle d = new CompraDetalle(this, producto, cantidad, producto.getPrecio());
            detalles.add(d);
            total = total.add(d.getSubtotal());
        }

        /**
         * Asigna el número de orden legible "CM-AAAAMMDD-id".
         * Se llama después del INSERT porque necesita el id generado por la BD.
         */
        public void asignarNumeroOrden() {
            this.numeroOrden = "CM-" + fecha.format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + id;
        }

        /**
         * Pago SIMULADO: cambia la orden de "Pendiente de Pago" a "Pagada".
         * No procesa dinero ni pide datos (el pago real llega en la 2.ª iteración);
         * sirve para que las ventas cuenten en el panel del administrador.
         * La entidad protege su ciclo de vida: solo se paga una orden pendiente.
         * @throws IllegalStateException si la orden no está pendiente de pago
         */
        public void registrarPagoSimulado() {
            if (estado != EstadoCompra.PENDIENTE_PAGO) {
                throw new IllegalStateException("La orden " + numeroOrden + " no está pendiente de pago.");
            }
            this.estado = EstadoCompra.PAGADA;
        }

        public Long getId() { return id; }
        public String getNumeroOrden() { return numeroOrden; }
        public Usuario getUsuario() { return usuario; }
        public LocalDateTime getFecha() { return fecha; }
        public EstadoCompra getEstado() { return estado; }
        public BigDecimal getTotal() { return total; }
        public List<CompraDetalle> getDetalles() { return detalles; }
    }

    @Entity(name = "CompraDetalle")
    @Table(name = "compra_detalle")
    public static class CompraDetalle {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "compra_id", nullable = false)
        private Compra compra;

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "producto_id", nullable = false)
        private Producto producto;

        @Column(nullable = false)
        private Integer cantidad;

        @Column(name = "precio_unitario", nullable = false, precision = 12, scale = 2)
        private BigDecimal precioUnitario;

        @Column(nullable = false, precision = 12, scale = 2)
        private BigDecimal subtotal;

        protected CompraDetalle() { }

        CompraDetalle(Compra compra, Producto producto, int cantidad, BigDecimal precioUnitario) {
            this.compra = compra;
            this.producto = producto;
            this.cantidad = cantidad;
            this.precioUnitario = precioUnitario;
            this.subtotal = precioUnitario.multiply(BigDecimal.valueOf(cantidad));
        }

        public Producto getProducto() { return producto; }
        public Integer getCantidad() { return cantidad; }
        public BigDecimal getPrecioUnitario() { return precioUnitario; }
        public BigDecimal getSubtotal() { return subtotal; }
    }

    // -----------------------------------------------------------------
    // DTOs
    // -----------------------------------------------------------------

    /** Renglón del resumen/historial (precios en Bs.). */
    public record DetalleResponse(Long productoId, String nombre, int cantidad,
                                  BigDecimal precioUnitario, BigDecimal subtotal) {
        public static DetalleResponse desde(CompraDetalle d) {
            return new DetalleResponse(d.getProducto().getId(), d.getProducto().getNombre(),
                    d.getCantidad(), d.getPrecioUnitario(), d.getSubtotal());
        }
    }

    /**
     * "Resumen de Compra" previo a generar la orden (HU-05).
     * Si {@code carritoVacio} es true, el frontend muestra la alerta y detiene el flujo.
     */
    public record ResumenCompraResponse(boolean carritoVacio, String mensaje,
                                        List<DetalleResponse> detalles, BigDecimal total, String moneda) { }

    /** Orden registrada o consultada en el historial (HU-05 / HU-09). */
    public record CompraResponse(Long id, String numeroOrden, LocalDateTime fecha, String estado,
                                 String estadoEtiqueta, BigDecimal total, String moneda,
                                 List<DetalleResponse> detalles) {
        public static CompraResponse desde(Compra c) {
            return new CompraResponse(c.getId(), c.getNumeroOrden(), c.getFecha(), c.getEstado().name(),
                    c.getEstado().etiqueta(), c.getTotal(), "Bs.",
                    c.getDetalles().stream().map(DetalleResponse::desde).toList());
        }
    }

    /**
     * Factura de una orden: datos del comprador + detalle de productos + total en Bs.
     * Se muestra al generar la orden y desde "Mis compras". Mientras la orden esté
     * "Pendiente de Pago", la factura indica que el pago aún no se ha registrado.
     */
    public record FacturaResponse(Long id, String numeroOrden, LocalDateTime fecha, String estado,
                                  String estadoEtiqueta, String cliente, String correo, String telefono,
                                  List<DetalleResponse> detalles, int unidades, BigDecimal total, String moneda) {
        public static FacturaResponse desde(Compra c) {
            List<DetalleResponse> detalles = c.getDetalles().stream().map(DetalleResponse::desde).toList();
            return new FacturaResponse(c.getId(), c.getNumeroOrden(), c.getFecha(), c.getEstado().name(),
                    c.getEstado().etiqueta(), c.getUsuario().getNombre(), c.getUsuario().getCorreo(),
                    c.getUsuario().getTelefono(), detalles, detalles.stream().mapToInt(DetalleResponse::cantidad).sum(),
                    c.getTotal(), "Bs.");
        }
    }

    // -----------------------------------------------------------------
    // EVENTOS DE DOMINIO
    // -----------------------------------------------------------------

    /**
     * Se publica al registrar una orden. EmailNotificationController lo escucha
     * DESPUÉS del commit; CompraController jamás llama al correo directamente.
     */
    public record CompraRegistradaEvent(String correo, String nombre, String numeroOrden,
                                        BigDecimal total, List<DetalleResponse> detalles) { }

    /** Se publica al registrar el pago simulado de una orden (mismo mecanismo desacoplado). */
    public record CompraPagadaEvent(String correo, String nombre, String numeroOrden, BigDecimal total) { }

    // -----------------------------------------------------------------
    // REPOSITORIO
    // -----------------------------------------------------------------

    public interface CompraRepository extends JpaRepository<Compra, Long> {

        /** Historial de la cuenta activa, de la más reciente a la más antigua (JOIN FETCH evita N+1). */
        @Query("""
               SELECT DISTINCT c FROM Compra c
               LEFT JOIN FETCH c.detalles d
               LEFT JOIN FETCH d.producto
               WHERE c.usuario.id = :usuarioId
               ORDER BY c.fecha DESC
               """)
        List<Compra> historialDeUsuario(@Param("usuarioId") Long usuarioId);

        /**
         * Una orden con su detalle y su comprador, SOLO si pertenece al estudiante
         * indicado. Así nadie puede ver ni pagar la factura de otra persona.
         */
        @Query("""
               SELECT c FROM Compra c
               JOIN FETCH c.usuario
               LEFT JOIN FETCH c.detalles d
               LEFT JOIN FETCH d.producto
               WHERE c.id = :id AND c.usuario.id = :usuarioId
               """)
        Optional<Compra> buscarDeUsuario(@Param("id") Long id, @Param("usuarioId") Long usuarioId);

        /**
         * ¿El estudiante compró este producto en alguna orden no cancelada?
         * Requisito para insertar una reseña (HU-10).
         */
        @Query("""
               SELECT COUNT(d) > 0 FROM CompraDetalle d
               WHERE d.compra.usuario.id = :usuarioId
                 AND d.producto.id = :productoId
                 AND d.compra.estado <> model.CompraModel.EstadoCompra.CANCELADA
               """)
        boolean usuarioComproProducto(@Param("usuarioId") Long usuarioId, @Param("productoId") Long productoId);
    }
}
