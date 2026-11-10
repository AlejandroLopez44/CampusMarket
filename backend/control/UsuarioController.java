package control;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;

import control.CampusMarketApplication.RespuestaApi;
import model.UsuarioModel;
import model.UsuarioModel.*;

/**
 * =====================================================================
 * UsuarioController — Capa CONTROL del módulo Usuario.
 * ---------------------------------------------------------------------
 * HU-07 Registro de Cuenta · HU-02 Consultar Perfil
 * (+ inicio/cierre de sesión, requisito técnico para tener "sesión activa").
 *
 * Este archivo contiene dos piezas con responsabilidades separadas (SRP):
 *   1. {@link UsuarioService} / {@link UsuarioServiceImpl}: reglas de negocio.
 *   2. {@link UsuarioController}: adaptador HTTP (rutas, códigos, sesión).
 *
 * SOLID:
 *   • DIP — el controlador depende de la interfaz UsuarioService; el
 *     servicio depende de UsuarioRepository y PasswordEncoder (interfaces).
 *   • OCP — el correo de bienvenida se dispara publicando un evento; añadir
 *     nuevas reacciones al registro no requiere modificar este servicio.
 * =====================================================================
 */
@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    /** Mensaje exacto exigido por la HU-02 ante fallo de base de datos. */
    static final String ERROR_PERFIL = "Error al cargar la información del perfil. Intente nuevamente";

    private final UsuarioService usuarioService;
    private final SesionActiva sesionActiva;

    /** Inyección por constructor: dependencias explícitas e inmutables. */
    UsuarioController(UsuarioService usuarioService, SesionActiva sesionActiva) {
        this.usuarioService = usuarioService;
        this.sesionActiva = sesionActiva;
    }

    /**
     * POST /api/usuarios/registro — HU-07.
     * @param req datos del formulario (validados con @Valid)
     * @return 201 + mensaje de bienvenida y el perfil creado
     */
    @PostMapping("/registro")
    @ResponseStatus(HttpStatus.CREATED)
    RespuestaApi<PerfilResponse> registrar(@Valid @RequestBody RegistroRequest req) {
        PerfilResponse perfil = usuarioService.registrar(req);
        return new RespuestaApi<>("¡Bienvenido, " + perfil.nombre() + "! Tu cuenta ha sido creada.", perfil);
    }

    /**
     * POST /api/usuarios/login — abre la sesión HTTP.
     * @return perfil del usuario autenticado (el navegador recibe la cookie JSESSIONID)
     */
    @PostMapping("/login")
    PerfilResponse login(@Valid @RequestBody LoginRequest req, HttpSession sesion) {
        PerfilResponse perfil = usuarioService.autenticar(req);
        sesionActiva.iniciar(sesion, perfil.id(), perfil.rol());
        return perfil;
    }

    /** POST /api/usuarios/logout — invalida la sesión actual. */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(HttpSession sesion) {
        sesion.invalidate();
    }

    /**
     * GET /api/usuarios/perfil — HU-02 (solo lectura de la sesión activa).
     * Cualquier falla de acceso a la BD se traduce al mensaje exacto de la HU
     * con 503, para que la vista ofrezca "Intentar nuevamente".
     */
    @GetMapping("/perfil")
    PerfilResponse perfil(HttpSession sesion) {
        Long usuarioId = sesionActiva.usuarioId(sesion);
        try {
            return usuarioService.consultarPerfil(usuarioId);
        } catch (DataAccessException | TransactionException ex) {
            // TransactionException cubre el caso "BD caída" al abrir la transacción.
            throw new NegocioException(HttpStatus.SERVICE_UNAVAILABLE, ERROR_PERFIL);
        }
    }
}

// =====================================================================
// Servicio (lógica de negocio)
// =====================================================================

/** Contrato de negocio del módulo Usuario. */
interface UsuarioService {
    /** Registra una cuenta UCAB nueva. @return perfil público creado */
    PerfilResponse registrar(RegistroRequest req);
    /** Valida credenciales. @return perfil si son correctas */
    PerfilResponse autenticar(LoginRequest req);
    /** Lee el perfil del usuario indicado (solo lectura). */
    PerfilResponse consultarPerfil(Long usuarioId);
}

@Service
class UsuarioServiceImpl implements UsuarioService {

    private final UsuarioRepository usuarios;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventos;

    UsuarioServiceImpl(UsuarioRepository usuarios, PasswordEncoder passwordEncoder,
                       ApplicationEventPublisher eventos) {
        this.usuarios = usuarios;
        this.passwordEncoder = passwordEncoder;
        this.eventos = eventos;
    }

    /**
     * Flujo HU-07:
     *  1) Normaliza el correo (trim + minúsculas) para evitar duplicados "Ana@" vs "ana@".
     *  2) Valida el dominio institucional @est.ucab.edu.ve (validación estricta).
     *  3) Verifica que el correo no esté registrado.
     *  4) Guarda la contraseña como hash BCrypt.
     *  5) Publica UsuarioRegistradoEvent → el correo de bienvenida se envía
     *     DESPUÉS del commit, sin que este servicio conozca al servicio de correo.
     */
    @Override
    @Transactional
    public PerfilResponse registrar(RegistroRequest req) {
        String correo = normalizarCorreo(req.correo());

        if (!correo.matches(UsuarioModel.REGEX_CORREO_UCAB)) {
            throw new NegocioException(HttpStatus.BAD_REQUEST,
                    "Solo se permiten correos institucionales " + UsuarioModel.DOMINIO_UCAB);
        }
        if (usuarios.existsByCorreo(correo)) {
            throw new NegocioException(HttpStatus.CONFLICT, "Ya existe una cuenta registrada con este correo.");
        }

        Usuario u = new Usuario();
        u.setNombre(req.nombre().trim());
        u.setCorreo(correo);
        u.setPasswordHash(passwordEncoder.encode(req.password()));
        u.setTelefono(req.telefono() == null || req.telefono().isBlank() ? null : req.telefono().trim());
        u.setRol(Rol.ESTUDIANTE); // El registro público SIEMPRE crea estudiantes (seguridad)
        usuarios.save(u);

        eventos.publishEvent(new UsuarioRegistradoEvent(u.getNombre(), u.getCorreo()));
        return PerfilResponse.desde(u);
    }

    /**
     * Autenticación. Se usa el MISMO mensaje para "correo inexistente" y
     * "clave incorrecta" para no revelar qué cuentas existen.
     */
    @Override
    @Transactional(readOnly = true)
    public PerfilResponse autenticar(LoginRequest req) {
        return usuarios.findByCorreo(normalizarCorreo(req.correo()))
                .filter(u -> passwordEncoder.matches(req.password(), u.getPasswordHash()))
                .map(PerfilResponse::desde)
                .orElseThrow(() -> new NegocioException(HttpStatus.UNAUTHORIZED, "Correo o contraseña incorrectos."));
    }

    /** HU-02: lectura pura; readOnly optimiza la transacción en PostgreSQL. */
    @Override
    @Transactional(readOnly = true)
    public PerfilResponse consultarPerfil(Long usuarioId) {
        return usuarios.findById(usuarioId)
                .map(PerfilResponse::desde)
                .orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND, UsuarioController.ERROR_PERFIL));
    }

    private static String normalizarCorreo(String correo) {
        return correo == null ? "" : correo.trim().toLowerCase(Locale.ROOT);
    }
}
