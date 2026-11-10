package model;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import model.CompraModel.Compra;
import model.ProductoModel.Producto;

/**
 * =====================================================================
 * EstadisticasModel — Capa MODEL del panel del Administrador.
 * ---------------------------------------------------------------------
 * Épica "Gestión de compras (Consultar)" del Administrador (Brief):
 * "evitar quiebres de stock, tener control del estado de cada compra y
 * acceso al historial de ventas".
 *
 * Responsabilidad única (SRP): describir los datos del panel (DTOs) y las
 * consultas AGREGADAS (COUNT, SUM, AVG) que los calculan. No crea tablas
 * nuevas: lee las de compra, producto, usuario y reseña.
 *
 * SOLID:
 *   • ISP — repositorio propio, de SOLO LECTURA (extiende Repository y no
 *     JpaRepository): el panel no puede guardar ni borrar nada por error.
 *   • DIP — el servicio depende de esta interfaz, no de SQL concreto.
 * =====================================================================
 */
public final class EstadisticasModel {

    private EstadisticasModel() { }

    /** Un producto con stock igual o menor a este valor se considera "stock bajo". */
    public static final int UMBRAL_STOCK_BAJO = 5;

    // -----------------------------------------------------------------
    // DTOs (lo que recibe la vista del panel)
    // -----------------------------------------------------------------

    /** Producto que necesita reposición. */
    public record ProductoStockBajo(Long id, String codigo, String nombre, int stock) {
        public static ProductoStockBajo desde(Producto p) {
            return new ProductoStockBajo(p.getId(), p.getCodigo(), p.getNombre(), p.getStock());
        }
    }

    /** Producto del ranking de más vendidos (unidades e ingresos en Bs.). */
    public record ProductoMasVendido(Long productoId, String nombre, long unidades, BigDecimal ingresos) { }

    /** Ventas de un día, separadas por estado (para el gráfico de barras). */
    public record VentaDia(LocalDate fecha, BigDecimal pagado, BigDecimal pendiente, long ordenes) { }

    /** Orden reciente de cualquier estudiante (control del estado de cada compra). */
    public record OrdenReciente(Long id, String numeroOrden, String cliente, LocalDateTime fecha,
                                String estado, String estadoEtiqueta, BigDecimal total) {
        public static OrdenReciente desde(Compra c) {
            return new OrdenReciente(c.getId(), c.getNumeroOrden(), c.getUsuario().getNombre(), c.getFecha(),
                    c.getEstado().name(), c.getEstado().etiqueta(), c.getTotal());
        }
    }

    /**
     * Respuesta completa del panel. Todos los montos en Bs.
     * "Ventas" = órdenes Pagadas (pago simulado); "pendiente" = por cobrar.
     */
    public record EstadisticasResponse(
            BigDecimal ventasPagadas, long ordenesPagadas,
            BigDecimal montoPendiente, long ordenesPendientes,
            long ordenesTotales, BigDecimal ticketPromedio,
            long productos, long agotados, long estudiantes,
            long resenas, Double calificacionPromedio,
            List<ProductoStockBajo> stockBajo,
            List<ProductoMasVendido> masVendidos,
            List<VentaDia> ventasUltimos7Dias,
            List<OrdenReciente> ordenesRecientes,
            int umbralStockBajo, String moneda, LocalDateTime generadoEn) { }

    // -----------------------------------------------------------------
    // REPOSITORIO DE SOLO LECTURA (consultas agregadas)
    // -----------------------------------------------------------------

    /**
     * Las consultas devuelven Object[] cuando calculan varias columnas a la vez
     * (p. ej. estado + cantidad + suma); el servicio las convierte en DTOs.
     */
    public interface EstadisticasRepository extends Repository<Compra, Long> {

        /** Por cada estado de orden: [estado, cantidad de órdenes, suma de totales]. */
        @Query("SELECT c.estado, COUNT(c), COALESCE(SUM(c.total), 0) FROM Compra c GROUP BY c.estado")
        List<Object[]> resumenPorEstado();

        @Query("SELECT COUNT(p) FROM Producto p")
        long contarProductos();

        @Query("SELECT COUNT(p) FROM Producto p WHERE p.stock = 0")
        long contarAgotados();

        /** Productos con stock bajo, los más urgentes primero. */
        @Query("SELECT p FROM Producto p WHERE p.stock <= :umbral ORDER BY p.stock ASC, p.nombre ASC")
        List<Producto> stockBajo(@Param("umbral") int umbral, Pageable limite);

        @Query("SELECT COUNT(u) FROM Usuario u WHERE u.rol = model.UsuarioModel.Rol.ESTUDIANTE")
        long contarEstudiantes();

        /** Ranking: [productoId, nombre, unidades vendidas, ingresos]. Excluye órdenes canceladas. */
        @Query("""
               SELECT d.producto.id, d.producto.nombre, SUM(d.cantidad), SUM(d.subtotal)
               FROM CompraDetalle d
               WHERE d.compra.estado <> model.CompraModel.EstadoCompra.CANCELADA
               GROUP BY d.producto.id, d.producto.nombre
               ORDER BY SUM(d.cantidad) DESC
               """)
        List<Object[]> masVendidos(Pageable limite);

        /** Órdenes no canceladas desde una fecha (para agrupar por día). */
        @Query("""
               SELECT c FROM Compra c
               WHERE c.fecha >= :desde AND c.estado <> model.CompraModel.EstadoCompra.CANCELADA
               """)
        List<Compra> comprasDesde(@Param("desde") LocalDateTime desde);

        /** Últimas órdenes de todos los estudiantes, con su comprador (JOIN FETCH evita N+1). */
        @Query("SELECT c FROM Compra c JOIN FETCH c.usuario ORDER BY c.fecha DESC")
        List<Compra> ordenesRecientes(Pageable limite);

        /** Una sola fila: [cantidad de reseñas, calificación promedio]. */
        @Query("SELECT COUNT(r), AVG(r.calificacion) FROM Resena r")
        List<Object[]> resumenResenas();
    }
}
