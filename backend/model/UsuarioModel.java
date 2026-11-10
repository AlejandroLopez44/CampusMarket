package model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * =====================================================================
 * UsuarioModel — Capa MODEL del módulo Usuario.
 * ---------------------------------------------------------------------
 * HU-07 Registro de Cuenta · HU-02 Consultar Perfil.
 *
 * Responsabilidad única (SRP): agrupar TODO lo que describe los datos de un
 * usuario — la entidad persistente, los DTOs de entrada/salida, el evento de
 * dominio y el contrato del repositorio. NO contiene lógica de negocio
 * (esa vive en control/UsuarioController.java).
 *
 * ¿Por qué una clase contenedora con tipos anidados? La estructura del
 * proyecto exige un único archivo por módulo; Java solo permite un tipo
 * público por archivo, así que los tipos se declaran como miembros
 * estáticos de esta clase (no se puede instanciar: constructor privado).
 *
 * Principio SOLID aplicado: DIP — los servicios dependen de la INTERFAZ
 * {@link UsuarioRepository}; Spring Data genera la implementación concreta
 * en tiempo de ejecución, por lo que la lógica nunca conoce SQL ni JDBC.
 * =====================================================================
 */
public final class UsuarioModel {

    private UsuarioModel() { }

    /** Dominio institucional permitido (Ámbito UCAB). */
    public static final String DOMINIO_UCAB = "@est.ucab.edu.ve";

    /** Expresión regular estricta para el correo estudiantil (en minúsculas). */
    public static final String REGEX_CORREO_UCAB = "^[a-z0-9._%+-]+@est\\.ucab\\.edu\\.ve$";

    /**
     * Teléfono venezolano: código permitido + 7 dígitos, guion opcional al escribir.
     * Móviles 0412, 0414, 0416, 0424, 0426 y fijo 0212 (el Brief limita la operación
     * a la Gran Caracas). Ejemplos válidos: "0414-1234567" o "04141234567".
     */
    public static final String REGEX_TELEFONO = "^0(412|414|416|424|426|212)-?[0-9]{7}$";

    /** Mensaje único para el error de teléfono (se reutiliza en DTO y servicio). */
    public static final String MENSAJE_TELEFONO =
            "Teléfono inválido. Use un código 0412, 0414, 0416, 0424, 0426 o 0212 y 7 dígitos (ej. 0414-1234567)";

    /**
     * Normaliza un teléfono válido al formato con guion "0414-1234567",
     * que es el único que acepta la base de datos.
     * @param telefono texto escrito por el usuario (puede ser null o vacío)
     * @return teléfono normalizado, o null si venía vacío
     */
    public static String normalizarTelefono(String telefono) {
        if (telefono == null || telefono.isBlank()) return null;
        String digitos = telefono.replace("-", "").trim();
        return digitos.substring(0, 4) + "-" + digitos.substring(4);
    }

    /** Roles posibles. Un enum evita "cadenas mágicas" y errores de escritura. */
    public enum Rol { ESTUDIANTE, ADMINISTRADOR }

    // -----------------------------------------------------------------
    // ENTIDAD JPA  → tabla "usuario"
    // -----------------------------------------------------------------

    /**
     * Entidad persistente Usuario. Se mapea 1:1 con la tabla {@code usuario}.
     * Encapsulamiento (POO): atributos privados, acceso por getters/setters.
     */
    @Entity(name = "Usuario")
    @Table(name = "usuario")
    public static class Usuario {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY) // BIGSERIAL en PostgreSQL
        private Long id;

        @Column(nullable = false, length = 100)
        private String nombre;

        @Column(nullable = false, unique = true, length = 120)
        private String correo;

        /** Hash BCrypt. La contraseña en texto plano jamás se guarda. */
        @Column(name = "password_hash", nullable = false, length = 100)
        private String passwordHash;

        @Column(length = 20)
        private String telefono;

        @Enumerated(EnumType.STRING) // Se guarda "ESTUDIANTE" y no un número
        @Column(nullable = false, length = 20)
        private Rol rol = Rol.ESTUDIANTE;

        @Column(name = "fecha_registro", nullable = false)
        private LocalDateTime fechaRegistro = LocalDateTime.now();

        public Long getId() { return id; }
        public String getNombre() { return nombre; }
        public void setNombre(String nombre) { this.nombre = nombre; }
        public String getCorreo() { return correo; }
        public void setCorreo(String correo) { this.correo = correo; }
        public String getPasswordHash() { return passwordHash; }
        public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
        public String getTelefono() { return telefono; }
        public void setTelefono(String telefono) { this.telefono = telefono; }
        public Rol getRol() { return rol; }
        public void setRol(Rol rol) { this.rol = rol; }
        public LocalDateTime getFechaRegistro() { return fechaRegistro; }

        /** @return true si el usuario tiene permisos de administración. */
        public boolean esAdministrador() { return rol == Rol.ADMINISTRADOR; }
    }

    // -----------------------------------------------------------------
    // DTOs (Data Transfer Objects)
    // Se usan "records" (inmutables) para que la API nunca exponga la
    // entidad directamente (p. ej. el passwordHash).
    // -----------------------------------------------------------------

    /**
     * Datos de entrada del formulario de registro (HU-07).
     * Las anotaciones de Bean Validation se evalúan automáticamente con @Valid.
     */
    public record RegistroRequest(
            @NotBlank(message = "El nombre es obligatorio")
            @Size(max = 100, message = "El nombre admite máximo 100 caracteres")
            String nombre,

            @NotBlank(message = "El correo es obligatorio")
            @Email(message = "El correo no tiene un formato válido")
            String correo,

            @NotBlank(message = "La contraseña es obligatoria")
            @Size(min = 8, max = 64, message = "La contraseña debe tener entre 8 y 64 caracteres")
            String password,

            @Size(max = 12, message = "El teléfono admite máximo 12 caracteres")
            @Pattern(regexp = "^$|" + REGEX_TELEFONO, message = MENSAJE_TELEFONO)
            String telefono
    ) { }

    /** Credenciales de inicio de sesión (necesario para tener una "sesión activa"). */
    public record LoginRequest(
            @NotBlank(message = "El correo es obligatorio") String correo,
            @NotBlank(message = "La contraseña es obligatoria") String password
    ) { }

    /**
     * Vista de solo lectura del perfil (HU-02): Nombre, Correo UCAB, Rol y Teléfono.
     * Factoría estática {@link #desde(Usuario)} = conversión Entidad → DTO en un solo lugar.
     */
    public record PerfilResponse(Long id, String nombre, String correo, String rol, String telefono) {
        public static PerfilResponse desde(Usuario u) {
            return new PerfilResponse(u.getId(), u.getNombre(), u.getCorreo(), u.getRol().name(), u.getTelefono());
        }
    }

    // -----------------------------------------------------------------
    // EVENTO DE DOMINIO
    // -----------------------------------------------------------------

    /**
     * Se publica cuando una cuenta queda registrada. El servicio de usuarios
     * NO conoce al servicio de correo: solo anuncia "esto ocurrió" y quien
     * esté interesado (EmailNotificationController) reacciona. (OCP + bajo acoplamiento)
     */
    public record UsuarioRegistradoEvent(String nombre, String correo) { }

    // -----------------------------------------------------------------
    // REPOSITORIO (abstracción de persistencia)
    // -----------------------------------------------------------------

    /**
     * Contrato de acceso a datos de usuarios. Spring Data deriva el SQL
     * a partir del nombre del método (p. ej. findByCorreo → WHERE correo = ?).
     */
    public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
        Optional<Usuario> findByCorreo(String correo);
        boolean existsByCorreo(String correo);
    }
}
