/**
 * =====================================================================
 * CompraClientController — Capa CONTROL (cliente) del módulo Compras.
 * ---------------------------------------------------------------------
 * HU-05 Generar Compra · HU-09 Consultar Compras.
 *
 * Responsabilidad única (SRP): llamadas REST de órdenes. Tras generar una
 * orden, sincroniza el carrito (que el backend vació) para que el ícono
 * del encabezado vuelva a 0.
 *
 * Nota de desacoplamiento: el frontend NO envía correos. El backend lo hace
 * por su cuenta después de confirmar la orden en la BD.
 * =====================================================================
 */
import { ApiClient, type RespuestaApi } from './ApiClient'
import { carritoController } from './CarritoClientController'

export interface DetalleCompra {
  productoId: number
  nombre: string
  cantidad: number
  precioUnitario: number
  subtotal: number
}

/** Resumen previo a la orden (HU-05, paso 1). */
export interface ResumenCompra {
  carritoVacio: boolean
  mensaje: string
  detalles: DetalleCompra[]
  total: number
  moneda: 'Bs.'
}

/** Orden registrada / historial (HU-05, HU-09). */
export interface Compra {
  id: number
  numeroOrden: string
  fecha: string
  estado: 'PENDIENTE_PAGO' | 'PAGADA' | 'CANCELADA'
  estadoEtiqueta: string
  total: number
  moneda: 'Bs.'
  detalles: DetalleCompra[]
}

/** Factura de una orden: comprador + detalle + total (HU-05, HU-09). */
export interface Factura {
  id: number
  numeroOrden: string
  fecha: string
  estado: Compra['estado']
  estadoEtiqueta: string
  cliente: string
  correo: string
  telefono: string | null
  detalles: DetalleCompra[]
  unidades: number
  total: number
  moneda: 'Bs.'
}

class CompraClientController extends ApiClient {
  constructor() {
    super('/api/compras')
  }

  /** GET /resumen — si carritoVacio es true la vista debe detener el flujo. */
  resumen(): Promise<ResumenCompra> {
    return this.get<ResumenCompra>('/resumen')
  }

  /**
   * POST /api/compras — registra la orden "Pendiente de Pago".
   * Solo debe invocarse tras la confirmación EXPLÍCITA del usuario.
   * @returns mensaje "Orden generada - N° ..." y la orden creada
   * @throws ApiError 409 "Stock insuficiente del ..." / 400 carrito vacío
   */
  async generar(): Promise<RespuestaApi<Compra>> {
    const r = await this.post<RespuestaApi<Compra>>()
    carritoController.limpiar() // El backend ya vació el carrito en la misma transacción.
    return r
  }

  /** GET /api/compras — historial de la cuenta activa (HU-09). */
  historial(): Promise<Compra[]> {
    return this.get<Compra[]>()
  }

  /** GET /api/compras/{id}/factura — factura de una orden propia. */
  factura(id: number): Promise<Factura> {
    return this.get<Factura>(`/${id}/factura`)
  }

  /**
   * POST /api/compras/{id}/pago-simulado — marca la orden como "Pagada".
   * NO pide ni envía datos de pago: el pago real es de la 2.ª iteración.
   * @throws ApiError 409 si la orden ya no está pendiente de pago
   */
  registrarPagoSimulado(id: number): Promise<RespuestaApi<Factura>> {
    return this.post<RespuestaApi<Factura>>(`/${id}/pago-simulado`)
  }
}

export const compraController = new CompraClientController()
