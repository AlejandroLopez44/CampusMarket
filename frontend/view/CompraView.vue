<!--
  =====================================================================
  CompraView — Capa VIEW del módulo Compras.
  ---------------------------------------------------------------------
  modo="generar":   HU-05 Generar Compra.
  modo="historial": HU-09 Consultar Compras.

  Flujo HU-05 (diagrama corregido):
    Resumen → [carrito vacío] alerta "No hay productos en el carrito"
                → [OK] regresa a Consultar Producto · [Salir] menú principal
            → [con productos] Revisa → [Generar orden] → confirmación EXPLÍCITA
                → [stock insuficiente] alerta → [OK] vuelve a validar · [Salir] menú
                → [stock suficiente] "Orden generada - N° ..." (Pendiente de Pago)
                    → [OK] Procesar pago (próximo sprint → historial) · [Salir] menú
  =====================================================================
-->
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import DialogoView from './DialogoView.vue'
import { compraController, type Compra, type ResumenCompra } from '../control/CompraClientController'
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
 * inventario responde 409 "Stock insuficiente del ...".
 */
async function generarOrden(): Promise<void> {
  procesando.value = true
  try {
    const r = await compraController.generar()
    dialogo.value = {
      titulo: r.mensaje,
      mensaje: `Estado: ${r.datos.estadoEtiqueta}\nTotal: ${formatearBs(r.datos.total)}\nTe enviamos la confirmación a tu correo UCAB.`,
      tipo: 'exito',
      botones: [{ texto: 'Salir', valor: 'salir', estilo: 'secundario' }, { texto: 'OK', valor: 'ok' }],
      // [OK] → "Redirigir a Procesar Pago": el pago llega en el próximo sprint; por ahora se muestra el historial.
      alAccion: v => emit('navegar', v === 'ok' ? 'compras' : 'inicio'),
    }
  } catch (e) {
    if (e instanceof ApiError && (e.status === 409 || e.status === 400)) {
      dialogo.value = {
        titulo: e.status === 409 ? 'Stock insuficiente' : 'No se pudo generar la orden', mensaje: e.message, tipo: 'error',
        botones: [{ texto: 'Salir', valor: 'salir', estilo: 'secundario' }, { texto: 'OK', valor: 'ok' }],
        // [OK] vuelve a "Validar producto, cantidad y precio" (inicio del flujo).
        alAccion: v => { if (v === 'ok') { carritoController.consultar().catch(() => undefined); cargarResumen() } else emit('navegar', 'inicio') },
      }
    } else manejarError(e)
  } finally {
    procesando.value = false
  }
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
  <section class="mx-auto max-w-3xl">
    <h1 class="mb-4 text-2xl font-bold">{{ modo === 'generar' ? 'Resumen de compra' : 'Mis compras' }}</h1>

    <p v-if="cargando" class="py-10 text-center text-slate-500">Cargando…</p>
    <div v-else-if="error" class="rounded-xl bg-red-50 p-6 text-center text-red-700">
      {{ error }}
      <button class="ml-2 font-semibold underline" @click="modo === 'generar' ? cargarResumen() : cargarHistorial()">Reintentar</button>
    </div>

    <!-- ============ HU-05: Resumen ============ -->
    <div v-else-if="modo === 'generar' && resumen && !resumen.carritoVacio" class="rounded-2xl bg-white p-4 shadow-sm sm:p-6">
      <p class="mb-3 text-slate-500">{{ resumen.mensaje }}</p>
      <!-- Tabla con scroll horizontal en móviles -->
      <div class="overflow-x-auto">
        <table class="w-full min-w-[420px] text-left">
          <thead class="text-sm text-slate-500">
            <tr><th class="py-2">Producto</th><th class="py-2 text-center">Cant.</th><th class="py-2 text-right">Precio</th><th class="py-2 text-right">Subtotal</th></tr>
          </thead>
          <tbody class="divide-y divide-slate-100">
            <tr v-for="d in resumen.detalles" :key="d.productoId">
              <td class="py-2">{{ d.nombre }}</td>
              <td class="py-2 text-center">{{ d.cantidad }}</td>
              <td class="py-2 text-right">{{ formatearBs(d.precioUnitario) }}</td>
              <td class="py-2 text-right font-semibold">{{ formatearBs(d.subtotal) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="mt-4 flex items-center justify-between border-t-2 border-slate-200 pt-4">
        <span class="text-lg font-semibold">Total a pagar</span>
        <span class="text-2xl font-bold text-emerald-600">{{ formatearBs(resumen.total) }}</span>
      </div>
      <div class="mt-6 flex flex-col gap-2 sm:flex-row sm:justify-end">
        <button class="rounded-lg bg-slate-200 px-4 py-2 font-semibold hover:bg-slate-300" @click="emit('navegar', 'inicio')">Salir</button>
        <button :disabled="procesando" class="rounded-lg bg-marca-600 px-6 py-2 font-semibold text-white hover:bg-marca-700 disabled:opacity-50" @click="solicitarConfirmacion">Generar orden</button>
      </div>
    </div>

    <!-- ============ HU-09: Historial ============ -->
    <template v-else-if="modo === 'historial'">
      <div v-if="historial.length === 0" class="rounded-xl bg-white p-8 text-center text-slate-500 shadow-sm">
        Aún no tienes compras registradas.
      </div>
      <ul v-else class="space-y-3">
        <li v-for="c in historial" :key="c.id" class="rounded-2xl bg-white shadow-sm">
          <button class="flex w-full flex-wrap items-center justify-between gap-2 p-4 text-left" :aria-expanded="abierta === c.id" @click="abierta = abierta === c.id ? null : c.id">
            <div>
              <strong>Orden {{ c.numeroOrden }}</strong>
              <p class="text-sm text-slate-500">{{ formatearFecha(c.fecha) }}</p>
            </div>
            <span :class="['rounded-full px-3 py-0.5 text-xs font-bold', claseEstado(c.estado)]">{{ c.estadoEtiqueta }}</span>
            <span class="font-bold text-emerald-600">{{ formatearBs(c.total) }}</span>
          </button>
          <ul v-if="abierta === c.id" class="border-t border-slate-100 px-4 py-3 text-sm">
            <li v-for="d in c.detalles" :key="d.productoId" class="flex justify-between py-1">
              <span>{{ d.cantidad }} × {{ d.nombre }}</span><span>{{ formatearBs(d.subtotal) }}</span>
            </li>
          </ul>
        </li>
      </ul>
      <button class="mt-6 w-full rounded-lg bg-slate-200 px-4 py-2 font-semibold hover:bg-slate-300" @click="emit('navegar', 'inicio')">← Volver al menú principal</button>
    </template>

    <DialogoView v-if="dialogo" :titulo="dialogo.titulo" :mensaje="dialogo.mensaje" :tipo="dialogo.tipo" :botones="dialogo.botones" @accion="resolverDialogo" />
  </section>
</template>
