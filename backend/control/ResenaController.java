package control;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import control.CampusMarketApplication.RespuestaApi;
import model.CompraModel.CompraRepository;
import model.ProductoModel.Producto;
import model.ResenaModel;
import model.ResenaModel.*;
import model.UsuarioModel.UsuarioRepository;

/**
 * =====================================================================
 * ResenaController — Capa CONTROL del módulo Reseñas.
 * ---------------------------------------------------------------------
 * HU-01 Consultar Reseña · HU-10 Insertar Reseña.
 *
 * Flujo HU-01 (diagrama "Consultar reseña"): el sistema busca las reseñas
 * del producto → [existen] lista (autor, fecha, texto) · [no existen]
 * estado vacío "No hay reseñas disponibles para este producto".
 *
 * Flujo HU-10: valida que el producto exista, que el estudiante lo haya
 * comprado, que no tenga ya una reseña para él y el límite de caracteres;
 * solo entonces persiste.
 *
 * SOLID: SRP, DIP (repositorios como interfaces).
 * =====================================================================
 */
@RestController
@RequestMapping("/api/productos/{productoId}/resenas")
public class ResenaController {

    private final ResenaService resenaService;
    private final SesionActiva sesionActiva;

    ResenaController(ResenaService resenaService, SesionActiva sesionActiva) {
        this.resenaService = resenaService;
        this.sesionActiva = sesionActiva;
    }

    /** GET /api/productos/{id}/resenas — HU-01 (público). */
    @GetMapping
    ListaResenasResponse consultar(@PathVariable Long productoId) {
        return resenaService.consultar(productoId);
    }

    /** POST /api/productos/{id}/resenas — HU-10 (solo estudiantes; el admin solo lee). */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    RespuestaApi<ResenaResponse> insertar(@PathVariable Long productoId,
                                          @Valid @RequestBody ResenaRequest req, HttpSession sesion) {
        ResenaResponse r = resenaService.insertar(sesionActiva.exigirEstudiante(sesion), productoId, req);
        return new RespuestaApi<>("¡Reseña publicada con éxito!", r);
    }
}

// =====================================================================
// Servicio
// =====================================================================

/** Contrato de negocio de reseñas. */
interface ResenaService {
    ListaResenasResponse consultar(Long productoId);
    ResenaResponse insertar(Long usuarioId, Long productoId, ResenaRequest req);
}

@Service
class ResenaServiceImpl implements ResenaService {

    private final ResenaRepository resenas;
    private final CompraRepository compras;
    private final UsuarioRepository usuarios;
    private final ProductoService productoService;

    ResenaServiceImpl(ResenaRepository resenas, CompraRepository compras,
                      UsuarioRepository usuarios, ProductoService productoService) {
        this.resenas = resenas;
        this.compras = compras;
        this.usuarios = usuarios;
        this.productoService = productoService;
    }

    /**
     * HU-01. Un producto inexistente responde 404; uno sin reseñas responde
     * 200 con lista vacía + mensaje (el estado vacío NO es un error).
     */
    @Override
    @Transactional(readOnly = true)
    public ListaResenasResponse consultar(Long productoId) {
        Producto p = productoService.obtenerEntidad(productoId);
        List<ResenaResponse> lista = resenas.buscarPorProducto(productoId)
                .stream().map(ResenaResponse::desde).toList();
        return new ListaResenasResponse(p.getId(), p.getNombre(), lista, lista.isEmpty(),
                lista.isEmpty() ? ResenaModel.MENSAJE_SIN_RESENAS : null);
    }

    /**
     * HU-10. Orden de validaciones (de la más barata a la más costosa):
     *  1) Texto no vacío y ≤ 500 caracteres (tras recortar espacios).
     *  2) El producto existe (404).
     *  3) El estudiante compró el producto (403).
     *  4) No existe ya una reseña suya para ese producto (409).
     */
    @Override
    @Transactional
    public ResenaResponse insertar(Long usuarioId, Long productoId, ResenaRequest req) {
        String texto = req.texto().trim();
        if (texto.isEmpty() || texto.length() > ResenaModel.MAX_CARACTERES) {
            throw new NegocioException(HttpStatus.BAD_REQUEST,
                    "El comentario debe tener entre 1 y " + ResenaModel.MAX_CARACTERES + " caracteres.");
        }
        Producto producto = productoService.obtenerEntidad(productoId);

        if (!compras.usuarioComproProducto(usuarioId, productoId)) {
            throw new NegocioException(HttpStatus.FORBIDDEN,
                    "Solo puedes reseñar productos que hayas comprado.");
        }
        if (resenas.existsByProductoIdAndUsuarioId(productoId, usuarioId)) {
            throw new NegocioException(HttpStatus.CONFLICT, "Ya registraste una reseña para este producto.");
        }

        Resena r = new Resena(producto, usuarios.getReferenceById(usuarioId), texto, req.calificacion());
        resenas.save(r);
        // getReferenceById es un proxy: se inicializa aquí para poder leer el nombre del autor.
        return ResenaResponse.desde(r);
    }
}
