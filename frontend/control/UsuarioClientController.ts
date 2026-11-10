/**
 * =====================================================================
 * UsuarioClientController — Capa CONTROL (cliente) del módulo Usuario.
 * ---------------------------------------------------------------------
 * HU-07 Registro de Cuenta · HU-02 Consultar Perfil (+ login/logout).
 *
 * Responsabilidad única (SRP): llamadas REST de usuario, validación previa
 * de formularios y el ESTADO DE SESIÓN compartido por toda la interfaz.
 *
 * SOLID: hereda de ApiClient (OCP); las vistas dependen de esta clase y no
 * de fetch (DIP). Se exporta UNA instancia (Singleton) para que todas las
 * vistas compartan la misma sesión.
 * =====================================================================
 */
import { reactive } from 'vue'
import { ApiClient, ApiError, type RespuestaApi } from './ApiClient'

export type Rol = 'ESTUDIANTE' | 'ADMINISTRADOR'

/** Perfil de solo lectura (HU-02): Nombre, Correo UCAB, Rol y Teléfono. */
export interface Perfil {
  id: number
  nombre: string
  correo: string
  rol: Rol
  telefono: string | null
}

/** Datos del formulario de registro (HU-07). */
export interface RegistroDatos {
  nombre: string
  correo: string
  telefono: string
  password: string
  confirmarPassword: string
}

class UsuarioClientController extends ApiClient {
  /** Mismo patrón que el backend: validación estricta del dominio UCAB. */
  static readonly REGEX_CORREO_UCAB = /^[a-z0-9._%+-]+@est\.ucab\.edu\.ve$/

  /** Igual que UsuarioModel.REGEX_TELEFONO del backend: código permitido + 7 dígitos, guion opcional. */
  static readonly REGEX_TELEFONO = /^0(412|414|416|424|426|212)-?[0-9]{7}$/

  /** Máximo de caracteres del campo teléfono ("0414-1234567"). */
  static readonly MAX_TELEFONO = 12

  /** Estado reactivo: cualquier vista que lo lea se actualiza sola al cambiar. */
  readonly estado = reactive<{ usuario: Perfil | null }>({ usuario: null })

  constructor() {
    super('/api/usuarios')
  }

  /** @returns true si hay una sesión activa. */
  get autenticado(): boolean {
    return this.estado.usuario !== null
  }

  /** @returns true si el usuario autenticado es administrador. */
  get esAdmin(): boolean {
    return this.estado.usuario?.rol === 'ADMINISTRADOR'
  }

  /** @returns true si el usuario autenticado es estudiante (único rol que compra y reseña). */
  get esEstudiante(): boolean {
    return this.estado.usuario?.rol === 'ESTUDIANTE'
  }

  /** Máximo de caracteres del teléfono, expuesto para las vistas. */
  get maxTelefono(): number {
    return UsuarioClientController.MAX_TELEFONO
  }

  /**
   * Validación en el cliente (UX inmediata). El backend repite TODAS las
   * reglas: el cliente nunca es la única barrera.
   * @returns mapa campo → mensaje; vacío si todo es válido
   */
  validarRegistro(d: RegistroDatos): Record<string, string> {
    const errores: Record<string, string> = {}
    if (!d.nombre.trim()) errores.nombre = 'El nombre es obligatorio'
    if (!UsuarioClientController.REGEX_CORREO_UCAB.test(d.correo.trim().toLowerCase())) {
      errores.correo = 'Use su correo institucional @est.ucab.edu.ve'
    }
    if (d.telefono.trim() && !UsuarioClientController.REGEX_TELEFONO.test(d.telefono.trim())) {
      errores.telefono = 'Use un código 0412, 0414, 0416, 0424, 0426 o 0212 y 7 dígitos (ej. 0414-1234567)'
    }
    if (d.password.length < 8) errores.password = 'La contraseña debe tener al menos 8 caracteres'
    if (d.password !== d.confirmarPassword) errores.confirmarPassword = 'Las contraseñas no coinciden'
    return errores
  }

  /**
   * POST /registro (HU-07). No inicia sesión automáticamente: el usuario
   * luego entra con sus credenciales.
   */
  registrar(d: RegistroDatos): Promise<RespuestaApi<Perfil>> {
    return this.post('/registro', {
      nombre: d.nombre.trim(),
      correo: d.correo.trim().toLowerCase(),
      telefono: d.telefono.trim(),
      password: d.password,
    })
  }

  /** POST /login: guarda el perfil en el estado compartido. */
  async iniciarSesion(correo: string, password: string): Promise<Perfil> {
    const perfil = await this.post<Perfil>('/login', { correo: correo.trim().toLowerCase(), password })
    this.estado.usuario = perfil
    return perfil
  }

  /** POST /logout y limpieza del estado local. */
  async cerrarSesion(): Promise<void> {
    try {
      await this.post('/logout')
    } finally {
      this.estado.usuario = null
    }
  }

  /**
   * GET /perfil (HU-02). Lectura directa desde la BD (no del caché local),
   * para reflejar siempre los datos reales de la sesión activa.
   * @throws ApiError 503 con "Error al cargar la información del perfil. Intente nuevamente"
   */
  async consultarPerfil(): Promise<Perfil> {
    const perfil = await this.get<Perfil>('/perfil')
    this.estado.usuario = perfil
    return perfil
  }

  /**
   * Al recargar la página, recupera la sesión si la cookie sigue vigente.
   * Un 401 aquí es normal (no hay sesión) y se ignora en silencio.
   */
  async restaurarSesion(): Promise<void> {
    try {
      await this.consultarPerfil()
    } catch (e) {
      if (!(e instanceof ApiError) || e.status !== 401) console.warn('No se pudo restaurar la sesión', e)
      this.estado.usuario = null
    }
  }
}

/** Instancia única compartida (Singleton). */
export const usuarioController = new UsuarioClientController()
