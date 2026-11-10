<!--
  =====================================================================
  CompraView — Capa VIEW del módulo Compras (solo estudiantes).
  ---------------------------------------------------------------------
  modo="generar":   HU-05 Generar Compra.
  modo="historial": HU-09 Consultar Compras.

  Flujo HU-05 (diagrama corregido):
    Resumen → [carrito vacío] alerta "No hay productos en el carrito"
                → [OK] regresa a Consultar Producto · [Salir] menú principal
            → [con productos] Revisa → [Generar orden] → confirmación EXPLÍCITA
                → [stock insuficiente] alerta → [OK] vuelve a validar · [Salir] menú
                → [stock suficiente] "Orden generada - N° ..." (Pendiente de Pago)
                    → se muestra la FACTURA con todos los productos
                    → [Registrar pago (simulado)] → la orden queda "Pagada"
                      (no se piden datos: el pago real es de la 2.ª iteración)

  Desde "Mis compras" también se puede abrir la factura de cada orden y,
  si está pendiente, registrar su pago simulado.
  =====================================================================
-->
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import DialogoView from './DialogoView.vue'
import Icono from './Icono.vue'
import { compraController, type Compra, type Factura, type ResumenCompra } from '../control/CompraClientController'
import { carritoController } from '../control/CarritoClientController'
import { ApiError, formatearBs, formatearFecha } from '../control/ApiClient'
import type { BotonDialogo, Destino } from '../control/NavegacionController'

const props = defineProps<{ modo: 'generar' | 'historial' }>()
const emit = defineEmits<{ navegar: [destino: Destino] }>()

const cargando = ref(true)
const error = ref('')
const resumen = ref<ResumenCompra | null>(null)
const historial = ref<Compra[]>([])
const abierta = ref<number | null>(null) // Orden expandida en el historial
const procesando = ref(false)

/** Factura visible (tras generar la orden o al abrirla desde el historial). */
const factura = ref<Factura | null>(null)
/** Mensaje destacado sobre la factura ("Orden generada - N° ...", "Pago registrado"). */
const avisoFactura = ref('')

interface Dialogo { titulo: string; mensaje: string; tipo: 'info' | 'exito' | 'error' | 'confirmar'; botones: BotonDialogo[]; alAccion: (v: string) => void }
const dialogo = ref<Dialogo | null>(null)
function resolverDialogo(valor: string): void {
  const d = dialogo.value
  dialogo.value = null
  d?.alAccion(valor)
}

/** Maneja errores comunes: 401 → iniciar sesión; resto → mensaje en pantalla. */
function manejarError(e: unknown): void {
  if (e instanceof ApiError && e.status === 401) emit('navegar', 'cuenta')
  else error.value = (e as Error).message
}

// ======================= HU-05 =======================

/**
 * Paso 1: el sistema valida producto, cantidad y precio y arma el resumen.
 * Si el carrito está vacío, ALERTA y DETIENE el flujo.
 */
async function cargarResumen(): Promise<void> {
  cargando.value = true
  error.value = ''
  try {
    resumen.value = await compraController.resumen()
    if (resumen.value.carritoVacio) {
      dialogo.value = {
        titulo: resumen.value.mensaje, mensaje: 'Agrega productos desde el catálogo para generar una compra.', tipo: 'info',
        botones: [{ texto: 'Salir', valor: 'salir', estilo: 'secundario' }, { texto: 'OK', valor: 'ok' }],
        alAccion: v => emit('navegar', v === 'ok' ? 'productos' : 'inicio'),
      }
    }
  } catch (e) {
    manejarError(e)
  } finally {
    cargando.value = false
  }
}

/** Paso 2: [Generar orden] → confirmación explícita con el total en Bs. */
function solicitarConfirmacion(): void {
  if (!resumen.value) return
  dialogo.value = {
    titulo: '¿Confirmar la orden de compra?',
    mensaje: `Se registrará una orden por ${formatearBs(resumen.value.total)} con estado "Pendiente de Pago".`,
    tipo: 'confirmar',
    botones: [{ texto: 'Cancelar', valor: 'cancelar', estilo: 'secundario' }, { texto: 'Confirmar orden', valor: 'confirmar' }],
    alAccion: v => v === 'confirmar' && generarOrden(),
  }
}

/**
 * Paso 3: registra la orden. El backend valida stock con bloqueo; si falta
 * inventario responde 409 "Stock insuficiente en el producto [Nombre]". Al éxito se muestra la factura.
 */
async function generarOrden(): Promise<void> {
  procesando.value = true
  try {
    const r = await compraController.generar()
    await abrirFactura(r.datos.id, `${r.mensaje}. Te enviamos la confirmación a tu correo UCAB.`)
  } catch (e) {
    if (e instanceof ApiError && e.status === 409) {
      alertarStockInsuficiente(e.message)
    } else if (e instanceof ApiError && e.status === 400) {
      // Carrito vacío (p. ej. se vació en otra pestaña): [OK] al catálogo, [Salir] al menú.
      dialogo.value = {
        titulo: 'No se pudo generar la orden', mensaje: e.message, tipo: 'error',
        botones: [{ texto: 'Salir', valor: 'salir', estilo: 'secundario' }, { texto: 'OK', valor: 'ok' }],
        alAccion: v => emit('navegar', v === 'ok' ? 'productos' : 'inicio'),
      }
    } else manejarError(e)
  } finally {
    procesando.value = false
  }
}

/**
 * Rama "stock insuficiente". En el Sprint 1 NO se pueden ajustar cantidades, así que
 * repetir la validación sería un bucle sin salida. Se ofrecen dos salidas reales:
 *  • [Salir] → menú principal, sin cambiar nada.
 *  • [Vaciar carrito y volver al catálogo] → (con confirmación) empezar de nuevo.
 */
let ultimoMensajeStock = ''
function alertarStockInsuficiente(mensaje: string): void {
  ultimoMensajeStock = mensaje // Para volver a mostrarlo si el usuario cancela el vaciado
  dialogo.value = {
    titulo: 'Stock insuficiente', mensaje: `${mensaje}.\nNo es posible generar la compra con este carrito.`, tipo: 'error',
    botones: [{ texto: 'Salir', valor: 'salir', estilo: 'secundario' }, { texto: 'Vaciar carrito y volver al catálogo', valor: 'vaciar', estilo: 'peligro' }],
    alAccion: v => (v === 'vaciar' ? confirmarVaciado() : emit('navegar', 'inicio')),
  }
}

/** Vaciar el carrito borra todo: se confirma antes (acción irreversible). */
function confirmarVaciado(): void {
  dialogo.value = {
    titulo: '¿Vaciar tu carrito?', mensaje: 'Se quitarán todos los productos de tu carrito. Esta acción no se puede deshacer.', tipo: 'confirmar',
    botones: [{ texto: 'Cancelar', valor: 'cancelar', estilo: 'secundario' }, { texto: 'Vaciar carrito', valor: 'vaciar', estilo: 'peligro' }],
    alAccion: async v => {
      if (v !== 'vaciar') return alertarStockInsuficiente(ultimoMensajeStock)
      try {
        await carritoController.vaciar()
        emit('navegar', 'productos')
      } catch (e) {
        manejarError(e)
      }
    },
  }
}

// ======================= Factura y pago simulado =======================

/**
 * Pide la factura al servidor y la muestra.
 * @param id     id de la orden
 * @param aviso  mensaje destacado opcional (p. ej. "Orden generada - N° ...")
 */
async function abrirFactura(id: number, aviso = ''): Promise<void> {
  try {
    factura.value = await compraController.factura(id)
    avisoFactura.value = aviso
    window.scrollTo({ top: 0, behavior: 'smooth' })
  } catch (e) {
    manejarError(e)
  }
}

/** Pide confirmación antes de registrar el pago simulado (acción que cambia el estado). */
function solicitarPagoSimulado(): void {
  const f = factura.value
  if (!f) return
  dialogo.value = {
    titulo: '¿Registrar el pago simulado?',
    mensaje: `La orden ${f.numeroOrden} por ${formatearBs(f.total)} quedará como "Pagada".\nNo se solicitan datos de pago: es una simulación para pruebas.`,
    tipo: 'confirmar',
    botones: [{ texto: 'Cancelar', valor: 'cancelar', estilo: 'secundario' }, { texto: 'Registrar pago', valor: 'pagar' }],
    alAccion: v => v === 'pagar' && registrarPago(f.id),
  }
}

/** POST pago-simulado: actualiza la factura en pantalla con el nuevo estado. */
async function registrarPago(id: number): Promise<void> {
  procesando.value = true
  try {
    const r = await compraController.registrarPagoSimulado(id)
    factura.value = r.datos
    avisoFactura.value = r.mensaje
  } catch (e) {
    if (e instanceof ApiError && e.status === 409) avisoFactura.value = e.message
    else manejarError(e)
  } finally {
    procesando.value = false
  }
}

/** Cierra la factura: desde el historial vuelve a la lista actualizada; desde la compra va a "Mis compras". */
function cerrarFactura(): void {
  factura.value = null
  avisoFactura.value = ''
  if (props.modo === 'historial') cargarHistorial()
  else emit('navegar', 'compras')
}

// ======================= HU-09 =======================

/** Lista las órdenes de la cuenta activa (más recientes primero). */
async function cargarHistorial(): Promise<void> {
  cargando.value = true
  error.value = ''
  try {
    historial.value = await compraController.historial()
  } catch (e) {
    manejarError(e)
  } finally {
    cargando.value = false
  }
}

/** Colores del estado de la orden (solo presentación). */
const claseEstado = (estado: Compra['estado']) => ({
  PENDIENTE_PAGO: 'bg-amber-100 text-amber-800',
  PAGADA: 'bg-emerald-100 text-emerald-800',
  CANCELADA: 'bg-red-100 text-red-800',
}[estado])

onMounted(() => (props.modo === 'generar' ? cargarResumen() : cargarHistorial()))
</script>

<template>
  <section>
    <!-- ============ FACTURA (tras generar la orden o desde el historial) ============ -->
    <template v-if="factura">
      <!-- Progreso: en la factura el proceso está completo (solo al venir de "Generar compra") -->
      <ol v-if="modo === 'generar'" class="mb-6 flex items-center gap-2 text-xs font-semibold sm:text-sm" aria-label="Pasos de la compra">
        <li class="flex items-center gap-2 text-emerald-700"><span class="flex h-6 w-6 items-center justify-center rounded-full bg-emerald-600 text-white"><Icono nombre="check" :tamano="14" :grosor="3" /></span>Carrito</li>
        <li class="h-px flex-1 bg-emerald-300" aria-hidden="true" />
        <li class="flex items-center gap-2 text-emerald-700"><span class="flex h-6 w-6 items-center justify-center rounded-full bg-emerald-600 text-white"><Icono nombre="check" :tamano="14" :grosor="3" /></span>Resumen</li>
        <li class="h-px flex-1 bg-emerald-300" aria-hidden="true" />
        <li class="flex items-center gap-2 text-marca-700"><span class="flex h-6 w-6 items-center justify-center rounded-full bg-marca-700 text-white">3</span>Factura</li>
      </ol>

      <p v-if="avisoFactura" class="animar-entrada mb-5 flex items-start gap-3 whitespace-pre-line rounded-2xl border border-emerald-200 bg-emerald-50 p-4 font-semibold text-emerald-800">
        <span class="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-emerald-600 text-white"><Icono nombre="check" :tamano="16" :grosor="2.5" /></span>
        {{ avisoFactura }}
      </p>

      <article class="tarjeta overflow-hidden" aria-label="Factura">
        <!-- Cabecera del documento -->
        <header class="flex flex-col gap-4 bg-marca-900 p-6 text-white sm:flex-row sm:items-start sm:justify-between sm:p-8">
          <div class="flex items-center gap-3">
            <span class="flex h-11 w-11 items-center justify-center rounded-xl bg-white/10 font-titulo font-extrabold text-acento-400">CM</span>
            <div>
              <p class="font-titulo text-xl font-extrabold">CampusMarket</p>
              <p class="text-sm text-marca-200">Insumos estudiantiles · UCAB · Gran Caracas</p>
            </div>
          </div>
          <div class="sm:text-right">
            <p class="text-xs font-bold uppercase tracking-widest text-marca-200">Factura</p>
            <h1 class="font-titulo text-xl font-extrabold">N° {{ factura.numeroOrden }}</h1>
            <p class="text-sm text-marca-200">Fecha: {{ formatearFecha(factura.fecha) }}</p>
            <span :class="['insignia mt-2', claseEstado(factura.estado)]">{{ factura.estadoEtiqueta }}</span>
          </div>
        </header>

        <div class="p-6 sm:p-8">
          <!-- Datos del cliente -->
          <div class="grid gap-3 rounded-2xl bg-slate-50 p-4 text-sm sm:grid-cols-3">
            <p><span class="block text-xs font-bold uppercase tracking-wider text-slate-500">Cliente</span><span class="font-semibold">{{ factura.cliente }}</span></p>
            <p class="break-all"><span class="block text-xs font-bold uppercase tracking-wider text-slate-500">Correo</span>{{ factura.correo }}</p>
            <p><span class="block text-xs font-bold uppercase tracking-wider text-slate-500">Teléfono</span>{{ factura.telefono || 'No registrado' }}</p>
          </div>

          <!-- Detalle -->
          <div class="mt-6 overflow-x-auto">
            <table class="w-full min-w-[460px] text-left text-sm">
              <thead class="border-b border-slate-200 text-xs uppercase tracking-wider text-slate-500">
                <tr><th class="py-2.5">Producto</th><th class="py-2.5 text-center">Cant.</th><th class="py-2.5 text-right">Precio unit.</th><th class="py-2.5 text-right">Subtotal</th></tr>
              </thead>
              <tbody class="divide-y divide-slate-100">
                <tr v-for="d in factura.detalles" :key="d.productoId">
                  <td class="py-3 font-medium">{{ d.nombre }}</td>
                  <td class="py-3 text-center tabular-nums">{{ d.cantidad }}</td>
                  <td class="py-3 text-right tabular-nums text-slate-600">{{ formatearBs(d.precioUnitario) }}</td>
                  <td class="py-3 text-right font-semibold tabular-nums">{{ formatearBs(d.subtotal) }}</td>
                </tr>
              </tbody>
            </table>
          </div>

          <div class="mt-4 flex items-end justify-between border-t-2 border-tinta pt-4">
            <span class="font-semibold">Total · {{ factura.unidades }} unidades</span>
            <span class="whitespace-nowrap font-titulo text-2xl font-extrabold text-marca-800 sm:text-3xl">{{ formatearBs(factura.total) }}</span>
          </div>

          <p v-if="factura.estado === 'PENDIENTE_PAGO'" class="mt-6 flex items-start gap-3 rounded-2xl bg-acento-100 p-4 text-sm text-amber-900">
            <Icono nombre="reloj" :tamano="20" />
            <span><strong>Pago pendiente.</strong> El pago real se habilitará en la próxima iteración; por ahora puedes registrar un pago simulado para pruebas.</span>
          </p>
          <p v-else-if="factura.estado === 'PAGADA'" class="mt-6 flex items-center gap-3 rounded-2xl bg-emerald-50 p-4 text-sm font-semibold text-emerald-800">
            <Icono nombre="check" :tamano="20" /> Esta orden ya está pagada.
          </p>
        </div>
      </article>

      <div class="mt-6 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
        <button class="btn-secundario" @click="emit('navegar', 'inicio')"><Icono nombre="inicio" :tamano="16" /> Volver al inicio</button>
        <button class="btn-secundario" @click="cerrarFactura">
          <Icono :nombre="modo === 'historial' ? 'izquierda' : 'factura'" :tamano="16" /> {{ modo === 'historial' ? 'Volver a mis compras' : 'Ver mis compras' }}
        </button>
        <button v-if="factura.estado === 'PENDIENTE_PAGO'" :disabled="procesando" class="btn-acento !px-6" @click="solicitarPagoSimulado">
          <Icono nombre="billete" :tamano="18" /> Registrar pago (simulado)
        </button>
      </div>
    </template>

    <template v-else>
      <div class="mb-6">
        <p class="eyebrow">{{ modo === 'generar' ? 'Tu compra' : 'Historial' }}</p>
        <h1 class="mt-1 text-3xl font-extrabold">{{ modo === 'generar' ? 'Resumen de compra' : 'Mis compras' }}</h1>
      </div>

      <!-- Progreso de la compra (paso 2) -->
      <ol v-if="modo === 'generar'" class="mb-6 flex items-center gap-2 text-xs font-semibold sm:text-sm" aria-label="Pasos de la compra">
        <li class="flex items-center gap-2 text-emerald-700"><span class="flex h-6 w-6 items-center justify-center rounded-full bg-emerald-600 text-white"><Icono nombre="check" :tamano="14" :grosor="3" /></span>Carrito</li>
        <li class="h-px flex-1 bg-emerald-300" aria-hidden="true" />
        <li class="flex items-center gap-2 text-marca-700"><span class="flex h-6 w-6 items-center justify-center rounded-full bg-marca-700 text-white">2</span>Resumen</li>
        <li class="h-px flex-1 bg-slate-300" aria-hidden="true" />
        <li class="flex items-center gap-2 text-slate-400"><span class="flex h-6 w-6 items-center justify-center rounded-full bg-slate-200">3</span>Factura</li>
      </ol>

      <div v-if="cargando" class="tarjeta space-y-3 p-6" aria-label="Cargando">
        <div v-for="n in 3" :key="n" class="h-12 animate-pulse rounded-xl bg-slate-100" />
      </div>
      <div v-else-if="error" class="tarjeta flex flex-col items-center gap-3 p-8 text-center">
        <span class="flex h-12 w-12 items-center justify-center rounded-full bg-red-50 text-red-600"><Icono nombre="alerta" :tamano="24" /></span>
        <p class="font-semibold text-red-700">{{ error }}</p>
        <button class="btn-secundario" @click="modo === 'generar' ? cargarResumen() : cargarHistorial()"><Icono nombre="actualizar" :tamano="16" /> Reintentar</button>
      </div>

      <!-- ============ HU-05: Resumen ============ -->
      <div v-else-if="modo === 'generar' && resumen && !resumen.carritoVacio" class="grid gap-6 lg:grid-cols-[1fr_22rem] lg:items-start">
        <div class="tarjeta overflow-hidden">
          <p class="border-b border-slate-100 px-5 py-4 text-sm text-slate-500">{{ resumen.mensaje }}</p>
          <!-- Tabla con scroll horizontal en móviles -->
          <div class="overflow-x-auto">
            <table class="w-full min-w-[440px] text-left text-sm">
              <thead class="bg-slate-50 text-xs uppercase tracking-wider text-slate-500">
                <tr><th class="px-5 py-3">Producto</th><th class="px-3 py-3 text-center">Cant.</th><th class="px-3 py-3 text-right">Precio</th><th class="px-5 py-3 text-right">Subtotal</th></tr>
              </thead>
              <tbody class="divide-y divide-slate-100">
                <tr v-for="d in resumen.detalles" :key="d.productoId">
                  <td class="px-5 py-3.5 font-medium">{{ d.nombre }}</td>
                  <td class="px-3 py-3.5 text-center tabular-nums">{{ d.cantidad }}</td>
                  <td class="px-3 py-3.5 text-right tabular-nums text-slate-600">{{ formatearBs(d.precioUnitario) }}</td>
                  <td class="px-5 py-3.5 text-right font-semibold tabular-nums">{{ formatearBs(d.subtotal) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
        <aside class="tarjeta p-5 lg:sticky lg:top-24">
          <h2 class="text-lg font-bold">Total a pagar</h2>
          <p class="mt-2 whitespace-nowrap font-titulo text-2xl font-extrabold text-marca-800 sm:text-3xl">{{ formatearBs(resumen.total) }}</p>
          <p class="mt-1 text-sm text-slate-500">La orden quedará como "Pendiente de Pago".</p>
          <div class="mt-5 flex flex-col gap-2">
            <button :disabled="procesando" class="btn-primario !py-3" @click="solicitarConfirmacion"><Icono nombre="factura" :tamano="18" /> Generar orden</button>
            <button class="btn-secundario" @click="emit('navegar', 'inicio')">Salir</button>
          </div>
        </aside>
      </div>

      <!-- ============ HU-09: Historial ============ -->
      <template v-else-if="modo === 'historial'">
        <div v-if="historial.length === 0" class="tarjeta flex flex-col items-center gap-3 px-6 py-14 text-center">
          <span class="flex h-16 w-16 items-center justify-center rounded-full bg-marca-50 text-marca-600"><Icono nombre="compras" :tamano="30" /></span>
          <p class="text-xl font-bold">Aún no tienes compras registradas</p>
          <p class="max-w-sm text-slate-500">Cuando generes una orden, aquí verás su factura y su estado.</p>
          <button class="btn-primario mt-2" @click="emit('navegar', 'productos')"><Icono nombre="catalogo" :tamano="18" /> Explorar el catálogo</button>
        </div>
        <ul v-else class="space-y-3">
          <li v-for="c in historial" :key="c.id" class="tarjeta overflow-hidden">
            <button class="flex w-full items-center gap-4 p-4 text-left transition hover:bg-slate-50 sm:p-5" :aria-expanded="abierta === c.id" @click="abierta = abierta === c.id ? null : c.id">
              <span class="hidden h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-marca-50 text-marca-700 sm:flex"><Icono nombre="factura" :tamano="22" /></span>
              <div class="min-w-0 flex-1">
                <p class="font-bold">Orden {{ c.numeroOrden }}</p>
                <p class="text-sm text-slate-500">{{ formatearFecha(c.fecha) }} · {{ c.detalles.length }} producto(s)</p>
              </div>
              <div class="flex flex-col items-end gap-1">
                <span class="font-titulo font-bold text-marca-800">{{ formatearBs(c.total) }}</span>
                <span :class="['insignia', claseEstado(c.estado)]">{{ c.estadoEtiqueta }}</span>
              </div>
              <span :class="['text-slate-400 transition', abierta === c.id ? 'rotate-90' : '']"><Icono nombre="derecha" :tamano="18" /></span>
            </button>
            <div v-if="abierta === c.id" class="animar-entrada border-t border-slate-100 bg-slate-50/60 px-5 py-4 text-sm">
              <ul class="space-y-1.5">
                <li v-for="d in c.detalles" :key="d.productoId" class="flex justify-between gap-3">
                  <span><span class="font-semibold tabular-nums">{{ d.cantidad }} ×</span> {{ d.nombre }}</span><span class="tabular-nums">{{ formatearBs(d.subtotal) }}</span>
                </li>
              </ul>
              <button :class="[c.estado === 'PENDIENTE_PAGO' ? 'btn-acento' : 'btn-secundario', 'mt-4']" @click="abrirFactura(c.id)">
                <Icono :nombre="c.estado === 'PENDIENTE_PAGO' ? 'billete' : 'factura'" :tamano="16" />
                {{ c.estado === 'PENDIENTE_PAGO' ? 'Ver factura y registrar pago' : 'Ver factura' }}
              </button>
            </div>
          </li>
        </ul>
        <button class="btn-secundario mt-6 w-full sm:w-auto" @click="emit('navegar', 'inicio')"><Icono nombre="izquierda" :tamano="16" /> Volver al menú principal</button>
      </template>
    </template>

    <DialogoView v-if="dialogo" :titulo="dialogo.titulo" :mensaje="dialogo.mensaje" :tipo="dialogo.tipo" :botones="dialogo.botones" @accion="resolverDialogo" />
  </section>
</template>
