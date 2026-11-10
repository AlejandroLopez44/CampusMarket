package control;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

import control.CampusMarketApplication.RespuestaApi;
import model.CarritoModel;
import model.CarritoModel.*;
import model.ProductoModel.Producto;
import model.UsuarioModel.UsuarioRepository;

/**
 * =====================================================================
 * CarritoController — Capa CONTROL del módulo Carrito.
 * ---------------------------------------------------------------------
 * HU-04 Agregar a Carrito · HU-08 Consultar Carrito.
 *
 * Flujo HU-04 (diagrama "Agregar a Carrito", corregido):
 *   1. El estudiante elige producto + cantidad y pulsa "Agregar al carrito".
 *   2. GET /disponibilidad → el sistema consulta el inventario en BD.
 *      • Stock insuficiente → se BLOQUEA la acción y se informa el stock actual.
 *        El usuario puede [Salir] (volver al catálogo) u [OK].
 *      • Stock disponible → la vista muestra el subtotal en Bs. y pide confirmación.
 *   3. [Confirmar] → POST /items → se re-verifica el stock (pudo cambiar),
 *      se guarda en BD y se devuelve el carrito con total y unidades (ícono).
 *
 * SOLID: SRP, DIP (CarritoService / repositorios como interfaces).
 * RNF < 2 s: una sola consulta con JOIN FETCH por operación.
 * =====================================================================
 */
@RestController
@RequestMapping("/api/carrito")
public class CarritoController {

    private final CarritoService carritoService;
    private final SesionActiva sesionActiva;

    CarritoController(CarritoService carritoService, SesionActiva sesionActiva) {
        this.carritoService = carritoService;
        this.sesionActiva = sesionActiva;
    }

    /** GET /api/carrito — HU-08: ítems, cantidades y total acumulado en Bs. */
    @GetMapping
    CarritoResponse consultar(HttpSession sesion) {
        return carritoService.consultar(sesionActiva.usuarioId(sesion));
    }

    /**
     * GET /api/carrito/disponibilidad?productoId=1&cantidad=2 — HU-04 paso 2.
     * No modifica nada: solo informa si se puede agregar y el subtotal.
     */
    @GetMapping("/disponibilidad")
    DisponibilidadResponse disponibilidad(@RequestParam Long productoId,
                                          @RequestParam int cantidad, HttpSession sesion) {
        return carritoService.verificarDisponibilidad(sesionActiva.usuarioId(sesion), productoId, cantidad);
    }

    /** POST /api/carrito/items — HU-04 paso 3 (tras la confirmación del usuario). */
    @PostMapping("/items")
    RespuestaApi<CarritoResponse> agregar(@Valid @RequestBody AgregarItemRequest req, HttpSession sesion) {
        CarritoResponse carrito = carritoService.agregar(sesionActiva.usuarioId(sesion), req);
        return new RespuestaApi<>("Producto agregado al carrito.", carrito);
    }
}

// =====================================================================
// Servicio
// =====================================================================

/** Contrato de negocio del carrito. */
interface CarritoService {
    CarritoResponse consultar(Long usuarioId);
    DisponibilidadResponse verificarDisponibilidad(Long usuarioId, Long productoId, int cantidad);
    CarritoResponse agregar(Long usuarioId, AgregarItemRequest req);
}

@Service
class CarritoServiceImpl implements CarritoService {

    private final CarritoRepository carritos;
    private final UsuarioRepository usuarios;
    private final ProductoService productoService; // Reutiliza la búsqueda de producto (no duplica lógica)

    CarritoServiceImpl(CarritoRepository carritos, UsuarioRepository usuarios, ProductoService productoService) {
        this.carritos = carritos;
        this.usuarios = usuarios;
        this.productoService = productoService;
    }

    /** HU-08. Si el estudiante aún no tiene carrito, se responde un carrito vacío (sin crear filas). */
    @Override
    @Transactional(readOnly = true)
    public CarritoResponse consultar(Long usuarioId) {
        return carritos.buscarPorUsuario(usuarioId).map(CarritoResponse::desde).orElseGet(CarritoResponse::sinItems);
    }

    /**
     * Verificación de inventario. Se suma lo que YA está en el carrito: si hay
     * 5 en stock y el carrito tiene 4, pedir 2 más debe fallar.
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
     * Guarda el ítem. Se RE-VERIFICA el stock porque entre la consulta de
     * disponibilidad y la confirmación otro usuario pudo comprar unidades.
     * Nota: aquí NO se descuenta inventario; eso ocurre al generar la orden (HU-05).
     */
    @Override
    @Transactional
    public CarritoResponse agregar(Long usuarioId, AgregarItemRequest req) {
        Carrito carrito = carritos.buscarPorUsuario(usuarioId)
                .orElseGet(() -> new Carrito(usuarios.getReferenceById(usuarioId)));
        Producto p = productoService.obtenerEntidad(req.productoId());

        DisponibilidadResponse d = evaluar(p, req.cantidad(), carrito.cantidadDe(p.getId()));
        if (!d.disponible()) {
            // Rama "Stock Insuficiente": se bloquea la acción y se informa el stock actual.
            throw new NegocioException(HttpStatus.CONFLICT, d.mensaje(), Map.of("stockActual", d.stockActual()));
        }

        carrito.agregar(p, req.cantidad());
        return CarritoResponse.desde(carritos.save(carrito));
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
            mensaje = "Stock insuficiente de " + p.getNombre() + ". Stock actual: " + p.getStock() + " unidad(es)"
                    + (enCarrito > 0 ? " (ya tienes " + enCarrito + " en tu carrito)." : ".");
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
