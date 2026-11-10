/**
 * =====================================================================
 * ResenaClientController — Capa CONTROL (cliente) del módulo Reseñas.
 * ---------------------------------------------------------------------
 * HU-01 Consultar Reseña · HU-10 Insertar Reseña.
 *
 * Responsabilidad única (SRP): llamadas REST de reseñas y validación previa
 * (límite de caracteres y calificación). Las reglas "haber comprado" y
 * "1 reseña por producto" solo puede comprobarlas el servidor.
 * =====================================================================
 */
import { ApiClient, type RespuestaApi } from './ApiClient'

export interface Resena {
  id: number
  autor: string
  fecha: string
  texto: string
  calificacion: number
}

/** Respuesta de "Consultar reseñas": si vacia=true, mostrar `mensaje`. */
export interface ListaResenas {
  productoId: number
  producto: string
  resenas: Resena[]
  vacia: boolean
  mensaje: string | null
}

class ResenaClientController extends ApiClient {
  /** Debe coincidir con ResenaModel.MAX_CARACTERES del backend. */
  static readonly MAX_CARACTERES = 500

  constructor() {
    super('/api/productos')
  }

  /** GET /api/productos/{id}/resenas (HU-01). */
  consultar(productoId: number): Promise<ListaResenas> {
    return this.get<ListaResenas>(`/${productoId}/resenas`)
  }

  /**
   * Validación local antes de enviar (HU-10).
   * @returns mensaje de error o null si es válido
   */
  validar(texto: string, calificacion: number): string | null {
    const t = texto.trim()
    if (!t) return 'El comentario no puede estar vacío.'
    if (t.length > ResenaClientController.MAX_CARACTERES) return `Máximo ${ResenaClientController.MAX_CARACTERES} caracteres.`
    if (!Number.isInteger(calificacion) || calificacion < 1 || calificacion > 5) return 'Seleccione una calificación de 1 a 5.'
    return null
  }

  /**
   * POST /api/productos/{id}/resenas (HU-10).
   * @throws ApiError 403 (no compró el producto) · 409 (ya tiene reseña)
   */
  insertar(productoId: number, texto: string, calificacion: number): Promise<RespuestaApi<Resena>> {
    return this.post(`/${productoId}/resenas`, { texto: texto.trim(), calificacion })
  }
}

export const resenaController = new ResenaClientController()
export const MAX_CARACTERES_RESENA = ResenaClientController.MAX_CARACTERES
