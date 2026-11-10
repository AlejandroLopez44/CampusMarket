/**
 * =====================================================================
 * ProductoClientController — Capa CONTROL (cliente) del módulo Producto.
 * ---------------------------------------------------------------------
 * HU-06 Consultar Producto · HU-03 Agregar Producto (Administrador).
 *
 * Responsabilidad única (SRP): llamadas REST del catálogo y validación
 * previa del formulario de nuevo producto.
 * SOLID: OCP (extiende ApiClient), DIP (las vistas dependen de esta clase).
 * =====================================================================
 */
import { ApiClient, type RespuestaApi } from './ApiClient'

/** Producto del catálogo (precio en Bs.). */
export interface Producto {
  id: number
  codigo: string
  nombre: string
  descripcion: string | null
  categoria: string
  precio: number
  stock: number
  imagenUrl: string
  moneda: 'Bs.'
}

/** Formulario "Registro de nuevo producto" (HU-03). */
export interface ProductoFormulario {
  codigo: string
  nombre: string
  descripcion: string
  categoria: string
  precio: number | null
  stock: number | null
  imagenUrl: string
}

class ProductoClientController extends ApiClient {
  /** Categorías del catálogo estudiantil (restricción del Brief: solo uso estudiantil). */
  readonly categorias = ['Papelería', 'Tecnología', 'Accesorios', 'Libros', 'Arte y Diseño', 'Laboratorio'] as const

  constructor() {
    super('/api/productos')
  }

  /**
   * GET /api/productos — búsqueda con filtros opcionales (HU-06).
   * URLSearchParams codifica acentos/espacios correctamente.
   */
  listar(texto = '', categoria = ''): Promise<Producto[]> {
    const params = new URLSearchParams()
    if (texto.trim()) params.set('q', texto.trim())
    if (categoria) params.set('categoria', categoria)
    const qs = params.toString()
    return this.get<Producto[]>(qs ? `?${qs}` : '')
  }

  /** GET /api/productos/{id} — detalle con stock y precio actualizados. */
  detalle(id: number): Promise<Producto> {
    return this.get<Producto>(`/${id}`)
  }

  /** Formulario vacío con valores por defecto. */
  formularioVacio(): ProductoFormulario {
    return { codigo: '', nombre: '', descripcion: '', categoria: 'Papelería', precio: null, stock: null, imagenUrl: '' }
  }

  /**
   * Validación en cliente de las reglas HU-03:
   * precio > 0, stock ≥ 0 (entero; 0 = "Agotado"), código con formato e imagen obligatoria.
   * La unicidad del código solo puede verificarla el servidor.
   * @returns mapa campo → mensaje (vacío = válido)
   */
  validar(f: ProductoFormulario): Record<string, string> {
    const e: Record<string, string> = {}
    if (!/^[A-Za-z0-9-]{1,30}$/.test(f.codigo.trim())) e.codigo = 'Código obligatorio (letras, números o guiones, máx. 30)'
    if (!f.nombre.trim()) e.nombre = 'El nombre es obligatorio'
    if (!f.categoria) e.categoria = 'Seleccione una categoría'
    if (f.precio === null || !(Number(f.precio) > 0)) e.precio = 'El precio debe ser mayor a 0 Bs.'
    if (f.stock === null || !Number.isInteger(Number(f.stock)) || Number(f.stock) < 0) e.stock = 'El stock debe ser un número entero igual o mayor a 0'
    if (!/^https?:\/\/.+/.test(f.imagenUrl.trim())) e.imagenUrl = 'La imagen es obligatoria (URL http/https)'
    return e
  }

  /**
   * Comprueba que la URL realmente sea una imagen que el navegador puede mostrar,
   * para que no aparezcan tarjetas sin foto en el catálogo.
   * @param url dirección pegada por el administrador
   * @returns true si la imagen cargó; false si falló o tardó más de 8 s
   */
  verificarImagen(url: string): Promise<boolean> {
    return new Promise(resolve => {
      const img = new Image()
      const limite = setTimeout(() => resolve(false), 8000)
      img.onload = () => { clearTimeout(limite); resolve(img.naturalWidth > 0) }
      img.onerror = () => { clearTimeout(limite); resolve(false) }
      img.src = url.trim()
    })
  }

  /** POST /validacion — el servidor valida y verifica código duplicado SIN guardar. */
  validarEnServidor(f: ProductoFormulario): Promise<RespuestaApi<unknown>> {
    return this.post('/validacion', this.normalizar(f))
  }

  /** POST /api/productos — persiste tras la confirmación del administrador. */
  registrar(f: ProductoFormulario): Promise<RespuestaApi<Producto>> {
    return this.post('', this.normalizar(f))
  }

  /** Convierte el formulario al formato que espera el backend (números reales, textos recortados). */
  private normalizar(f: ProductoFormulario) {
    return {
      codigo: f.codigo.trim().toUpperCase(),
      nombre: f.nombre.trim(),
      descripcion: f.descripcion.trim(),
      categoria: f.categoria,
      precio: Number(f.precio),
      stock: Number(f.stock),
      imagenUrl: f.imagenUrl.trim(),
    }
  }
}

export const productoController = new ProductoClientController()
