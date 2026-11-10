package control;

import jakarta.servlet.http.HttpSession;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

import control.CampusMarketApplication.RespuestaApi;
import model.CarritoModel.Carrito;
import model.CarritoModel.CarritoItem;
import model.CarritoModel.CarritoRepository;
import model.CompraModel.*;
import model.ProductoModel.Producto;
import model.ProductoModel.ProductoRepository;

/**
 * =====================================================================
 * CompraController — Capa CONTROL del módulo Compras.
 * ---------------------------------------------------------------------
 * HU-05 Generar Compra · HU-09 Consultar Compras.
 *
 * Flujo HU-05 (diagrama de Generar Compra, corregido):
 *   1. GET /resumen → valida producto, cantidad y precio del carrito.
 *      • Carrito vacío → "No hay productos en el carrito" y se DETIENE.
 *      • Carrito con productos → resumen con detalle, cantidades y Bs.
 *   2. [Generar Orden] → la vista solicita confirmación EXPLÍCITA.
 *   3. POST /api/compras → consulta stock en BD (con bloqueo):
 *      • Insuficiente → "Stock insuficiente del [producto]" ([OK] vuelve al
 *        resumen, [Salir] al menú principal).
 *      • Suficiente → registra la orden "Pendiente de Pago", descuenta
 *        (reserva) el inventario, vacía el carrito y devuelve el N° de orden.
 *   4. Tras el COMMIT, EmailNotificationController envía el correo (evento).
 *
 * SOLID: SRP, DIP, OCP (notificaciones por evento, no por llamada directa).
 * =====================================================================
 */
@RestController
@RequestMapping("/api/compras")
public class CompraController {

    private final CompraService compraService;
    private final SesionActiva sesionActiva;

    CompraController(CompraService compraService, SesionActiva sesionActiva) {
        this.compraService = compraService;
        this.sesionActiva = sesionActiva;
    }

    /** GET /api/compras/resumen — HU-05 paso 1. */
    @GetMapping("/resumen")
    ResumenCompraResponse resumen(HttpSession sesion) {
        return compraService.resumen(sesionActiva.usuarioId(sesion));
    }

    /** POST /api/compras — HU-05 paso 3 (solo tras la confirmación explícita del usuario). */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    RespuestaApi<CompraResponse> generar(HttpSession sesion) {
        CompraResponse orden = compraService.generar(sesionActiva.usuarioId(sesion));
        return new RespuestaApi<>("Orden generada - N° " + orden.numeroOrden(), orden);
    }

    /** GET /api/compras — HU-09: historial de órdenes de la cuenta activa. */
    @GetMapping
    List<CompraResponse> historial(HttpSession sesion) {
        return compraService.historial(sesionActiva.usuarioId(sesion));
    }
}

// =====================================================================
// Servicio
// =====================================================================

/** Contrato de negocio de compras. */
interface CompraService {
    ResumenCompraResponse resumen(Long usuarioId);
    CompraResponse generar(Long usuarioId);
    List<CompraResponse> historial(Long usuarioId);
}

@Service
class CompraServiceImpl implements CompraService {

    static final String CARRITO_VACIO = "No hay productos en el carrito";

    private final CompraRepository compras;
    private final CarritoRepository carritos;
    private final ProductoRepository productos;
    private final ApplicationEventPublisher eventos;

    CompraServiceImpl(CompraRepository compras, CarritoRepository carritos,
                      ProductoRepository productos, ApplicationEventPublisher eventos) {
        this.compras = compras;
        this.carritos = carritos;
        this.productos = productos;
        this.eventos = eventos;
    }

    /**
     * Construye el resumen con los precios VIGENTES del catálogo.
     * @return carritoVacio=true + mensaje si no hay ítems (la vista detiene el flujo)
     */
    @Override
    @Transactional(readOnly = true)
    public ResumenCompraResponse resumen(Long usuarioId) {
        Carrito carrito = carritos.buscarPorUsuario(usuarioId).orElse(null);
        if (carrito == null || carrito.getItems().isEmpty()) {
            return new ResumenCompraResponse(true, CARRITO_VACIO, List.of(), BigDecimal.ZERO, "Bs.");
        }
        List<DetalleResponse> detalles = carrito.getItems().stream()
                .map(i -> new DetalleResponse(i.getProducto().getId(), i.getProducto().getNombre(),
                        i.getCantidad(), i.getProducto().getPrecio(), i.subtotal()))
                .toList();
        return new ResumenCompraResponse(false, "Revise su pedido antes de generar la orden.",
                detalles, carrito.total(), "Bs.");
    }

    /**
     * Registra la orden de forma ATÓMICA (todo o nada en una transacción):
     *  1) Bloquea (SELECT ... FOR UPDATE) los productos del carrito ANTES de
     *     leerlos, para que dos compras simultáneas no vendan la misma unidad.
     *  2) Verifica stock de cada ítem; al primer faltante aborta con 409.
     *  3) Crea la orden PENDIENTE_PAGO copiando precios y descuenta inventario.
     *  4) Vacía el carrito y publica CompraRegistradaEvent (correo post-commit).
     */
    @Override
    @Transactional
    public CompraResponse generar(Long usuarioId) {
        List<Long> ids = carritos.idsDeProductosEnCarrito(usuarioId);
        if (ids.isEmpty()) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, CARRITO_VACIO);
        }
        productos.bloquearPorIds(ids); // Paso 1: ahora las filas están bloqueadas y frescas

        Carrito carrito = carritos.buscarPorUsuario(usuarioId)
                .orElseThrow(() -> new NegocioException(HttpStatus.BAD_REQUEST, CARRITO_VACIO));

        // Paso 2: validación de inventario sobre datos bloqueados.
        for (CarritoItem item : carrito.getItems()) {
            Producto p = item.getProducto();
            if (!p.tieneStock(item.getCantidad())) {
                throw new NegocioException(HttpStatus.CONFLICT,
                        "Stock insuficiente del " + p.getNombre() + " (disponible: " + p.getStock() + ").");
            }
        }

        // Paso 3: la orden se arma con el comportamiento de la propia entidad.
        Compra compra = new Compra(carrito.getUsuario());
        for (CarritoItem item : carrito.getItems()) {
            compra.agregarDetalle(item.getProducto(), item.getCantidad());
            item.getProducto().descontarStock(item.getCantidad()); // Reserva de inventario
        }
        compras.saveAndFlush(compra);   // flush → la BD asigna el id
        compra.asignarNumeroOrden();    // usa el id; Hibernate lo guardará al hacer commit

        // Paso 4.
        carrito.vaciar();
        CompraResponse respuesta = CompraResponse.desde(compra);
        eventos.publishEvent(new CompraRegistradaEvent(carrito.getUsuario().getCorreo(),
                carrito.getUsuario().getNombre(), compra.getNumeroOrden(), compra.getTotal(), respuesta.detalles()));
        return respuesta;
    }

    /** HU-09: solo las órdenes de la cuenta activa (el id viene de la sesión, nunca del cliente). */
    @Override
    @Transactional(readOnly = true)
    public List<CompraResponse> historial(Long usuarioId) {
        return compras.historialDeUsuario(usuarioId).stream().map(CompraResponse::desde).toList();
    }
}
