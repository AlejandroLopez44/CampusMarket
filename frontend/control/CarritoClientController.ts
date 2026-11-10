/**
 * =====================================================================
 * CarritoClientController — Capa CONTROL (cliente) del módulo Carrito.
 * ---------------------------------------------------------------------
 * HU-04 Agregar a Carrito · HU-08 Consultar Carrito.
 *
 * Responsabilidad única (SRP): operaciones del carrito y el estado reactivo
 * que alimenta el ícono/badge del encabezado.
 *
 * Tres modos, según quién usa la página:
 *   • 'invitado'      → visitante sin sesión: los ítems se guardan en el
 *                       navegador (localStorage); stock y precios se piden al servidor.
 *   • 'estudiante'    → con sesión: el carrito se guarda en la base de datos.
 *   • 'deshabilitado' → administrador: no tiene carrito.
 *
 * Patrón Strategy + SOLID:
 *   • OCP/DIP: el controlador trabaja contra la interfaz AlmacenCarrito y no
 *     sabe DÓNDE se guardan los ítems. Cambiar de modo = cambiar de estrategia.
 *     Un tercer almacén (p. ej. sincronizado entre pestañas) no tocaría las vistas.
 *   • LSP: AlmacenInvitado y AlmacenServidor son intercambiables: ambos
 *     devuelven los mismos tipos (Carrito, Disponibilidad).
 * =====================================================================
 */
import { reactive } from 'vue'
import { ApiClient, ApiError, type RespuestaApi } from './ApiClient'

export interface ItemCarrito {
  productoId: number
  codigo: string
  nombre: string
  imagenUrl: string
  precioUnitario: number
  cantidad: number
  subtotal: number
  stockActual: number
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

export type ModoCarrito = 'invitado' | 'estudiante' | 'deshabilitado'

/** Ítem mínimo que se guarda en el navegador: solo id y cantidad (nunca precios). */
interface ItemLocal {
  productoId: number
  cantidad: number
}

/** Funciones HTTP que el controlador presta a las estrategias (inyección de dependencias). */
interface Http {
  get<T>(ruta: string): Promise<T>
  post<T>(ruta: string, cuerpo?: unknown): Promise<T>
  eliminar<T>(ruta: string): Promise<T>
}

/** Contrato común de los almacenes de carrito (abstracción de la que depende el controlador). */
interface AlmacenCarrito {
  consultar(): Promise<Carrito>
  verificar(productoId: number, cantidad: number): Promise<Disponibilidad>
  agregar(productoId: number, cantidad: number): Promise<RespuestaApi<Carrito>>
  vaciar(): Promise<void>
}

const CARRITO_VACIO: Carrito = { items: [], cantidadTotal: 0, total: 0, moneda: 'Bs.', vacio: true }

// ---------------------------------------------------------------------
// Estrategia 1: carrito guardado en la base de datos (estudiante)
// ---------------------------------------------------------------------
class AlmacenServidor implements AlmacenCarrito {
  constructor(private readonly http: Http) {}

  /** GET /api/carrito */
  consultar(): Promise<Carrito> {
    return this.http.get<Carrito>('')
  }

  /** GET /api/carrito/disponibilidad (el servidor lee lo que ya hay en el carrito guardado). */
  verificar(productoId: number, cantidad: number): Promise<Disponibilidad> {
    const qs = new URLSearchParams({ productoId: String(productoId), cantidad: String(cantidad) })
    return this.http.get<Disponibilidad>(`/disponibilidad?${qs}`)
  }

  /** POST /api/carrito/items: el servidor re-verifica el stock y guarda. */
  agregar(productoId: number, cantidad: number): Promise<RespuestaApi<Carrito>> {
    return this.http.post<RespuestaApi<Carrito>>('/items', { productoId, cantidad })
  }

  /** DELETE /api/carrito: vacía el carrito guardado en la BD. */
  async vaciar(): Promise<void> {
    await this.http.eliminar('')
  }
}

// ---------------------------------------------------------------------
// Estrategia 2: carrito en el navegador (visitante sin sesión)
// ---------------------------------------------------------------------
class AlmacenInvitado implements AlmacenCarrito {
  /** Clave de localStorage donde vive el carrito del visitante. */
  static readonly CLAVE = 'campusmarket.carritoInvitado'

  constructor(private readonly http: Http) {}

  /** Lee los ítems del navegador. Si el almacenamiento falla o está corrupto, devuelve lista vacía. */
  leer(): ItemLocal[] {
    try {
      const datos = JSON.parse(localStorage.getItem(AlmacenInvitado.CLAVE) ?? '[]')
      return Array.isArray(datos)
        ? datos.filter((i): i is ItemLocal => Number.isInteger(i?.productoId) && Number.isInteger(i?.cantidad) && i.cantidad > 0)
        : []
    } catch {
      return []
    }
  }

  /** Guarda los ítems en el navegador (si el navegador lo bloquea, el carrito solo dura esta visita). */
  escribir(items: ItemLocal[]): void {
    try {
      localStorage.setItem(AlmacenInvitado.CLAVE, JSON.stringify(items))
    } catch {
      /* Navegación privada o almacenamiento bloqueado: se ignora sin romper la página. */
    }
  }

  /** Borra el carrito del navegador (tras unirlo al de la cuenta, o cuando el usuario lo vacía). */
  async vaciar(): Promise<void> {
    this.escribir([])
  }

  /**
   * POST /api/carrito/cotizacion: el servidor calcula precios y totales reales.
   * Además limpia del navegador los productos que ya no existen en el catálogo.
   */
  async consultar(): Promise<Carrito> {
    const items = this.leer()
    if (items.length === 0) return { ...CARRITO_VACIO }
    const carrito = await this.http.post<Carrito>('/cotizacion', { items })
    const existentes = new Set(carrito.items.map(i => i.productoId))
    this.escribir(items.filter(i => existentes.has(i.productoId)))
    return carrito
  }

  /** GET /api/carrito/disponibilidad enviando cuántas unidades ya hay en el navegador. */
  verificar(productoId: number, cantidad: number): Promise<Disponibilidad> {
    const enCarrito = this.leer().find(i => i.productoId === productoId)?.cantidad ?? 0
    const qs = new URLSearchParams({ productoId: String(productoId), cantidad: String(cantidad), enCarrito: String(enCarrito) })
    return this.http.get<Disponibilidad>(`/disponibilidad?${qs}`)
  }

  /**
   * Re-verifica el stock (como hace el servidor para el estudiante) y, si
   * alcanza, guarda en el navegador. Si no alcanza, lanza el mismo 409.
   */
  async agregar(productoId: number, cantidad: number): Promise<RespuestaApi<Carrito>> {
    const d = await this.verificar(productoId, cantidad)
    if (!d.disponible) throw new ApiError(409, d.mensaje, {}, { stockActual: d.stockActual })
    const items = this.leer()
    const existente = items.find(i => i.productoId === productoId)
    if (existente) existente.cantidad += cantidad
    else items.push({ productoId, cantidad })
    this.escribir(items)
    return { mensaje: 'Producto agregado al carrito exitosamente', datos: await this.consultar() }
  }
}

// ---------------------------------------------------------------------
// Controlador público
// ---------------------------------------------------------------------
class CarritoClientController extends ApiClient {
  /** Estado compartido: el encabezado muestra cantidadTotal y la vista del carrito los ítems. */
  readonly estado = reactive<Carrito & { modo: ModoCarrito }>({ ...CARRITO_VACIO, modo: 'invitado' })

  private readonly invitado: AlmacenInvitado
  private readonly servidor: AlmacenServidor
  private almacen: AlmacenCarrito

  constructor() {
    super('/api/carrito')
    // Se prestan los métodos HTTP protegidos a las estrategias (inyección por constructor).
    const http: Http = { get: r => this.get(r), post: (r, c) => this.post(r, c), eliminar: r => this.eliminar(r) }
    this.invitado = new AlmacenInvitado(http)
    this.servidor = new AlmacenServidor(http)
    this.almacen = this.invitado
  }

  /** @returns true si el carrito está disponible (visitante o estudiante). */
  get habilitado(): boolean {
    return this.estado.modo !== 'deshabilitado'
  }

  /** @returns true si es el carrito del navegador (visitante). */
  get esInvitado(): boolean {
    return this.estado.modo === 'invitado'
  }

  /**
   * Cambia de estrategia según el rol y refresca el ícono.
   * @param modo 'invitado' (sin sesión), 'estudiante' o 'deshabilitado' (admin)
   */
  async usarModo(modo: ModoCarrito): Promise<void> {
    this.estado.modo = modo
    if (modo === 'deshabilitado') {
      this.actualizarEstado(CARRITO_VACIO)
      return
    }
    this.almacen = modo === 'estudiante' ? this.servidor : this.invitado
    await this.consultar().catch(() => this.actualizarEstado(CARRITO_VACIO))
  }

  /** HU-08: consulta el carrito del modo actual y actualiza el estado. */
  async consultar(): Promise<Carrito> {
    if (!this.habilitado) return { ...CARRITO_VACIO }
    const c = await this.almacen.consultar()
    this.actualizarEstado(c)
    return c
  }

  /** HU-04 paso 2: verifica el inventario. No modifica nada. */
  verificarDisponibilidad(productoId: number, cantidad: number): Promise<Disponibilidad> {
    return this.almacen.verificar(productoId, cantidad)
  }

  /**
   * HU-04 paso 3 (tras la confirmación del usuario): agrega y actualiza el ícono.
   * @throws ApiError 409 si el stock cambió entre la verificación y la confirmación
   */
  async agregar(productoId: number, cantidad: number): Promise<RespuestaApi<Carrito>> {
    const r = await this.almacen.agregar(productoId, cantidad)
    this.actualizarEstado(r.datos)
    return r
  }

  /**
   * Al iniciar sesión como estudiante: une el carrito del navegador con el
   * de la cuenta (POST /fusion) y borra el del navegador.
   * @returns mensaje del servidor con los ajustes, o null si no había nada que unir
   */
  async fusionarCarritoInvitado(): Promise<string | null> {
    const items = this.invitado.leer()
    if (items.length === 0) return null
    const r = await this.post<RespuestaApi<Carrito>>('/fusion', { items })
    this.invitado.vaciar()
    this.actualizarEstado(r.datos)
    return r.mensaje
  }

  /**
   * Vacía el carrito del modo actual (navegador o BD) y pone el ícono en 0.
   * Salida para no quedar atrapado si un producto se quedó sin stock al comprar.
   */
  async vaciar(): Promise<void> {
    if (!this.habilitado) return
    await this.almacen.vaciar()
    this.actualizarEstado(CARRITO_VACIO)
  }

  /** Tras generar una orden, el backend vacía el carrito guardado: se refleja aquí. */
  limpiar(): void {
    this.actualizarEstado(CARRITO_VACIO)
  }

  private actualizarEstado(c: Carrito): void {
    Object.assign(this.estado, { items: c.items, cantidadTotal: c.cantidadTotal, total: c.total, moneda: c.moneda, vacio: c.vacio })
  }
}

export const carritoController = new CarritoClientController()
