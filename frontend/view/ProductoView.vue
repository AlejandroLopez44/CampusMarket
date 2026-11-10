<!--
  =====================================================================
  ProductoView — Capa VIEW del módulo Producto.
  ---------------------------------------------------------------------
  HU-06 Consultar Producto (catálogo + detalle)
  HU-04 Agregar a Carrito (desde el detalle)
  HU-03 Agregar Producto (formulario visible solo para ADMINISTRADOR)
  Abre ResenaView para HU-01/HU-10.

  Responsabilidad (SRP): presentar datos y capturar acciones del usuario.
  Toda llamada a la API pasa por los *ClientController (DIP): esta vista
  no conoce URLs ni fetch.
  =====================================================================
-->
<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import DialogoView from './DialogoView.vue'
import ResenaView from './ResenaView.vue'
import Icono from './Icono.vue'
import { productoController, type Producto, type ProductoFormulario } from '../control/ProductoClientController'
import { carritoController } from '../control/CarritoClientController'
import { usuarioController } from '../control/UsuarioClientController'
import { ApiError, formatearBs } from '../control/ApiClient'
import type { BotonDialogo, Destino } from '../control/NavegacionController'

/**
 * @prop textoInicial     búsqueda escrita en el inicio (opcional)
 * @prop categoriaInicial categoría elegida en el inicio (opcional)
 */
const props = defineProps<{ textoInicial?: string; categoriaInicial?: string }>()
const emit = defineEmits<{ navegar: [destino: Destino] }>()

// ------------------------------------------------------------------
// Estado de la vista
// ------------------------------------------------------------------
const productos = ref<Producto[]>([])
const cargando = ref(false)
const errorCarga = ref('')
const filtro = reactive({ texto: props.textoInicial ?? '', categoria: props.categoriaInicial ?? '' })

/** Umbral para mostrar la insignia "Últimas unidades" en las tarjetas. */
const POCAS_UNIDADES = 5

/** Suma o resta 1 a la cantidad del detalle, sin bajar de 1 ni pasar de 99. */
function cambiarCantidad(delta: number): void {
  cantidad.value = Math.min(99, Math.max(1, (Number(cantidad.value) || 1) + delta))
}

const seleccionado = ref<Producto | null>(null)   // Detalle abierto
const cantidad = ref(1)                           // Cantidad deseada (HU-04)
const procesando = ref(false)                     // Evita doble clic en acciones
const productoResenas = ref<Producto | null>(null) // Modal de reseñas
const toast = ref('')

const sesion = usuarioController.estado
const esAdmin = computed(() => sesion.usuario?.rol === 'ADMINISTRADOR')

/** Unidades de este producto que ya están en el carrito (para no superar el stock al sumar). */
const enCarrito = computed(() =>
  carritoController.estado.items.find(i => i.productoId === seleccionado.value?.id)?.cantidad ?? 0)

/**
 * Criterio 3 de "Agregar al carrito": si la cantidad escrita (más lo que ya hay en
 * el carrito) supera el inventario, el botón se BLOQUEA al instante y se muestra
 * "Cantidad no disponible. Stock actual: [N]". Igual el servidor vuelve a verificar.
 */
const excedeStock = computed(() =>
  !!seleccionado.value && Number(cantidad.value) + enCarrito.value > seleccionado.value.stock)

/** Diálogo modal genérico: la vista decide el flujo según el botón pulsado. */
interface Dialogo {
  titulo: string
  mensaje: string
  tipo: 'info' | 'exito' | 'error' | 'confirmar'
  botones: BotonDialogo[]
  alAccion: (valor: string) => void
}
const dialogo = ref<Dialogo | null>(null)

/** Abre un diálogo; al pulsar un botón se cierra y se ejecuta la acción. */
function mostrarDialogo(d: Dialogo): void {
  dialogo.value = d
}
function resolverDialogo(valor: string): void {
  const actual = dialogo.value
  dialogo.value = null
  actual?.alAccion(valor)
}

/** Mensaje breve no bloqueante que desaparece solo (feedback de éxito). */
function mostrarToast(texto: string): void {
  toast.value = texto
  setTimeout(() => (toast.value = ''), 3000)
}

// ------------------------------------------------------------------
// HU-06 Consultar Producto
// ------------------------------------------------------------------

/**
 * Carga el catálogo con los filtros actuales.
 * Muestra un error recuperable si el servidor no responde.
 */
async function cargarCatalogo(): Promise<void> {
  cargando.value = true
  errorCarga.value = ''
  try {
    productos.value = await productoController.listar(filtro.texto, filtro.categoria)
  } catch (e) {
    errorCarga.value = e instanceof ApiError ? e.message : 'No se pudo cargar el catálogo.'
  } finally {
    cargando.value = false
  }
}

/**
 * Búsqueda con "debounce" de 300 ms: no se llama a la API en cada tecla,
 * sino cuando el usuario deja de escribir (ayuda al RNF < 2 s).
 */
let temporizadorBusqueda: ReturnType<typeof setTimeout> | undefined
watch(filtro, () => {
  clearTimeout(temporizadorBusqueda)
  temporizadorBusqueda = setTimeout(cargarCatalogo, 300)
})

/** Abre el detalle pidiendo el producto al servidor (stock y precio actualizados). */
async function verDetalle(p: Producto): Promise<void> {
  try {
    seleccionado.value = await productoController.detalle(p.id)
    cantidad.value = 1
  } catch (e) {
    mostrarDialogo({ titulo: 'Producto no disponible', mensaje: (e as Error).message, tipo: 'error',
      botones: [{ texto: 'Aceptar', valor: 'ok' }], alAccion: () => cargarCatalogo() })
  }
}

// ------------------------------------------------------------------
// HU-04 Agregar a Carrito
// ------------------------------------------------------------------

/**
 * Paso 1-2 del diagrama: el usuario pulsa "Agregar al carrito" → el sistema
 * consulta disponibilidad en BD.
 *  • Disponible   → solicita CONFIRMACIÓN mostrando el subtotal en Bs.
 *  • Insuficiente → BLOQUEA la acción y muestra el stock actual ([Salir] / [OK]).
 * No exige sesión: el visitante también puede llenar su carrito (se guarda en su
 * navegador). La sesión se pide recién al generar la compra o al reseñar.
 */
async function solicitarAgregar(): Promise<void> {
  const p = seleccionado.value
  if (!p || !carritoController.habilitado) return // El administrador no tiene carrito
  if (!Number.isInteger(cantidad.value) || cantidad.value < 1) {
    mostrarToast('Indique una cantidad válida (mínimo 1).')
    return
  }
  procesando.value = true
  try {
    const d = await carritoController.verificarDisponibilidad(p.id, cantidad.value)
    if (d.disponible) {
      mostrarDialogo({
        titulo: '¿Agregar al carrito?',
        mensaje: `${d.cantidadSolicitada} × ${d.nombre}\nSubtotal: ${formatearBs(d.subtotalBs)}`,
        tipo: 'confirmar',
        botones: [{ texto: 'Cancelar', valor: 'cancelar', estilo: 'secundario' }, { texto: 'Confirmar', valor: 'confirmar' }],
        alAccion: v => v === 'confirmar' && confirmarAgregar(p.id, d.cantidadSolicitada),
      })
    } else {
      alertarStock(d.mensaje)
    }
  } catch (e) {
    mostrarDialogo({ titulo: 'No se pudo verificar el inventario', mensaje: (e as Error).message, tipo: 'error',
      botones: [{ texto: 'Aceptar', valor: 'ok' }], alAccion: () => undefined })
  } finally {
    procesando.value = false
  }
}

/**
 * Paso 3: tras la confirmación se guarda. El servidor re-verifica el stock
 * (pudo cambiar); si falla, se vuelve a la alerta de stock.
 * Al éxito (criterios 1 y 4): el ícono y el total del encabezado se actualizan al
 * instante, aparece el aviso temporal "Producto agregado al carrito exitosamente"
 * y se regresa a la Consulta de Productos (diagrama).
 */
async function confirmarAgregar(productoId: number, cant: number): Promise<void> {
  procesando.value = true
  try {
    const r = await carritoController.agregar(productoId, cant)
    seleccionado.value = null
    mostrarToast(r.mensaje)
    cargarCatalogo()
  } catch (e) {
    if (e instanceof ApiError && e.status === 409) alertarStock(e.message)
    else mostrarDialogo({ titulo: 'No se pudo agregar', mensaje: (e as Error).message, tipo: 'error',
      botones: [{ texto: 'Aceptar', valor: 'ok' }], alAccion: () => undefined })
  } finally {
    procesando.value = false
  }
}

/** Rama "Stock insuficiente": [Salir] regresa al catálogo; [OK] permite ajustar la cantidad. */
function alertarStock(mensaje: string): void {
  mostrarDialogo({ titulo: 'Stock insuficiente', mensaje, tipo: 'error',
    botones: [{ texto: 'Salir', valor: 'salir', estilo: 'secundario' }, { texto: 'OK', valor: 'ok' }],
    alAccion: v => { if (v === 'salir') { seleccionado.value = null; cargarCatalogo() } } })
}

// ------------------------------------------------------------------
// HU-03 Agregar Producto (Administrador)
// ------------------------------------------------------------------
const formularioAbierto = ref(false)
const form = ref<ProductoFormulario>(productoController.formularioVacio())
const erroresForm = ref<Record<string, string>>({})

/** Muestra el formulario de registro de nuevo producto. */
function abrirFormulario(): void {
  form.value = productoController.formularioVacio()
  erroresForm.value = {}
  formularioAbierto.value = true
}

/** [Cancelar] en el formulario: sale sin guardar. */
function cancelarFormulario(): void {
  formularioAbierto.value = false
}

/**
 * [Guardar] en el formulario. Flujo del diagrama:
 *  1) Valida datos (cliente) → [datos inválidos] mensaje → [OK] vuelve al formulario / [Cancelar] sale.
 *     Incluye comprobar que la URL de la imagen realmente cargue una imagen.
 *  2) Verifica en servidor que el código NO exista → [existe] "Producto duplicado" → sale o corrige.
 *  3) [datos válidos y no existe] → solicita confirmación antes de persistir.
 */
async function enviarFormulario(): Promise<void> {
  erroresForm.value = productoController.validar(form.value)
  if (Object.keys(erroresForm.value).length > 0) {
    mostrarDatosInvalidos()
    return
  }
  procesando.value = true
  try {
    // La URL tiene formato válido; ahora se comprueba que de verdad sea una imagen.
    if (!(await productoController.verificarImagen(form.value.imagenUrl))) {
      erroresForm.value = { imagenUrl: 'No se pudo cargar una imagen desde esa URL. Copie la dirección de la imagen (clic derecho → "Copiar dirección de imagen").' }
      mostrarDatosInvalidos()
      return
    }
    await productoController.validarEnServidor(form.value)
    mostrarDialogo({
      titulo: '¿Registrar producto?',
      mensaje: `${form.value.codigo.toUpperCase()} · ${form.value.nombre}\nPrecio: ${formatearBs(Number(form.value.precio))} · Stock: ${form.value.stock}`,
      tipo: 'confirmar',
      botones: [{ texto: 'Volver', valor: 'volver', estilo: 'secundario' }, { texto: 'Confirmar', valor: 'confirmar' }],
      alAccion: v => v === 'confirmar' && guardarProducto(),
    })
  } catch (e) {
    manejarErrorProducto(e)
  } finally {
    procesando.value = false
  }
}

/** Rama "[datos inválidos]": muestra los errores; [OK] vuelve al formulario, [Cancelar] sale. */
function mostrarDatosInvalidos(): void {
  mostrarDialogo({ titulo: 'Datos inválidos', mensaje: Object.values(erroresForm.value).join('\n'), tipo: 'error',
    botones: [{ texto: 'Cancelar', valor: 'cancelar', estilo: 'secundario' }, { texto: 'OK', valor: 'ok' }],
    alAccion: v => v === 'cancelar' && cancelarFormulario() })
}

/** Persiste tras la confirmación → mensaje de éxito → el admin confirma lectura → fin. */
async function guardarProducto(): Promise<void> {
  procesando.value = true
  try {
    const r = await productoController.registrar(form.value)
    mostrarDialogo({ titulo: r.mensaje, mensaje: 'El producto ya está disponible en el catálogo.', tipo: 'exito',
      botones: [{ texto: 'Confirmar lectura', valor: 'ok' }],
      alAccion: () => { formularioAbierto.value = false; cargarCatalogo() } })
  } catch (e) {
    manejarErrorProducto(e)
  } finally {
    procesando.value = false
  }
}

/** Traduce errores del servidor: 400 → errores por campo; 409 → alerta de "Producto duplicado" (criterio 1). */
function manejarErrorProducto(e: unknown): void {
  if (e instanceof ApiError && e.status === 409) {
    erroresForm.value = { codigo: e.message }
    mostrarDialogo({ titulo: 'Producto duplicado', mensaje: e.message, tipo: 'error',
      botones: [{ texto: 'Salir', valor: 'salir', estilo: 'secundario' }, { texto: 'Corregir código', valor: 'corregir' }],
      alAccion: v => v === 'salir' && cancelarFormulario() })
    return
  }
  if (e instanceof ApiError && Object.keys(e.errores).length > 0) erroresForm.value = e.errores
  mostrarDialogo({ titulo: 'No se pudo registrar', mensaje: (e as Error).message, tipo: 'error',
    botones: [{ texto: 'OK', valor: 'ok' }], alAccion: () => undefined })
}

onMounted(cargarCatalogo)

/** Clases comunes de los campos del formulario (marcan en rojo si hay error). */
const claseCampo = (campo: string) => ['campo', erroresForm.value[campo] ? 'campo-error' : '']
</script>

<template>
  <section>
    <!-- ===== Encabezado del catálogo ===== -->
    <div class="mb-6 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
      <div>
        <p class="eyebrow">{{ esAdmin ? 'Gestión del catálogo' : 'Catálogo' }}</p>
        <h1 class="mt-1 text-3xl font-extrabold">{{ esAdmin ? 'Productos en venta' : 'Encuentra lo que necesitas' }}</h1>
        <p class="mt-1 text-slate-500">Precios en bolívares (Bs.) · Stock actualizado en tiempo real</p>
      </div>
      <button v-if="esAdmin" class="btn-primario" @click="abrirFormulario">
        <Icono nombre="mas" :tamano="18" /> Agregar producto
      </button>
    </div>

    <!-- ===== Filtros de búsqueda (HU-06) ===== -->
    <div class="tarjeta mb-6 space-y-3 p-3 sm:p-4">
      <div class="relative">
        <span class="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400"><Icono nombre="buscar" /></span>
        <input v-model="filtro.texto" type="search" placeholder="Buscar por nombre…" aria-label="Buscar producto" class="campo !py-3 !pl-11" />
      </div>
      <!-- Categorías como chips: se ven todas de un vistazo y se eligen con un toque -->
      <div class="-mx-1 flex gap-2 overflow-x-auto px-1 pb-1" role="radiogroup" aria-label="Filtrar por categoría">
        <button v-for="c in ['', ...productoController.categorias]" :key="c || 'todas'" role="radio" :aria-checked="filtro.categoria === c"
                :class="['shrink-0 rounded-full px-4 py-1.5 text-sm font-semibold transition',
                         filtro.categoria === c ? 'bg-marca-800 text-white shadow-suave' : 'bg-slate-100 text-slate-600 hover:bg-slate-200']"
                @click="filtro.categoria = c">{{ c || 'Todas' }}</button>
      </div>
    </div>

    <!-- Estado de carga: tarjetas "esqueleto" que anticipan la forma del contenido -->
    <div v-if="cargando" class="grid grid-cols-2 gap-3 sm:gap-5 lg:grid-cols-4" aria-label="Cargando catálogo">
      <div v-for="n in 8" :key="n" class="tarjeta overflow-hidden">
        <div class="aspect-[4/3] animate-pulse bg-slate-200" />
        <div class="space-y-2 p-4"><div class="h-3 w-1/3 animate-pulse rounded bg-slate-200" /><div class="h-4 w-3/4 animate-pulse rounded bg-slate-200" /><div class="h-5 w-1/2 animate-pulse rounded bg-slate-200" /></div>
      </div>
    </div>
    <div v-else-if="errorCarga" class="tarjeta flex flex-col items-center gap-3 p-8 text-center">
      <span class="flex h-12 w-12 items-center justify-center rounded-full bg-red-50 text-red-600"><Icono nombre="alerta" :tamano="24" /></span>
      <p class="font-semibold text-red-700">{{ errorCarga }}</p>
      <button class="btn-secundario" @click="cargarCatalogo"><Icono nombre="actualizar" :tamano="16" /> Reintentar</button>
    </div>
    <div v-else-if="productos.length === 0" class="tarjeta flex flex-col items-center gap-3 p-10 text-center">
      <span class="flex h-14 w-14 items-center justify-center rounded-full bg-marca-50 text-marca-600"><Icono nombre="buscar" :tamano="26" /></span>
      <p class="text-lg font-bold">No encontramos productos</p>
      <p class="text-slate-500">Prueba con otra palabra o elige otra categoría.</p>
      <button class="btn-secundario" @click="filtro.texto = ''; filtro.categoria = ''">Ver todo el catálogo</button>
    </div>

    <template v-else>
      <p class="mb-3 text-sm text-slate-500">{{ productos.length }} producto(s){{ filtro.categoria ? ` en ${filtro.categoria}` : '' }}</p>
      <!-- Grilla responsiva: 2 columnas en móvil, hasta 4 en escritorio -->
      <div class="grid grid-cols-2 gap-3 sm:gap-5 lg:grid-cols-4">
        <article v-for="p in productos" :key="p.id" class="tarjeta group flex flex-col overflow-hidden transition hover:-translate-y-1 hover:shadow-elevada">
          <button class="relative block aspect-[4/3] overflow-hidden bg-slate-100" :aria-label="`Ver detalle de ${p.nombre}`" @click="verDetalle(p)">
            <img :src="p.imagenUrl" :alt="p.nombre" loading="lazy" :class="['h-full w-full object-cover transition duration-300 group-hover:scale-105', p.stock === 0 ? 'grayscale' : '']" />
            <!-- Insignias de inventario -->
            <span v-if="p.stock === 0" class="insignia absolute left-2 top-2 bg-tinta/85 text-white">Agotado</span>
            <span v-else-if="p.stock <= POCAS_UNIDADES" class="insignia absolute left-2 top-2 bg-acento-400 text-tinta">¡Últimas {{ p.stock }}!</span>
          </button>
          <div class="flex flex-1 flex-col p-3 sm:p-4">
            <span class="text-[11px] font-bold uppercase tracking-wider text-marca-600">{{ p.categoria }}</span>
            <h2 class="mt-0.5 font-sans text-sm font-bold leading-snug sm:text-base">{{ p.nombre }}</h2>
            <!-- Descripción breve (máx. 2 líneas) en la tarjeta: criterio 2 de "Agregar producto" -->
            <p v-if="p.descripcion" class="mt-1 line-clamp-2 text-xs text-slate-500 sm:text-sm">{{ p.descripcion }}</p>
            <div class="mt-auto flex items-end justify-between gap-2 pt-3">
              <p class="font-titulo text-lg font-bold text-marca-800">{{ formatearBs(p.precio) }}</p>
              <p :class="['text-xs font-semibold', p.stock > 0 ? 'text-slate-500' : 'text-red-600']">{{ p.stock > 0 ? `Stock: ${p.stock}` : 'Sin stock' }}</p>
            </div>
            <div class="mt-3 grid grid-cols-[1fr_auto] gap-2">
              <button class="btn-primario !px-3 !py-2" @click="verDetalle(p)">Ver detalle</button>
              <button class="btn-secundario !px-3 !py-2" :aria-label="`Reseñas de ${p.nombre}`" title="Reseñas" @click="productoResenas = p">
                <Icono nombre="estrella" :tamano="18" /><span class="hidden sm:inline">Reseñas</span>
              </button>
            </div>
          </div>
        </article>
      </div>
    </template>

    <!-- ===== Detalle del producto + Agregar a carrito (HU-06 / HU-04) ===== -->
    <div v-if="seleccionado" class="animar-fondo fixed inset-0 z-40 flex items-end justify-center bg-tinta/60 p-0 backdrop-blur-sm sm:items-center sm:p-4" @click.self="seleccionado = null">
      <div class="animar-entrada max-h-[92vh] w-full max-w-3xl overflow-y-auto rounded-t-3xl bg-white shadow-elevada sm:rounded-3xl" role="dialog" aria-modal="true" :aria-label="seleccionado.nombre">
        <div class="grid sm:grid-cols-2">
          <div class="relative bg-slate-100">
            <img :src="seleccionado.imagenUrl" :alt="seleccionado.nombre" class="h-64 w-full object-cover sm:h-full" />
            <button class="absolute right-3 top-3 rounded-full bg-white/90 p-2 shadow-suave hover:bg-white sm:hidden" aria-label="Cerrar" @click="seleccionado = null"><Icono nombre="cerrar" /></button>
          </div>
          <div class="flex flex-col gap-3 p-6">
            <div class="flex items-start justify-between gap-3">
              <span class="eyebrow">{{ seleccionado.categoria }} · {{ seleccionado.codigo }}</span>
              <button class="hidden rounded-full p-1.5 text-slate-400 hover:bg-slate-100 hover:text-tinta sm:block" aria-label="Cerrar" @click="seleccionado = null"><Icono nombre="cerrar" /></button>
            </div>
            <h2 class="text-2xl font-extrabold">{{ seleccionado.nombre }}</h2>
            <p class="text-slate-600">{{ seleccionado.descripcion || 'Sin descripción.' }}</p>
            <div class="flex items-center justify-between gap-3 rounded-2xl bg-slate-50 p-4">
              <p class="font-titulo text-3xl font-extrabold text-marca-800">{{ formatearBs(seleccionado.precio) }}</p>
              <span :class="['insignia', seleccionado.stock === 0 ? 'bg-red-100 text-red-700' : seleccionado.stock <= POCAS_UNIDADES ? 'bg-acento-100 text-amber-800' : 'bg-emerald-100 text-emerald-800']">
                {{ seleccionado.stock === 0 ? 'Agotado' : `${seleccionado.stock} disponibles` }}
              </span>
            </div>

            <!-- Visitante y estudiante: elegir cantidad y agregar al carrito (HU-04) -->
            <template v-if="!esAdmin">
              <label class="etiqueta !mb-0" for="cantidad">Cantidad</label>
              <div class="flex items-center gap-3">
                <!-- Selector −/+ : más cómodo que escribir, sobre todo en el celular -->
                <div :class="['flex items-center rounded-xl border', excedeStock ? 'border-red-500' : 'border-slate-300']">
                  <button class="p-2.5 text-slate-600 hover:text-tinta disabled:opacity-40" :disabled="cantidad <= 1" aria-label="Restar uno" @click="cambiarCantidad(-1)"><Icono nombre="menos" :tamano="18" /></button>
                  <input id="cantidad" v-model.number="cantidad" type="number" min="1" max="99" inputmode="numeric"
                         class="w-12 border-0 bg-transparent text-center font-bold outline-none [appearance:textfield] [&::-webkit-inner-spin-button]:appearance-none" />
                  <button class="p-2.5 text-slate-600 hover:text-tinta disabled:opacity-40" :disabled="cantidad >= 99" aria-label="Sumar uno" @click="cambiarCantidad(1)"><Icono nombre="mas" :tamano="18" /></button>
                </div>
                <p v-if="!excedeStock" class="text-sm text-slate-500">Subtotal: <strong class="text-tinta">{{ formatearBs(seleccionado.precio * (cantidad || 0)) }}</strong></p>
              </div>
              <!-- Aviso instantáneo del criterio 3 (el botón queda bloqueado mientras se muestre) -->
              <p v-if="excedeStock" class="flex items-start gap-2 rounded-xl bg-red-50 p-3 text-sm font-semibold text-red-700" role="alert">
                <Icono nombre="alerta" :tamano="18" />
                <span>Cantidad no disponible. Stock actual: {{ seleccionado.stock }}
                  <span v-if="enCarrito > 0" class="block font-normal">Ya tienes {{ enCarrito }} en tu carrito.</span></span>
              </p>
            </template>
            <!-- Administrador: vista de inventario (no compra) -->
            <p v-else :class="['flex items-center gap-2 rounded-xl p-3 text-sm font-medium', seleccionado.stock <= 5 ? 'bg-acento-100 text-amber-800' : 'bg-emerald-50 text-emerald-800']">
              <Icono :nombre="seleccionado.stock <= 5 ? 'alerta' : 'check'" :tamano="18" />
              {{ seleccionado.stock === 0 ? 'Producto agotado: conviene reponer inventario.'
                 : seleccionado.stock <= 5 ? 'Stock bajo: quedan pocas unidades.' : 'Inventario suficiente.' }}
            </p>

            <div class="mt-auto flex flex-col-reverse gap-2 pt-2 sm:flex-row">
              <button class="btn-secundario" @click="seleccionado = null"><Icono nombre="izquierda" :tamano="16" /> Volver</button>
              <button class="btn-secundario" @click="productoResenas = seleccionado"><Icono nombre="estrella" :tamano="16" /> Reseñas</button>
              <button v-if="!esAdmin" :disabled="procesando || seleccionado.stock === 0 || excedeStock" class="btn-primario flex-1" @click="solicitarAgregar">
                <Icono nombre="carrito" :tamano="18" /> Agregar al carrito
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- ===== Formulario de registro de nuevo producto (HU-03, solo admin) ===== -->
    <div v-if="formularioAbierto" class="animar-fondo fixed inset-0 z-40 flex items-end justify-center bg-tinta/60 backdrop-blur-sm sm:items-center sm:p-4">
      <form class="animar-entrada max-h-[92vh] w-full max-w-2xl overflow-y-auto rounded-t-3xl bg-white p-6 shadow-elevada sm:rounded-3xl sm:p-8" novalidate @submit.prevent="enviarFormulario">
        <div class="mb-6 flex items-start justify-between gap-3">
          <div>
            <p class="eyebrow">Administrador</p>
            <h2 class="mt-1 text-2xl font-extrabold">Registro de nuevo producto</h2>
            <p class="text-sm text-slate-500">Los campos con * son obligatorios.</p>
          </div>
          <button type="button" class="rounded-full p-1.5 text-slate-400 hover:bg-slate-100 hover:text-tinta" aria-label="Cerrar" @click="cancelarFormulario"><Icono nombre="cerrar" /></button>
        </div>
        <div class="grid gap-4 sm:grid-cols-2">
          <div>
            <label class="etiqueta" for="f-codigo">Código *</label>
            <input id="f-codigo" v-model="form.codigo" :class="claseCampo('codigo')" placeholder="PAP-010" />
            <p v-if="erroresForm.codigo" class="error-texto">{{ erroresForm.codigo }}</p>
          </div>
          <div>
            <label class="etiqueta" for="f-categoria">Categoría *</label>
            <select id="f-categoria" v-model="form.categoria" :class="claseCampo('categoria')">
              <option v-for="c in productoController.categorias" :key="c" :value="c">{{ c }}</option>
            </select>
          </div>
          <div class="sm:col-span-2">
            <label class="etiqueta" for="f-nombre">Nombre *</label>
            <input id="f-nombre" v-model="form.nombre" :class="claseCampo('nombre')" placeholder="Ej. Resaltador amarillo" />
            <p v-if="erroresForm.nombre" class="error-texto">{{ erroresForm.nombre }}</p>
          </div>
          <div class="sm:col-span-2">
            <label class="etiqueta" for="f-desc">Descripción breve</label>
            <textarea id="f-desc" v-model="form.descripcion" rows="2" maxlength="500" :class="claseCampo('descripcion')" placeholder="Se muestra en la tarjeta del catálogo" />
          </div>
          <div>
            <label class="etiqueta" for="f-precio">Precio (Bs.) *</label>
            <input id="f-precio" v-model.number="form.precio" type="number" min="0.01" step="0.01" :class="claseCampo('precio')" />
            <p v-if="erroresForm.precio" class="error-texto">{{ erroresForm.precio }}</p>
          </div>
          <div>
            <label class="etiqueta" for="f-stock">Stock *</label>
            <input id="f-stock" v-model.number="form.stock" type="number" min="0" step="1" :class="claseCampo('stock')" />
            <p v-if="erroresForm.stock" class="error-texto">{{ erroresForm.stock }}</p>
            <p v-else class="ayuda">Con 0 se publica como "Agotado".</p>
          </div>
          <div class="sm:col-span-2">
            <label class="etiqueta" for="f-img">URL de la imagen *</label>
            <input id="f-img" v-model="form.imagenUrl" type="url" placeholder="https://…" :class="claseCampo('imagenUrl')" />
            <p v-if="erroresForm.imagenUrl" class="error-texto">{{ erroresForm.imagenUrl }}</p>
            <p v-else class="ayuda">Clic derecho sobre una imagen en internet → "Copiar dirección de imagen".</p>
            <div v-if="/^https?:\/\/.+/.test(form.imagenUrl)" class="mt-3 flex items-center gap-3 rounded-xl bg-slate-50 p-2">
              <img :src="form.imagenUrl" alt="Vista previa" class="h-20 w-20 rounded-lg object-cover" />
              <span class="text-xs text-slate-500">Vista previa de cómo se verá en el catálogo.</span>
            </div>
          </div>
        </div>
        <div class="mt-8 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <button type="button" class="btn-secundario" @click="cancelarFormulario">Cancelar</button>
          <button type="submit" :disabled="procesando" class="btn-primario"><Icono nombre="check" :tamano="18" /> Guardar</button>
        </div>
      </form>
    </div>

    <ResenaView v-if="productoResenas" :producto-id="productoResenas.id" :producto-nombre="productoResenas.nombre" @cerrar="productoResenas = null" />

    <DialogoView v-if="dialogo" :titulo="dialogo.titulo" :mensaje="dialogo.mensaje" :tipo="dialogo.tipo" :botones="dialogo.botones" @accion="resolverDialogo" />

    <!-- Aviso temporal (desaparece a los 3 s); role="status" lo anuncia a lectores de pantalla -->
    <div v-if="toast" role="status" class="animar-entrada fixed inset-x-4 bottom-4 z-50 mx-auto flex max-w-sm items-center gap-3 rounded-2xl bg-tinta px-4 py-3 font-semibold text-white shadow-elevada sm:left-auto sm:right-6 sm:mx-0">
      <span class="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-emerald-500"><Icono nombre="check" :tamano="16" :grosor="2.5" /></span>
      {{ toast }}
    </div>
  </section>
</template>
