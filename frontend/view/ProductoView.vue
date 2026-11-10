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
import { productoController, type Producto, type ProductoFormulario } from '../control/ProductoClientController'
import { carritoController } from '../control/CarritoClientController'
import { usuarioController } from '../control/UsuarioClientController'
import { ApiError, formatearBs } from '../control/ApiClient'
import type { BotonDialogo, Destino } from '../control/NavegacionController'

const emit = defineEmits<{ navegar: [destino: Destino] }>()

// ------------------------------------------------------------------
// Estado de la vista
// ------------------------------------------------------------------
const productos = ref<Producto[]>([])
const cargando = ref(false)
const errorCarga = ref('')
const filtro = reactive({ texto: '', categoria: '' })

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
const claseCampo = (campo: string) =>
  ['w-full rounded-lg border px-3 py-2 outline-none focus:ring-2 focus:ring-marca-500',
   erroresForm.value[campo] ? 'border-red-500' : 'border-slate-300']
</script>

<template>
  <section>
    <div class="mb-4 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
      <div>
        <h1 class="text-2xl font-bold">Catálogo de Productos</h1>
        <p class="text-slate-500">Precios expresados en bolívares (Bs.)</p>
      </div>
      <button v-if="esAdmin" class="rounded-lg bg-marca-600 px-4 py-2 font-semibold text-white hover:bg-marca-700" @click="abrirFormulario">
        + Agregar producto
      </button>
    </div>

    <!-- Filtros de búsqueda (HU-06) -->
    <div class="mb-6 flex flex-col gap-3 sm:flex-row">
      <input v-model="filtro.texto" type="search" placeholder="Buscar por nombre…" aria-label="Buscar producto"
             class="flex-1 rounded-lg border border-slate-300 px-3 py-2 outline-none focus:ring-2 focus:ring-marca-500" />
      <select v-model="filtro.categoria" aria-label="Filtrar por categoría" class="rounded-lg border border-slate-300 px-3 py-2">
        <option value="">Todas las categorías</option>
        <option v-for="c in productoController.categorias" :key="c" :value="c">{{ c }}</option>
      </select>
    </div>

    <p v-if="cargando" class="py-10 text-center text-slate-500">Cargando catálogo…</p>
    <div v-else-if="errorCarga" class="rounded-xl bg-red-50 p-6 text-center text-red-700">
      {{ errorCarga }}
      <button class="ml-2 font-semibold underline" @click="cargarCatalogo">Reintentar</button>
    </div>
    <p v-else-if="productos.length === 0" class="py-10 text-center text-slate-500">No se encontraron productos.</p>

    <!-- Grilla responsiva: 1 columna en móvil, hasta 4 en escritorio -->
    <div v-else class="grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-4">
      <article v-for="p in productos" :key="p.id" class="flex flex-col overflow-hidden rounded-2xl bg-white shadow-sm transition hover:-translate-y-1 hover:shadow-md">
        <img :src="p.imagenUrl" :alt="p.nombre" loading="lazy" class="h-44 w-full bg-slate-200 object-cover" />
        <div class="flex flex-1 flex-col p-4">
          <span class="text-xs font-bold uppercase text-marca-600">{{ p.categoria }}</span>
          <h2 class="font-semibold">{{ p.nombre }}</h2>
          <!-- Descripción breve (máx. 2 líneas) en la tarjeta: criterio 2 de "Agregar producto" -->
          <p v-if="p.descripcion" class="line-clamp-2 text-sm text-slate-500">{{ p.descripcion }}</p>
          <p class="mt-auto pt-2 text-lg font-bold text-emerald-600">{{ formatearBs(p.precio) }}</p>
          <p :class="['text-sm', p.stock > 0 ? 'text-slate-500' : 'font-semibold text-red-600']">
            {{ p.stock > 0 ? `Stock: ${p.stock}` : 'Agotado' }}
          </p>
          <div class="mt-3 flex gap-2">
            <button class="flex-1 rounded-lg bg-marca-600 px-3 py-2 text-sm font-semibold text-white hover:bg-marca-700" @click="verDetalle(p)">Ver detalle</button>
            <button class="flex-1 rounded-lg bg-slate-200 px-3 py-2 text-sm font-semibold hover:bg-slate-300" @click="productoResenas = p">Reseñas</button>
          </div>
        </div>
      </article>
    </div>

    <!-- ===== Detalle del producto + Agregar a carrito (HU-06 / HU-04) ===== -->
    <div v-if="seleccionado" class="fixed inset-0 z-40 flex items-end justify-center bg-black/50 p-4 sm:items-center" @click.self="seleccionado = null">
      <div class="max-h-[90vh] w-full max-w-2xl overflow-y-auto rounded-2xl bg-white shadow-xl">
        <div class="grid sm:grid-cols-2">
          <img :src="seleccionado.imagenUrl" :alt="seleccionado.nombre" class="h-56 w-full object-cover sm:h-full" />
          <div class="flex flex-col gap-2 p-6">
            <span class="text-xs font-bold uppercase text-marca-600">{{ seleccionado.categoria }} · {{ seleccionado.codigo }}</span>
            <h2 class="text-xl font-bold">{{ seleccionado.nombre }}</h2>
            <p class="text-slate-600">{{ seleccionado.descripcion || 'Sin descripción.' }}</p>
            <p class="text-2xl font-bold text-emerald-600">{{ formatearBs(seleccionado.precio) }}</p>
            <p class="text-sm text-slate-500">Disponibles: {{ seleccionado.stock }}</p>

            <!-- Visitante y estudiante: elegir cantidad y agregar al carrito (HU-04) -->
            <template v-if="!esAdmin">
              <label class="mt-2 text-sm font-medium" for="cantidad">Cantidad deseada</label>
              <input id="cantidad" v-model.number="cantidad" type="number" min="1" max="99"
                     :class="['w-28 rounded-lg border px-3 py-2', excedeStock ? 'border-red-500' : 'border-slate-300']" />
              <!-- Aviso instantáneo del criterio 3 (el botón queda bloqueado mientras se muestre) -->
              <p v-if="excedeStock" class="text-sm font-semibold text-red-600" role="alert">
                Cantidad no disponible. Stock actual: {{ seleccionado.stock }}
                <span v-if="enCarrito > 0" class="block font-normal">Ya tienes {{ enCarrito }} en tu carrito.</span>
              </p>
              <p v-else class="text-sm text-slate-500">Subtotal: {{ formatearBs(seleccionado.precio * (cantidad || 0)) }}</p>
            </template>
            <!-- Administrador: vista de inventario (no compra) -->
            <p v-else :class="['mt-2 rounded-lg p-3 text-sm', seleccionado.stock <= 5 ? 'bg-amber-50 text-amber-800' : 'bg-slate-100 text-slate-600']">
              {{ seleccionado.stock === 0 ? 'Producto agotado: conviene reponer inventario.'
                 : seleccionado.stock <= 5 ? 'Stock bajo: quedan pocas unidades.' : 'Inventario suficiente.' }}
            </p>

            <div class="mt-auto flex flex-col gap-2 pt-4 sm:flex-row">
              <button class="rounded-lg bg-slate-200 px-4 py-2 font-semibold hover:bg-slate-300" @click="seleccionado = null">Volver</button>
              <button v-if="!esAdmin" :disabled="procesando || seleccionado.stock === 0 || excedeStock"
                      class="flex-1 rounded-lg bg-marca-600 px-4 py-2 font-semibold text-white hover:bg-marca-700 disabled:opacity-50"
                      @click="solicitarAgregar">Agregar al carrito</button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- ===== Formulario de registro de nuevo producto (HU-03, solo admin) ===== -->
    <div v-if="formularioAbierto" class="fixed inset-0 z-40 flex items-end justify-center bg-black/50 p-4 sm:items-center">
      <form class="max-h-[90vh] w-full max-w-xl overflow-y-auto rounded-2xl bg-white p-6 shadow-xl" novalidate @submit.prevent="enviarFormulario">
        <h2 class="mb-4 text-xl font-bold">Registro de nuevo producto</h2>
        <div class="grid gap-4 sm:grid-cols-2">
          <div>
            <label class="text-sm font-medium" for="f-codigo">Código *</label>
            <input id="f-codigo" v-model="form.codigo" :class="claseCampo('codigo')" placeholder="PAP-010" />
            <p v-if="erroresForm.codigo" class="text-sm text-red-600">{{ erroresForm.codigo }}</p>
          </div>
          <div>
            <label class="text-sm font-medium" for="f-categoria">Categoría *</label>
            <select id="f-categoria" v-model="form.categoria" :class="claseCampo('categoria')">
              <option v-for="c in productoController.categorias" :key="c" :value="c">{{ c }}</option>
            </select>
          </div>
          <div class="sm:col-span-2">
            <label class="text-sm font-medium" for="f-nombre">Nombre *</label>
            <input id="f-nombre" v-model="form.nombre" :class="claseCampo('nombre')" />
            <p v-if="erroresForm.nombre" class="text-sm text-red-600">{{ erroresForm.nombre }}</p>
          </div>
          <div class="sm:col-span-2">
            <label class="text-sm font-medium" for="f-desc">Descripción</label>
            <textarea id="f-desc" v-model="form.descripcion" rows="2" maxlength="500" :class="claseCampo('descripcion')" />
          </div>
          <div>
            <label class="text-sm font-medium" for="f-precio">Precio (Bs.) *</label>
            <input id="f-precio" v-model.number="form.precio" type="number" min="0.01" step="0.01" :class="claseCampo('precio')" />
            <p v-if="erroresForm.precio" class="text-sm text-red-600">{{ erroresForm.precio }}</p>
          </div>
          <div>
            <label class="text-sm font-medium" for="f-stock">Stock *</label>
            <input id="f-stock" v-model.number="form.stock" type="number" min="0" step="1" :class="claseCampo('stock')" />
            <p v-if="erroresForm.stock" class="text-sm text-red-600">{{ erroresForm.stock }}</p>
          </div>
          <div class="sm:col-span-2">
            <label class="text-sm font-medium" for="f-img">URL de la imagen *</label>
            <input id="f-img" v-model="form.imagenUrl" type="url" placeholder="https://…" :class="claseCampo('imagenUrl')" />
            <p v-if="erroresForm.imagenUrl" class="text-sm text-red-600">{{ erroresForm.imagenUrl }}</p>
            <img v-if="/^https?:\/\/.+/.test(form.imagenUrl)" :src="form.imagenUrl" alt="Vista previa" class="mt-2 h-24 rounded-lg object-cover" />
          </div>
        </div>
        <div class="mt-6 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <button type="button" class="rounded-lg bg-slate-200 px-4 py-2 font-semibold hover:bg-slate-300" @click="cancelarFormulario">Cancelar</button>
          <button type="submit" :disabled="procesando" class="rounded-lg bg-marca-600 px-4 py-2 font-semibold text-white hover:bg-marca-700 disabled:opacity-50">Guardar</button>
        </div>
      </form>
    </div>

    <ResenaView v-if="productoResenas" :producto-id="productoResenas.id" :producto-nombre="productoResenas.nombre" @cerrar="productoResenas = null" />

    <DialogoView v-if="dialogo" :titulo="dialogo.titulo" :mensaje="dialogo.mensaje" :tipo="dialogo.tipo" :botones="dialogo.botones" @accion="resolverDialogo" />

    <!-- Aviso temporal (desaparece a los 3 s); role="status" lo anuncia a lectores de pantalla -->
    <div v-if="toast" role="status" class="fixed bottom-4 right-4 z-50 rounded-lg bg-emerald-600 px-4 py-3 font-semibold text-white shadow-lg">✓ {{ toast }}</div>
  </section>
</template>
