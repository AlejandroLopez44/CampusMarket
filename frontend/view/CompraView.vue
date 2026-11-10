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
  <section class="mx-auto max-w-3xl">
    <!-- ============ FACTURA (tras generar la orden o desde el historial) ============ -->
    <template v-if="factura">
      <p v-if="avisoFactura" class="mb-4 whitespace-pre-line rounded-xl bg-emerald-50 p-4 font-semibold text-emerald-800">{{ avisoFactura }}</p>

      <article class="rounded-2xl bg-white p-4 shadow-sm sm:p-8" aria-label="Factura">
        <header class="flex flex-col gap-4 border-b border-slate-200 pb-4 sm:flex-row sm:items-start sm:justify-between">
          <div>
            <p class="text-xl font-extrabold text-marca-600">CampusMarket</p>
            <p class="text-sm text-slate-500">Insumos estudiantiles · UCAB · Gran Caracas</p>
          </div>
          <div class="sm:text-right">
            <h1 class="text-lg font-bold">Factura N° {{ factura.numeroOrden }}</h1>
            <p class="text-sm text-slate-500">Fecha: {{ formatearFecha(factura.fecha) }}</p>
            <span :class="['mt-1 inline-block rounded-full px-3 py-0.5 text-xs font-bold', claseEstado(factura.estado)]">{{ factura.estadoEtiqueta }}</span>
          </div>
        </header>

        <div class="grid gap-1 border-b border-slate-200 py-4 text-sm sm:grid-cols-3">
          <p><span class="text-slate-500">Cliente:</span> <span class="font-medium">{{ factura.cliente }}</span></p>
          <p class="break-all"><span class="text-slate-500">Correo:</span> {{ factura.correo }}</p>
          <p><span class="text-slate-500">Teléfono:</span> {{ factura.telefono || 'No registrado' }}</p>
        </div>

        <div class="overflow-x-auto py-4">
          <table class="w-full min-w-[440px] text-left text-sm">
            <thead class="text-xs uppercase text-slate-500">
              <tr><th class="py-2">Producto</th><th class="py-2 text-center">Cant.</th><th class="py-2 text-right">Precio unit.</th><th class="py-2 text-right">Subtotal</th></tr>
            </thead>
            <tbody class="divide-y divide-slate-100">
              <tr v-for="d in factura.detalles" :key="d.productoId">
                <td class="py-2">{{ d.nombre }}</td>
                <td class="py-2 text-center tabular-nums">{{ d.cantidad }}</td>
                <td class="py-2 text-right tabular-nums">{{ formatearBs(d.precioUnitario) }}</td>
                <td class="py-2 text-right font-semibold tabular-nums">{{ formatearBs(d.subtotal) }}</td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="flex items-center justify-between border-t-2 border-slate-200 pt-4">
          <span class="font-semibold">Total ({{ factura.unidades }} unidades)</span>
          <span class="text-2xl font-bold tabular-nums text-emerald-600">{{ formatearBs(factura.total) }}</span>
        </div>

        <p v-if="factura.estado === 'PENDIENTE_PAGO'" class="mt-4 rounded-lg bg-amber-50 p-3 text-sm text-amber-800">
          Pago pendiente. El pago real se habilitará en la próxima iteración; por ahora puedes registrar un pago simulado para pruebas.
        </p>
      </article>

      <div class="mt-6 flex flex-col gap-2 sm:flex-row sm:justify-end">
        <button class="rounded-lg bg-slate-200 px-4 py-2 font-semibold hover:bg-slate-300" @click="emit('navegar', 'inicio')">Volver al inicio</button>
        <button class="rounded-lg bg-slate-200 px-4 py-2 font-semibold hover:bg-slate-300" @click="cerrarFactura">
          {{ modo === 'historial' ? '← Volver a mis compras' : 'Ver mis compras' }}
        </button>
        <button v-if="factura.estado === 'PENDIENTE_PAGO'" :disabled="procesando"
                class="rounded-lg bg-marca-600 px-6 py-2 font-semibold text-white hover:bg-marca-700 disabled:opacity-50"
                @click="solicitarPagoSimulado">Registrar pago (simulado)</button>
      </div>
    </template>

    <template v-else>
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
            <div v-if="abierta === c.id" class="border-t border-slate-100 px-4 py-3 text-sm">
              <ul>
                <li v-for="d in c.detalles" :key="d.productoId" class="flex justify-between py-1">
                  <span>{{ d.cantidad }} × {{ d.nombre }}</span><span>{{ formatearBs(d.subtotal) }}</span>
                </li>
              </ul>
              <button class="mt-3 rounded-lg bg-marca-600 px-4 py-2 font-semibold text-white hover:bg-marca-700" @click="abrirFactura(c.id)">
                {{ c.estado === 'PENDIENTE_PAGO' ? 'Ver factura y registrar pago' : 'Ver factura' }}
              </button>
            </div>
          </li>
        </ul>
        <button class="mt-6 w-full rounded-lg bg-slate-200 px-4 py-2 font-semibold hover:bg-slate-300" @click="emit('navegar', 'inicio')">← Volver al menú principal</button>
      </template>
    </template>

    <DialogoView v-if="dialogo" :titulo="dialogo.titulo" :mensaje="dialogo.mensaje" :tipo="dialogo.tipo" :botones="dialogo.botones" @accion="resolverDialogo" />
  </section>
</template>
