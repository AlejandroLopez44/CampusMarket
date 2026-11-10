/**
 * =====================================================================
 * EstadisticasClientController — Capa CONTROL (cliente) del panel del admin.
 * ---------------------------------------------------------------------
 * Épica "Gestión de compras (Consultar)" del Administrador.
 *
 * Responsabilidad única (SRP): pedir al backend los números del negocio
 * (GET /api/admin/estadisticas) y exponer cálculos de presentación
 * (porcentajes del gráfico). La vista no conoce URLs ni hace cuentas.
 * SOLID: OCP (extiende ApiClient sin modificarlo), DIP (la vista depende de esta clase).
 * =====================================================================
 */
import { ApiClient } from './ApiClient'

export interface ProductoStockBajo { id: number; codigo: string; nombre: string; stock: number }
export interface ProductoMasVendido { productoId: number; nombre: string; unidades: number; ingresos: number }
export interface VentaDia { fecha: string; pagado: number; pendiente: number; ordenes: number }
export interface OrdenReciente {
  id: number
  numeroOrden: string
  cliente: string
  fecha: string
  estado: 'PENDIENTE_PAGO' | 'PAGADA' | 'CANCELADA'
  estadoEtiqueta: string
  total: number
}

/** Respuesta completa del panel (montos en Bs.). */
export interface Estadisticas {
  ventasPagadas: number
  ordenesPagadas: number
  montoPendiente: number
  ordenesPendientes: number
  ordenesTotales: number
  ticketPromedio: number
  productos: number
  agotados: number
  estudiantes: number
  resenas: number
  calificacionPromedio: number | null
  stockBajo: ProductoStockBajo[]
  masVendidos: ProductoMasVendido[]
  ventasUltimos7Dias: VentaDia[]
  ordenesRecientes: OrdenReciente[]
  umbralStockBajo: number
  moneda: 'Bs.'
  generadoEn: string
}

class EstadisticasClientController extends ApiClient {
  constructor() {
    super('/api/admin')
  }

  /**
   * GET /api/admin/estadisticas
   * @throws ApiError 403 si el usuario no es administrador
   */
  obtener(): Promise<Estadisticas> {
    return this.get<Estadisticas>('/estadisticas')
  }

  /**
   * Monto máximo diario (pagado + pendiente) de la semana: escala del gráfico.
   * @returns al menos 1, para no dividir entre cero cuando no hay ventas
   */
  maximoDiario(dias: VentaDia[]): number {
    return Math.max(1, ...dias.map(d => Number(d.pagado) + Number(d.pendiente)))
  }

  /** Nombre corto del día para el eje del gráfico (p. ej. "lun 10"). */
  etiquetaDia(fechaIso: string): string {
    const [a, m, d] = fechaIso.split('-').map(Number)
    return new Date(a, m - 1, d).toLocaleDateString('es-VE', { weekday: 'short', day: 'numeric' })
  }
}

export const estadisticasController = new EstadisticasClientController()
