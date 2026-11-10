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
 *   5. Se muestra la FACTURA de la orden (GET /{id}/factura).
 *   6. Opcional: "Registrar pago (simulado)" (POST /{id}/pago-simulado) cambia
 *      el estado a "Pagada" sin pedir datos. El pago real es de la 2.ª iteración;
 *      la simulación permite que las ventas aparezcan en el panel del admin.
 *
 * Todas las operaciones son SOLO para estudiantes (el administrador no compra).
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
        return compraService.resumen(sesionActiva.exigirEstudiante(sesion));
    }

    /** POST /api/compras — HU-05 paso 3 (solo tras la confirmación explícita del usuario). */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    RespuestaApi<CompraResponse> generar(HttpSession sesion) {
        CompraResponse orden = compraService.generar(sesionActiva.exigirEstudiante(sesion));
        return new RespuestaApi<>("Orden generada - N° " + orden.numeroOrden(), orden);
    }

    /** GET /api/compras — HU-09: historial de órdenes de la cuenta activa. */
    @GetMapping
    List<CompraResponse> historial(HttpSession sesion) {
        return compraService.historial(sesionActiva.exigirEstudiante(sesion));
    }

    /** GET /api/compras/{id}/factura — factura de una orden propia. */
    @GetMapping("/{id}/factura")
    FacturaResponse factura(@PathVariable Long id, HttpSession sesion) {
        return compraService.factura(sesionActiva.exigirEstudiante(sesion), id);
    }

    /** POST /api/compras/{id}/pago-simulado — marca la orden como "Pagada" (sin datos de pago). */
    @PostMapping("/{id}/pago-simulado")
    RespuestaApi<FacturaResponse> pagoSimulado(@PathVariable Long id, HttpSession sesion) {
        FacturaResponse f = compraService.registrarPagoSimulado(sesionActiva.exigirEstudiante(sesion), id);
        return new RespuestaApi<>("Pago simulado registrado. La orden " + f.numeroOrden() + " quedó como Pagada.", f);
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
    FacturaResponse factura(Long usuarioId, Long compraId);
    FacturaResponse registrarPagoSimulado(Long usuarioId, Long compraId);
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
                // Texto del criterio 4 de "Generar compra". No se ofrece ajustar cantidades
                // (Sprint 2); la vista permite salir o vaciar el carrito para no quedar atrapado.
                throw new NegocioException(HttpStatus.CONFLICT, "Stock insuficiente en el producto " + p.getNombre(),
                        java.util.Map.of("stockActual", p.getStock()));
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

    /** Factura de una orden propia. 404 si no existe o es de otro estudiante (no se revela cuál). */
    @Override
    @Transactional(readOnly = true)
    public FacturaResponse factura(Long usuarioId, Long compraId) {
        return FacturaResponse.desde(obtenerPropia(usuarioId, compraId));
    }

    /**
     * Pago SIMULADO. La regla "solo se paga una orden pendiente" vive en la
     * entidad (Compra.registrarPagoSimulado); aquí se traduce a un 409 legible.
     * Tras el commit, el correo de confirmación sale por evento.
     */
    @Override
    @Transactional
    public FacturaResponse registrarPagoSimulado(Long usuarioId, Long compraId) {
        Compra compra = obtenerPropia(usuarioId, compraId);
        try {
            compra.registrarPagoSimulado();
        } catch (IllegalStateException ex) {
            throw new NegocioException(HttpStatus.CONFLICT, ex.getMessage());
        }
        eventos.publishEvent(new CompraPagadaEvent(compra.getUsuario().getCorreo(), compra.getUsuario().getNombre(),
                compra.getNumeroOrden(), compra.getTotal()));
        return FacturaResponse.desde(compra);
    }

    private Compra obtenerPropia(Long usuarioId, Long compraId) {
        return compras.buscarDeUsuario(compraId, usuarioId)
                .orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND, "La orden solicitada no existe."));
    }
}
