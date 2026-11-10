package control;

import jakarta.servlet.http.HttpSession;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import model.CompraModel.Compra;
import model.CompraModel.EstadoCompra;
import model.EstadisticasModel;
import model.EstadisticasModel.*;

/**
 * =====================================================================
 * EstadisticasController — Capa CONTROL del panel del Administrador.
 * ---------------------------------------------------------------------
 * Épica "Gestión de compras (Consultar)" del Administrador: es la versión
 * del administrador de "Consultar Compras". El estudiante ve SUS órdenes;
 * el administrador ve el negocio completo (ventas, inventario, órdenes).
 *
 * Endpoint: GET /api/admin/estadisticas (solo ADMINISTRADOR → 403 si no).
 *
 * SOLID: SRP (adaptador HTTP ≠ servicio de cálculo), DIP (depende de las
 * interfaces EstadisticasService y EstadisticasRepository).
 * =====================================================================
 */
@RestController
@RequestMapping("/api/admin")
public class EstadisticasController {

    private final EstadisticasService estadisticasService;
    private final SesionActiva sesionActiva;

    EstadisticasController(EstadisticasService estadisticasService, SesionActiva sesionActiva) {
        this.estadisticasService = estadisticasService;
        this.sesionActiva = sesionActiva;
    }

    /** GET /api/admin/estadisticas — números del negocio para el panel. */
    @GetMapping("/estadisticas")
    EstadisticasResponse estadisticas(HttpSession sesion) {
        sesionActiva.exigirAdministrador(sesion);
        return estadisticasService.calcular();
    }
}

// =====================================================================
// Servicio
// =====================================================================

/** Contrato del cálculo de estadísticas. */
interface EstadisticasService {
    EstadisticasResponse calcular();
}

@Service
class EstadisticasServiceImpl implements EstadisticasService {

    /** Cuántos elementos muestra cada lista del panel. */
    private static final int TOP = 5;
    private static final int RECIENTES = 10;
    private static final int DIAS_GRAFICO = 7;

    private final EstadisticasRepository repo;

    EstadisticasServiceImpl(EstadisticasRepository repo) {
        this.repo = repo;
    }

    /**
     * Arma el panel con varias consultas agregadas en UNA transacción de solo
     * lectura (datos coherentes entre sí y sin bloquear a los compradores).
     */
    @Override
    @Transactional(readOnly = true)
    public EstadisticasResponse calcular() {
        // 1) Ventas por estado: Pagadas = ventas; Pendientes = por cobrar.
        BigDecimal pagado = BigDecimal.ZERO, pendiente = BigDecimal.ZERO;
        long nPagadas = 0, nPendientes = 0, nTotal = 0;
        for (Object[] fila : repo.resumenPorEstado()) {
            EstadoCompra estado = (EstadoCompra) fila[0];
            long cantidad = ((Number) fila[1]).longValue();
            BigDecimal suma = (BigDecimal) fila[2];
            nTotal += cantidad;
            if (estado == EstadoCompra.PAGADA) { pagado = suma; nPagadas = cantidad; }
            if (estado == EstadoCompra.PENDIENTE_PAGO) { pendiente = suma; nPendientes = cantidad; }
        }
        long noCanceladas = nPagadas + nPendientes;
        BigDecimal ticket = noCanceladas == 0 ? BigDecimal.ZERO
                : pagado.add(pendiente).divide(BigDecimal.valueOf(noCanceladas), 2, RoundingMode.HALF_UP);

        // 2) Ranking de productos más vendidos.
        List<ProductoMasVendido> masVendidos = repo.masVendidos(PageRequest.of(0, TOP)).stream()
                .map(f -> new ProductoMasVendido((Long) f[0], (String) f[1],
                        ((Number) f[2]).longValue(), (BigDecimal) f[3]))
                .toList();

        // 3) Reseñas: cantidad y promedio (null si aún no hay reseñas).
        Object[] resenas = repo.resumenResenas().get(0);
        long nResenas = ((Number) resenas[0]).longValue();
        Double promedio = resenas[1] == null ? null
                : BigDecimal.valueOf(((Number) resenas[1]).doubleValue()).setScale(1, RoundingMode.HALF_UP).doubleValue();

        return new EstadisticasResponse(
                pagado, nPagadas, pendiente, nPendientes, nTotal, ticket,
                repo.contarProductos(), repo.contarAgotados(), repo.contarEstudiantes(),
                nResenas, promedio,
                repo.stockBajo(EstadisticasModel.UMBRAL_STOCK_BAJO, PageRequest.of(0, 10)).stream()
                        .map(ProductoStockBajo::desde).toList(),
                masVendidos,
                ventasPorDia(),
                repo.ordenesRecientes(PageRequest.of(0, RECIENTES)).stream().map(OrdenReciente::desde).toList(),
                EstadisticasModel.UMBRAL_STOCK_BAJO, "Bs.", LocalDateTime.now());
    }

    /**
     * Ventas de los últimos 7 días (incluido hoy). Se agrupa en Java porque son
     * pocas filas y así la consulta es portable. Los días sin ventas quedan en 0,
     * para que el gráfico siempre muestre 7 barras.
     */
    private List<VentaDia> ventasPorDia() {
        LocalDate hoy = LocalDate.now();
        LocalDate desde = hoy.minusDays(DIAS_GRAFICO - 1L);
        List<Compra> compras = repo.comprasDesde(desde.atStartOfDay());

        List<VentaDia> dias = new ArrayList<>();
        for (LocalDate dia = desde; !dia.isAfter(hoy); dia = dia.plusDays(1)) {
            BigDecimal pagado = BigDecimal.ZERO, pendiente = BigDecimal.ZERO;
            long ordenes = 0;
            for (Compra c : compras) {
                if (!c.getFecha().toLocalDate().equals(dia)) continue;
                ordenes++;
                if (c.getEstado() == EstadoCompra.PAGADA) pagado = pagado.add(c.getTotal());
                else pendiente = pendiente.add(c.getTotal());
            }
            dias.add(new VentaDia(dia, pagado, pendiente, ordenes));
        }
        return dias;
    }
}
