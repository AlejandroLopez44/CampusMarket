/**
 * =====================================================================
 * CarritoClientController — Capa CONTROL (cliente) del módulo Carrito.
 * ---------------------------------------------------------------------
 * HU-04 Agregar a Carrito · HU-08 Consultar Carrito.
 *
 * Responsabilidad única (SRP): llamadas REST del carrito y el estado
 * reactivo que alimenta el ícono/badge del encabezado.
 *
 * Importante: el carrito se guarda en la BASE DE DATOS (no en el navegador);
 * este estado es solo un reflejo de la última respuesta del servidor.
 * =====================================================================
 */
import { reactive } from 'vue'
import { ApiClient, type RespuestaApi } from './ApiClient'

export interface ItemCarrito {
  productoId: number
  codigo: string
  nombre: string
  imagenUrl: string
  precioUnitario: number
  cantidad: number
  subtotal: number
}

export interface Carrito {
  items: ItemCarrito[]
  cantidadTotal: number
  total: number
  moneda: 'Bs.'
  vacio: boolean
}

/** Resultado de la verificación de inventario (paso previo a confirmar). */
export interface Disponibilidad {
  productoId: number
  nombre: string
  cantidadSolicitada: number
  cantidadEnCarrito: number
  stockActual: number
  disponible: boolean
  subtotalBs: number
  mensaje: string
}

class CarritoClientController extends ApiClient {
  /** Estado compartido: el encabezado muestra cantidadTotal y la vista del carrito los ítems. */
  readonly estado = reactive<Carrito>({ items: [], cantidadTotal: 0, total: 0, moneda: 'Bs.', vacio: true })

  constructor() {
    super('/api/carrito')
  }

  /** GET /api/carrito (HU-08): refresca el estado con los datos de la BD. */
  async consultar(): Promise<Carrito> {
    const c = await this.get<Carrito>()
    this.actualizarEstado(c)
    return c
  }

  /**
   * GET /disponibilidad (HU-04, paso 2). No modifica nada en el servidor.
   * @param productoId producto elegido
   * @param cantidad   unidades deseadas (1..99)
   */
  verificarDisponibilidad(productoId: number, cantidad: number): Promise<Disponibilidad> {
    const qs = new URLSearchParams({ productoId: String(productoId), cantidad: String(cantidad) })
    return this.get<Disponibilidad>(`/disponibilidad?${qs}`)
  }

  /**
   * POST /items (HU-04, paso 3) — solo se llama DESPUÉS de que el usuario confirma.
   * Actualiza subtotal/total y el ícono del carrito con la respuesta del servidor.
   * @throws ApiError 409 si el stock cambió entre la verificación y la confirmación
   */
  async agregar(productoId: number, cantidad: number): Promise<RespuestaApi<Carrito>> {
    const r = await this.post<RespuestaApi<Carrito>>('/items', { productoId, cantidad })
    this.actualizarEstado(r.datos)
    return r
  }

  /** Limpia el reflejo local (al cerrar sesión o tras generar una orden). */
  limpiar(): void {
    this.actualizarEstado({ items: [], cantidadTotal: 0, total: 0, moneda: 'Bs.', vacio: true })
  }

  private actualizarEstado(c: Carrito): void {
    Object.assign(this.estado, c)
  }
}

export const carritoController = new CarritoClientController()
