package control;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import control.CampusMarketApplication.RespuestaApi;
import model.CarritoModel;
import model.CarritoModel.*;
import model.ProductoModel.Producto;
import model.ProductoModel.ProductoRepository;
import model.UsuarioModel.UsuarioRepository;

/**
 * =====================================================================
 * CarritoController — Capa CONTROL del módulo Carrito.
 * ---------------------------------------------------------------------
 * HU-04 Agregar a Carrito · HU-08 Consultar Carrito.
 *
 * Flujo HU-04 (diagrama "Agregar a Carrito", corregido):
 *   1. El usuario elige producto + cantidad y pulsa "Agregar al carrito".
 *   2. GET /disponibilidad → el sistema consulta el inventario en BD.
 *      • Stock insuficiente → se BLOQUEA la acción y se informa el stock actual.
 *        El usuario puede [Salir] (volver al catálogo) u [OK].
 *      • Stock disponible → la vista muestra el subtotal en Bs. y pide confirmación.
 *   3. [Confirmar] → el producto se guarda en el carrito.
 *
 * Dos tipos de carrito:
 *   • VISITANTE (sin sesión): el carrito vive en el navegador. El backend no
 *     lo guarda, pero sí verifica el stock (/disponibilidad) y calcula precios
 *     y totales con datos reales de la BD (/cotizacion). El navegador nunca
 *     decide precios.
 *   • ESTUDIANTE (con sesión): el carrito se guarda en la BD (/items). Al
 *     iniciar sesión, el carrito del navegador se une al guardado (/fusion).
 *   • El ADMINISTRADOR no tiene carrito: recibe 403.
 *
 * SOLID: SRP (adaptador HTTP ≠ servicio), DIP (CarritoService y repositorios
 * como interfaces). RNF < 2 s: una consulta con JOIN FETCH por operación.
 * =====================================================================
 */
@RestController
@RequestMapping("/api/carrito")
public class CarritoController {

    /** Texto exacto del criterio de aceptación 4 de "Agregar producto al carrito". */
    static final String MENSAJE_AGREGADO = "Producto agregado al carrito exitosamente";

    private final CarritoService carritoService;
    private final SesionActiva sesionActiva;

    CarritoController(CarritoService carritoService, SesionActiva sesionActiva) {
        this.carritoService = carritoService;
        this.sesionActiva = sesionActiva;
    }

    /** GET /api/carrito — HU-08 (estudiante): ítems, cantidades y total acumulado en Bs. */
    @GetMapping
    CarritoResponse consultar(HttpSession sesion) {
        return carritoService.consultar(sesionActiva.exigirEstudiante(sesion));
    }

    /**
     * GET /api/carrito/disponibilidad?productoId=1&cantidad=2[&enCarrito=3] — HU-04 paso 2.
     * Funciona con o sin sesión. No modifica nada.
     * @param enCarrito solo lo usa el VISITANTE: unidades de ese producto que ya
     *                  tiene en el carrito de su navegador. Con sesión se ignora
     *                  y se lee el carrito guardado en la BD.
     */
    @GetMapping("/disponibilidad")
    DisponibilidadResponse disponibilidad(@RequestParam Long productoId, @RequestParam int cantidad,
                                          @RequestParam(defaultValue = "0") int enCarrito, HttpSession sesion) {
        bloquearAdministrador(sesion);
        Optional<Long> usuarioId = sesionActiva.usuarioIdOpcional(sesion);
        return usuarioId.isPresent()
                ? carritoService.verificarDisponibilidad(usuarioId.get(), productoId, cantidad)
                : carritoService.verificarDisponibilidadInvitado(productoId, cantidad, enCarrito);
    }

    /** POST /api/carrito/items — HU-04 paso 3 (estudiante, tras confirmar). */
    @PostMapping("/items")
    RespuestaApi<CarritoResponse> agregar(@Valid @RequestBody AgregarItemRequest req, HttpSession sesion) {
        CarritoResponse carrito = carritoService.agregar(sesionActiva.exigirEstudiante(sesion), req);
        return new RespuestaApi<>(MENSAJE_AGREGADO, carrito);
    }

    /**
     * POST /api/carrito/cotizacion — HU-08 para VISITANTES.
     * Recibe los ítems guardados en el navegador y devuelve el carrito con
     * precios, subtotales, total y stock vigentes. No guarda nada.
     */
    @PostMapping("/cotizacion")
    CarritoResponse cotizar(@Valid @RequestBody ListaItemsRequest req, HttpSession sesion) {
        bloquearAdministrador(sesion);
        return carritoService.cotizar(req);
    }

    /**
     * POST /api/carrito/fusion — al iniciar sesión, une el carrito del navegador
     * con el carrito guardado del estudiante, respetando el stock disponible.
     * @return carrito resultante + mensaje con los ajustes realizados (si hubo)
     */
    @PostMapping("/fusion")
    RespuestaApi<CarritoResponse> fusionar(@Valid @RequestBody ListaItemsRequest req, HttpSession sesion) {
        return carritoService.fusionar(sesionActiva.exigirEstudiante(sesion), req);
    }

    /**
     * DELETE /api/carrito — vacía el carrito del estudiante.
     * Es la salida que evita quedar atrapado cuando un producto se quedó sin stock
     * al generar la compra (modificar cantidades una por una es del Sprint 2).
     */
    @DeleteMapping
    RespuestaApi<CarritoResponse> vaciar(HttpSession sesion) {
        return new RespuestaApi<>("Se vació tu carrito.", carritoService.vaciar(sesionActiva.exigirEstudiante(sesion)));
    }

    /** El administrador no compra: cualquier operación de carrito le responde 403. */
    private void bloquearAdministrador(HttpSession sesion) {
        if (sesionActiva.esAdministrador(sesion)) {
            throw new NegocioException(HttpStatus.FORBIDDEN, "Esta acción es solo para estudiantes.");
        }
    }
}

// =====================================================================
// Servicio
// =====================================================================

/** Contrato de negocio del carrito. */
interface CarritoService {
    CarritoResponse consultar(Long usuarioId);
    DisponibilidadResponse verificarDisponibilidad(Long usuarioId, Long productoId, int cantidad);
    DisponibilidadResponse verificarDisponibilidadInvitado(Long productoId, int cantidad, int enCarrito);
    CarritoResponse agregar(Long usuarioId, AgregarItemRequest req);
    CarritoResponse cotizar(ListaItemsRequest req);
    RespuestaApi<CarritoResponse> fusionar(Long usuarioId, ListaItemsRequest req);
    CarritoResponse vaciar(Long usuarioId);
}

@Service
class CarritoServiceImpl implements CarritoService {

    private final CarritoRepository carritos;
    private final UsuarioRepository usuarios;
    private final ProductoRepository productos;
    private final ProductoService productoService; // Reutiliza la búsqueda de producto (no duplica lógica)

    CarritoServiceImpl(CarritoRepository carritos, UsuarioRepository usuarios,
                       ProductoRepository productos, ProductoService productoService) {
        this.carritos = carritos;
        this.usuarios = usuarios;
        this.productos = productos;
        this.productoService = productoService;
    }

    /** HU-08. Si el estudiante aún no tiene carrito, se responde un carrito vacío (sin crear filas). */
    @Override
    @Transactional(readOnly = true)
    public CarritoResponse consultar(Long usuarioId) {
        return carritos.buscarPorUsuario(usuarioId).map(CarritoResponse::desde).orElseGet(CarritoResponse::sinItems);
    }

    /**
     * Verificación de inventario del ESTUDIANTE. Se suma lo que YA está en su
     * carrito guardado: si hay 5 en stock y el carrito tiene 4, pedir 2 más falla.
     */
    @Override
    @Transactional(readOnly = true)
    public DisponibilidadResponse verificarDisponibilidad(Long usuarioId, Long productoId, int cantidad) {
        validarCantidad(cantidad);
        Producto p = productoService.obtenerEntidad(productoId);
        int enCarrito = carritos.buscarPorUsuario(usuarioId).map(c -> c.cantidadDe(productoId)).orElse(0);
        return evaluar(p, cantidad, enCarrito);
    }

    /**
     * Verificación de inventario del VISITANTE. La misma regla, pero las unidades
     * que ya tiene las informa su navegador (no hay carrito en la BD).
     */
    @Override
    @Transactional(readOnly = true)
    public DisponibilidadResponse verificarDisponibilidadInvitado(Long productoId, int cantidad, int enCarrito) {
        validarCantidad(cantidad);
        if (enCarrito < 0 || enCarrito > CarritoModel.MAX_UNIDADES_POR_ITEM) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "La cantidad en el carrito no es válida.");
        }
        return evaluar(productoService.obtenerEntidad(productoId), cantidad, enCarrito);
    }

    /**
     * Guarda el ítem en el carrito del estudiante. Se RE-VERIFICA el stock porque
     * entre la consulta de disponibilidad y la confirmación otro usuario pudo comprar.
     * Nota: aquí NO se descuenta inventario; eso ocurre al generar la orden (HU-05).
     */
    @Override
    @Transactional
    public CarritoResponse agregar(Long usuarioId, AgregarItemRequest req) {
        Carrito carrito = obtenerOCrear(usuarioId);
        Producto p = productoService.obtenerEntidad(req.productoId());

        DisponibilidadResponse d = evaluar(p, req.cantidad(), carrito.cantidadDe(p.getId()));
        if (!d.disponible()) {
            // Rama "Stock Insuficiente": se bloquea la acción y se informa el stock actual.
            throw new NegocioException(HttpStatus.CONFLICT, d.mensaje(), Map.of("stockActual", d.stockActual()));
        }

        carrito.agregar(p, req.cantidad());
        return CarritoResponse.desde(carritos.save(carrito));
    }

    /**
     * Calcula el carrito del visitante con datos reales de la BD.
     * Los productos que ya no existen se omiten (el navegador los limpiará).
     */
    @Override
    @Transactional(readOnly = true)
    public CarritoResponse cotizar(ListaItemsRequest req) {
        List<ItemCarritoResponse> renglones = new ArrayList<>();
        agrupar(req).forEach((productoId, cantidad) ->
                productos.findById(productoId).ifPresent(p -> renglones.add(ItemCarritoResponse.de(p, cantidad))));
        return CarritoResponse.deItems(renglones);
    }

    /**
     * Une el carrito del navegador con el carrito guardado del estudiante.
     * Regla: nunca se supera el stock ni el máximo por producto. Si una
     * cantidad no cabe completa, se agrega lo que alcance y se avisa.
     */
    @Override
    @Transactional
    public RespuestaApi<CarritoResponse> fusionar(Long usuarioId, ListaItemsRequest req) {
        Carrito carrito = obtenerOCrear(usuarioId);
        List<String> avisos = new ArrayList<>();

        agrupar(req).forEach((productoId, cantidad) -> {
            Optional<Producto> encontrado = productos.findById(productoId);
            if (encontrado.isEmpty()) return; // Producto eliminado del catálogo: se ignora
            Producto p = encontrado.get();
            int yaTiene = carrito.cantidadDe(productoId);
            int limite = Math.min(p.getStock(), CarritoModel.MAX_UNIDADES_POR_ITEM);
            int aAgregar = Math.min(cantidad, Math.max(0, limite - yaTiene));
            if (aAgregar > 0) carrito.agregar(p, aAgregar);
            if (aAgregar < cantidad) {
                avisos.add(p.getNombre() + ": se agregaron " + aAgregar + " de " + cantidad
                        + " (stock disponible: " + p.getStock() + ").");
            }
        });

        CarritoResponse respuesta = CarritoResponse.desde(carritos.save(carrito));
        String mensaje = avisos.isEmpty()
                ? "Tu carrito se guardó en tu cuenta."
                : "Tu carrito se guardó en tu cuenta con ajustes por inventario:\n" + String.join("\n", avisos);
        return new RespuestaApi<>(mensaje, respuesta);
    }

    /** Vacía el carrito guardado (si existe). Reutiliza el comportamiento de la entidad: Carrito.vaciar(). */
    @Override
    @Transactional
    public CarritoResponse vaciar(Long usuarioId) {
        carritos.buscarPorUsuario(usuarioId).ifPresent(c -> {
            c.vaciar();
            carritos.save(c);
        });
        return CarritoResponse.sinItems();
    }

    /** Carrito guardado del estudiante, o uno nuevo si aún no tiene. */
    private Carrito obtenerOCrear(Long usuarioId) {
        return carritos.buscarPorUsuario(usuarioId)
                .orElseGet(() -> new Carrito(usuarios.getReferenceById(usuarioId)));
    }

    /** Suma cantidades repetidas del mismo producto (el navegador podría enviar duplicados). */
    private static Map<Long, Integer> agrupar(ListaItemsRequest req) {
        Map<Long, Integer> mapa = new LinkedHashMap<>();
        req.items().forEach(i -> mapa.merge(i.productoId(), i.cantidad(), Integer::sum));
        return mapa;
    }

    /** Regla de disponibilidad en un único lugar (DRY): stock ≥ (en carrito + solicitado). */
    private DisponibilidadResponse evaluar(Producto p, int cantidad, int enCarrito) {
        int total = enCarrito + cantidad;
        boolean disponible = p.tieneStock(total) && total <= CarritoModel.MAX_UNIDADES_POR_ITEM;
        BigDecimal subtotal = p.getPrecio().multiply(BigDecimal.valueOf(cantidad));
        String mensaje;
        if (disponible) {
            mensaje = "Hay stock disponible. Subtotal: Bs. " + subtotal.setScale(2);
        } else if (total > CarritoModel.MAX_UNIDADES_POR_ITEM && p.tieneStock(total)) {
            mensaje = "Máximo " + CarritoModel.MAX_UNIDADES_POR_ITEM + " unidades por producto en el carrito.";
        } else {
            // Texto del criterio 3 de la HU: "Cantidad no disponible. Stock actual: [Cantidad]".
            mensaje = "Cantidad no disponible. Stock actual: " + p.getStock()
                    + (enCarrito > 0 ? " (ya tienes " + enCarrito + " en tu carrito)" : "");
        }
        return new DisponibilidadResponse(p.getId(), p.getNombre(), cantidad, enCarrito, p.getStock(),
                disponible, subtotal, mensaje);
    }

    private static void validarCantidad(int cantidad) {
        if (cantidad < 1 || cantidad > CarritoModel.MAX_UNIDADES_POR_ITEM) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "La cantidad debe estar entre 1 y 99.");
        }
    }
}
