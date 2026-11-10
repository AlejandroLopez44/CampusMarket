package control;

import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import model.UsuarioModel.Rol;

/**
 * =====================================================================
 * CampusMarketApplication — Punto de arranque y piezas transversales de la
 * capa CONTROL.
 * ---------------------------------------------------------------------
 * Aplica a TODAS las HU (infraestructura común).
 *
 * Contiene, además del main():
 *   • {@link NegocioException}: error de regla de negocio con su código HTTP.
 *   • {@link ManejadorGlobalErrores}: traduce excepciones a JSON uniforme.
 *   • {@link SesionActiva}: obtiene el usuario autenticado de la sesión HTTP.
 *   • {@link RespuestaApi}: sobre genérico "mensaje + datos".
 *
 * SOLID:
 *   • SRP — cada tipo auxiliar tiene una sola responsabilidad.
 *   • DIP — el codificador de contraseñas se expone como la interfaz
 *     {@link PasswordEncoder}; los servicios no saben que es BCrypt.
 * =====================================================================
 */
@SpringBootApplication(scanBasePackages = "control")
@EntityScan(basePackages = "model")
// considerNestedRepositories: los repositorios están declarados como interfaces
// anidadas dentro de cada *Model (estructura de un archivo por módulo).
@EnableJpaRepositories(basePackages = "model", considerNestedRepositories = true)
@EnableAsync // Permite que el envío de correos no bloquee la respuesta HTTP
public class CampusMarketApplication {

    /** Arranca el servidor embebido (Tomcat) en el puerto 8080. */
    public static void main(String[] args) {
        SpringApplication.run(CampusMarketApplication.class, args);
    }

    /**
     * Bean del codificador de contraseñas.
     * @return implementación BCrypt detrás de la abstracción PasswordEncoder (DIP).
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Sobre de respuesta para operaciones que deben mostrar un mensaje
     * al usuario (p. ej. "Producto registrado con éxito") junto con datos.
     * @param <T> tipo del dato devuelto
     */
    public record RespuestaApi<T>(String mensaje, T datos) { }
}

// =====================================================================
// Excepción de negocio
// =====================================================================

/**
 * Representa la violación de una regla del dominio (stock insuficiente,
 * correo duplicado, reseña repetida...). Lleva el código HTTP adecuado y,
 * opcionalmente, datos extra (p. ej. el stock actual) para la interfaz.
 */
class NegocioException extends RuntimeException {

    private final HttpStatus estado;
    private final Map<String, Object> detalles;

    NegocioException(HttpStatus estado, String mensaje) {
        this(estado, mensaje, Map.of());
    }

    NegocioException(HttpStatus estado, String mensaje, Map<String, Object> detalles) {
        super(mensaje);
        this.estado = estado;
        this.detalles = detalles;
    }

    HttpStatus getEstado() { return estado; }
    Map<String, Object> getDetalles() { return detalles; }
}

// =====================================================================
// Manejo centralizado de errores
// =====================================================================

/**
 * Convierte cualquier excepción en un JSON con la forma:
 * <pre>{ "status": 409, "mensaje": "...", "errores": {campo: msg}, ...detalles }</pre>
 * Así el frontend tiene UN solo formato de error que interpretar (bajo acoplamiento).
 */
@RestControllerAdvice
class ManejadorGlobalErrores {

    private static final Logger log = LoggerFactory.getLogger(ManejadorGlobalErrores.class);

    /** Reglas de negocio: se respeta el código HTTP definido por el servicio. */
    @ExceptionHandler(NegocioException.class)
    ResponseEntity<Map<String, Object>> negocio(NegocioException ex) {
        Map<String, Object> cuerpo = cuerpo(ex.getEstado(), ex.getMessage());
        cuerpo.putAll(ex.getDetalles());
        return ResponseEntity.status(ex.getEstado()).body(cuerpo);
    }

    /** Errores de Bean Validation (@Valid): se devuelven campo por campo. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> validacion(MethodArgumentNotValidException ex) {
        Map<String, String> errores = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
          .forEach(e -> errores.putIfAbsent(e.getField(), e.getDefaultMessage()));
        Map<String, Object> cuerpo = cuerpo(HttpStatus.BAD_REQUEST, "Datos inválidos. Revise los campos señalados.");
        cuerpo.put("errores", errores);
        return ResponseEntity.badRequest().body(cuerpo);
    }

    /** JSON mal formado o parámetros de tipo incorrecto. */
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
                       MissingServletRequestParameterException.class})
    ResponseEntity<Map<String, Object>> peticionInvalida(Exception ex) {
        return ResponseEntity.badRequest().body(cuerpo(HttpStatus.BAD_REQUEST, "La petición no tiene un formato válido."));
    }

    /**
     * Última barrera: un CONSTRAINT de la BD (UNIQUE/CHECK) rechazó el dato,
     * p. ej. por dos peticiones simultáneas que pasaron la validación previa.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Map<String, Object>> integridad(DataIntegrityViolationException ex) {
        log.warn("Violación de integridad: {}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(cuerpo(HttpStatus.CONFLICT, "La operación entra en conflicto con datos existentes."));
    }

    /** Cualquier otro error: se registra en el log y NO se filtran detalles internos al cliente. */
    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, Object>> inesperado(Exception ex) {
        log.error("Error inesperado", ex);
        return ResponseEntity.internalServerError()
                .body(cuerpo(HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error inesperado. Intente nuevamente."));
    }

    private static Map<String, Object> cuerpo(HttpStatus estado, String mensaje) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", estado.value());
        m.put("mensaje", mensaje);
        return m;
    }
}

// =====================================================================
// Sesión activa
// =====================================================================

/**
 * Encapsula el acceso a la sesión HTTP (cookie JSESSIONID).
 * Los controladores preguntan "¿quién es el usuario?" sin conocer las
 * claves internas de la sesión (encapsulamiento + SRP).
 */
@Component
class SesionActiva {

    static final String ATR_USUARIO_ID = "usuarioId";
    static final String ATR_ROL = "rol";

    /** Guarda la identidad del usuario tras un login exitoso. */
    void iniciar(HttpSession sesion, Long usuarioId, String rol) {
        sesion.setAttribute(ATR_USUARIO_ID, usuarioId);
        sesion.setAttribute(ATR_ROL, rol);
    }

    /**
     * @return id del usuario autenticado.
     * @throws NegocioException 401 si no hay sesión iniciada.
     */
    Long usuarioId(HttpSession sesion) {
        Object id = sesion.getAttribute(ATR_USUARIO_ID);
        if (id == null) {
            throw new NegocioException(HttpStatus.UNAUTHORIZED, "Debe iniciar sesión para continuar.");
        }
        return (Long) id;
    }

    /**
     * Verifica que el usuario autenticado sea ADMINISTRADOR (HU-03).
     * @throws NegocioException 401 sin sesión, 403 si no es administrador.
     */
    void exigirAdministrador(HttpSession sesion) {
        usuarioId(sesion);
        if (!Rol.ADMINISTRADOR.name().equals(sesion.getAttribute(ATR_ROL))) {
            throw new NegocioException(HttpStatus.FORBIDDEN, "Solo un administrador puede realizar esta acción.");
        }
    }

    /**
     * Verifica que el usuario autenticado sea ESTUDIANTE. Comprar, usar el
     * carrito guardado y escribir reseñas son acciones exclusivas del estudiante:
     * el administrador gestiona el negocio, no compra.
     * @return id del estudiante autenticado
     * @throws NegocioException 401 sin sesión, 403 si es administrador.
     */
    Long exigirEstudiante(HttpSession sesion) {
        Long id = usuarioId(sesion);
        if (!Rol.ESTUDIANTE.name().equals(sesion.getAttribute(ATR_ROL))) {
            throw new NegocioException(HttpStatus.FORBIDDEN, "Esta acción es solo para estudiantes.");
        }
        return id;
    }

    /**
     * Para endpoints que funcionan con o sin sesión (p. ej. el carrito de invitado).
     * @return id del usuario si hay sesión; Optional vacío si es un visitante
     */
    Optional<Long> usuarioIdOpcional(HttpSession sesion) {
        return Optional.ofNullable((Long) sesion.getAttribute(ATR_USUARIO_ID));
    }

    /** @return true si la sesión pertenece a un administrador. */
    boolean esAdministrador(HttpSession sesion) {
        return Rol.ADMINISTRADOR.name().equals(sesion.getAttribute(ATR_ROL));
    }
}
