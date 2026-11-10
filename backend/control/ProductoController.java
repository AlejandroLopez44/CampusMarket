package control;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import control.CampusMarketApplication.RespuestaApi;
import model.ProductoModel.*;

/**
 * =====================================================================
 * ProductoController — Capa CONTROL del módulo Producto.
 * ---------------------------------------------------------------------
 * HU-06 Consultar Producto · HU-03 Agregar Producto (Administrador).
 *
 * Flujo HU-03 (diagrama "Registro de nuevo producto", corregido):
 *   Formulario → [OK] → POST /validacion (valida datos + verifica que el
 *   código no exista, SIN guardar) → la vista pide confirmación →
 *   POST /api/productos (re-valida y persiste) → mensaje de éxito →
 *   el admin confirma lectura. [Cancelar] en cualquier paso = salir.
 *
 * SOLID: SRP (servicio ≠ adaptador HTTP), DIP (dependencia de interfaces),
 * ISP (ProductoService expone solo lo que necesitan sus clientes).
 * =====================================================================
 */
@RestController
@RequestMapping("/api/productos")
public class ProductoController {

    private final ProductoService productoService;
    private final SesionActiva sesionActiva;

    ProductoController(ProductoService productoService, SesionActiva sesionActiva) {
        this.productoService = productoService;
        this.sesionActiva = sesionActiva;
    }

    /**
     * GET /api/productos?q=texto&categoria=Papelería — HU-06 catálogo.
     * Ambos filtros son opcionales. Público (no requiere sesión).
     */
    @GetMapping
    List<ProductoResponse> catalogo(@RequestParam(required = false) String q,
                                    @RequestParam(required = false) String categoria) {
        return productoService.buscar(q, categoria);
    }

    /** GET /api/productos/{id} — HU-06 detalle con stock y precio en Bs. */
    @GetMapping("/{id}")
    ProductoResponse detalle(@PathVariable Long id) {
        return productoService.detalle(id);
    }

    /**
     * POST /api/productos/validacion — HU-03, paso previo a la confirmación.
     * Verifica reglas y unicidad del código pero NO persiste nada.
     */
    @PostMapping("/validacion")
    RespuestaApi<ProductoRequest> validar(@Valid @RequestBody ProductoRequest req, HttpSession sesion) {
        sesionActiva.exigirAdministrador(sesion);
        productoService.validarNuevo(req);
        return new RespuestaApi<>("Datos válidos. Confirme para registrar el producto en el catálogo.", req);
    }

    /**
     * POST /api/productos — HU-03, persistencia tras la confirmación del admin.
     * @return 201 + mensaje de éxito + producto creado
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    RespuestaApi<ProductoResponse> registrar(@Valid @RequestBody ProductoRequest req, HttpSession sesion) {
        sesionActiva.exigirAdministrador(sesion);
        ProductoResponse creado = productoService.registrar(req);
        return new RespuestaApi<>("¡Producto \"" + creado.nombre() + "\" registrado con éxito!", creado);
    }
}

// =====================================================================
// Servicio
// =====================================================================

/** Contrato de negocio del catálogo. */
interface ProductoService {
    List<ProductoResponse> buscar(String texto, String categoria);
    ProductoResponse detalle(Long id);
    /** Valida un producto nuevo sin guardarlo. Lanza NegocioException si no es válido. */
    void validarNuevo(ProductoRequest req);
    ProductoResponse registrar(ProductoRequest req);
    /** Uso interno de otros servicios (carrito, reseñas): entidad o 404. */
    Producto obtenerEntidad(Long id);
}

@Service
class ProductoServiceImpl implements ProductoService {

    private final ProductoRepository productos;

    ProductoServiceImpl(ProductoRepository productos) {
        this.productos = productos;
    }

    /**
     * Búsqueda del catálogo. Cadenas vacías se convierten en null para que
     * la consulta ignore ese filtro.
     */
    @Override
    @Transactional(readOnly = true)
    public List<ProductoResponse> buscar(String texto, String categoria) {
        return productos.buscar(vacioANull(texto), vacioANull(categoria))
                .stream().map(ProductoResponse::desde).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ProductoResponse detalle(Long id) {
        return ProductoResponse.desde(obtenerEntidad(id));
    }

    /**
     * Reglas HU-03 que requieren la BD. Las de formato (precio > 0, stock ≥ 0,
     * imagen obligatoria) ya las garantizó @Valid sobre ProductoRequest.
     * Se repite precio/stock como defensa por si el servicio se invoca sin @Valid.
     */
    @Override
    @Transactional(readOnly = true)
    public void validarNuevo(ProductoRequest req) {
        if (req.precio() == null || req.precio().signum() <= 0) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "El precio debe ser mayor a 0 Bs.");
        }
        if (req.stock() == null || req.stock() < 0) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "El stock no puede ser menor a 0.");
        }
        if (productos.existsByCodigoIgnoreCase(req.codigo().trim())) {
            // Rama "[el producto existe]" del diagrama → criterio 1: alerta de "producto duplicado".
            throw new NegocioException(HttpStatus.CONFLICT,
                    "Producto duplicado: el código " + req.codigo().trim().toUpperCase() + " ya existe en el catálogo.");
        }
    }

    /**
     * Persiste el producto. Vuelve a validar dentro de la MISMA transacción
     * (otro admin pudo registrar el mismo código entre validar y confirmar);
     * el UNIQUE de la BD es la última barrera.
     */
    @Override
    @Transactional
    public ProductoResponse registrar(ProductoRequest req) {
        validarNuevo(req);
        Producto p = new Producto();
        p.setCodigo(req.codigo().trim().toUpperCase());
        p.setNombre(req.nombre().trim());
        p.setDescripcion(vacioANull(req.descripcion()));
        p.setCategoria(req.categoria().trim());
        p.setPrecio(req.precio());
        p.setStock(req.stock());
        p.setImagenUrl(req.imagenUrl().trim());
        return ProductoResponse.desde(productos.save(p));
    }

    @Override
    @Transactional(readOnly = true)
    public Producto obtenerEntidad(Long id) {
        return productos.findById(id)
                .orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND, "El producto solicitado no existe."));
    }

    private static String vacioANull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
