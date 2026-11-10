/**
 * =====================================================================
 * ApiClient — Clase base de la capa CONTROL del frontend.
 * ---------------------------------------------------------------------
 * Transversal a las 10 HU.
 *
 * Responsabilidad única (SRP): encapsular la comunicación HTTP con el
 * backend (fetch, cookie de sesión, timeout y traducción de errores).
 *
 * POO / SOLID:
 *  • Herencia + OCP: cada *ClientController extiende esta clase y agrega
 *    sus operaciones sin modificarla.
 *  • DIP: las vistas nunca llaman a fetch(); dependen de los controladores.
 * =====================================================================
 */

/** Sobre "mensaje + datos" que devuelve el backend (RespuestaApi<T>). */
export interface RespuestaApi<T> {
  mensaje: string
  datos: T
}

/**
 * Error de API con información útil para la vista.
 * @property status   código HTTP (0 = sin conexión / timeout)
 * @property errores  errores por campo (validación de formularios)
 * @property extra    cuerpo completo (p. ej. stockActual)
 */
export class ApiError extends Error {
  constructor(
    public readonly status: number,
    mensaje: string,
    public readonly errores: Record<string, string> = {},
    public readonly extra: Record<string, unknown> = {},
  ) {
    super(mensaje)
    this.name = 'ApiError'
  }
}

export abstract class ApiClient {
  /** Tiempo máximo de espera. El RNF pide < 2 s; 10 s evita colgar la UI si el servidor cae. */
  private static readonly TIMEOUT_MS = 10_000

  /** @param baseUrl prefijo del recurso REST, p. ej. "/api/productos" */
  protected constructor(private readonly baseUrl: string) {}

  /**
   * Ejecuta una petición HTTP y devuelve el JSON tipado.
   * @param ruta     sufijo de la URL ("" para la raíz del recurso)
   * @param opciones método, cuerpo, etc.
   * @returns cuerpo de la respuesta como T
   * @throws ApiError con el mensaje del backend o uno de conexión
   */
  protected async request<T>(ruta: string, opciones: RequestInit = {}): Promise<T> {
    const abortador = new AbortController()
    const temporizador = setTimeout(() => abortador.abort(), ApiClient.TIMEOUT_MS)
    try {
      const resp = await fetch(this.baseUrl + ruta, {
        ...opciones,
        credentials: 'include', // Envía la cookie JSESSIONID (sesión activa)
        headers: { 'Content-Type': 'application/json', ...(opciones.headers ?? {}) },
        signal: abortador.signal,
      })
      if (resp.status === 204) return undefined as T

      const cuerpo = await resp.json().catch(() => ({}))
      if (!resp.ok) {
        // El backend siempre responde { status, mensaje, errores? }: un único formato que interpretar.
        throw new ApiError(resp.status, cuerpo.mensaje ?? 'Ocurrió un error inesperado.', cuerpo.errores ?? {}, cuerpo)
      }
      return cuerpo as T
    } catch (e) {
      if (e instanceof ApiError) throw e
      // fetch solo falla así por red caída, servidor apagado o timeout.
      throw new ApiError(0, 'No se pudo conectar con el servidor. Intente nuevamente.')
    } finally {
      clearTimeout(temporizador)
    }
  }

  /** Atajo GET. */
  protected get<T>(ruta = ''): Promise<T> {
    return this.request<T>(ruta)
  }

  /** Atajo POST con cuerpo JSON opcional. */
  protected post<T>(ruta = '', cuerpo?: unknown): Promise<T> {
    return this.request<T>(ruta, { method: 'POST', body: cuerpo === undefined ? undefined : JSON.stringify(cuerpo) })
  }

  /** Atajo DELETE (borrar un recurso; p. ej. vaciar el carrito). */
  protected eliminar<T>(ruta = ''): Promise<T> {
    return this.request<T>(ruta, { method: 'DELETE' })
  }
}

/**
 * Formatea un monto en Bolívares (única moneda del sistema).
 * @example formatearBs(1500) → "Bs. 1.500,00"
 */
export function formatearBs(monto: number): string {
  return 'Bs. ' + Number(monto).toLocaleString('es-VE', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

/** Formatea una fecha ISO del backend en formato local venezolano. */
export function formatearFecha(iso: string): string {
  return new Date(iso).toLocaleDateString('es-VE', { day: '2-digit', month: 'short', year: 'numeric' })
}
