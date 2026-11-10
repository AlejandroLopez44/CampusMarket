<!--
  =====================================================================
  EstadisticasView — Capa VIEW del panel del Administrador.
  ---------------------------------------------------------------------
  Épica "Gestión de compras (Consultar)" del Administrador: números del
  negocio para tomar decisiones.
    • Ventas (órdenes pagadas), monto pendiente de cobro y ticket promedio.
    • Alertas de inventario: agotados y stock bajo (evitar quiebres de stock).
    • Productos más vendidos y ventas de los últimos 7 días.
    • Últimas órdenes de todos los estudiantes (control del estado de cada compra).
    • Reseñas: cantidad y calificación promedio.

  Responsabilidad (SRP): solo presenta. Los datos y cálculos vienen de
  EstadisticasClientController (DIP).
  =====================================================================
-->
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { estadisticasController, type Estadisticas, type OrdenReciente } from '../control/EstadisticasClientController'
import { formatearBs, formatearFecha } from '../control/ApiClient'
import type { Destino } from '../control/NavegacionController'

const emit = defineEmits<{ navegar: [destino: Destino] }>()

const datos = ref<Estadisticas | null>(null)
const cargando = ref(true)
const error = ref('')

/** Pide las estadísticas al backend. Se puede repetir con "Actualizar". */
async function cargar(): Promise<void> {
  cargando.value = true
  error.value = ''
  try {
    datos.value = await estadisticasController.obtener()
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    cargando.value = false
  }
}

/** Escala del gráfico: el día con más ventas ocupa el 100 % de la altura. */
const maximo = computed(() => (datos.value ? estadisticasController.maximoDiario(datos.value.ventasUltimos7Dias) : 1))

/** Porcentaje de altura de una barra (mínimo visible de 2 % si hay algo). */
function alto(monto: number): string {
  const pct = (Number(monto) / maximo.value) * 100
  return `${monto > 0 ? Math.max(pct, 2) : 0}%`
}

/** Colores del estado de una orden (solo presentación). */
const claseEstado = (estado: OrdenReciente['estado']) => ({
  PENDIENTE_PAGO: 'bg-amber-100 text-amber-800',
  PAGADA: 'bg-emerald-100 text-emerald-800',
  CANCELADA: 'bg-red-100 text-red-800',
}[estado])

onMounted(cargar)
</script>

<template>
  <section>
    <div class="mb-6 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
      <div>
        <h1 class="text-2xl font-bold">Panel del administrador</h1>
        <p class="text-slate-500">Ventas, inventario y órdenes de CampusMarket · Montos en bolívares</p>
      </div>
      <div class="flex gap-2">
        <button class="rounded-lg bg-slate-200 px-4 py-2 font-semibold hover:bg-slate-300" @click="cargar">Actualizar</button>
        <button class="rounded-lg bg-marca-600 px-4 py-2 font-semibold text-white hover:bg-marca-700" @click="emit('navegar', 'productos')">Gestionar catálogo</button>
      </div>
    </div>

    <p v-if="cargando && !datos" class="py-10 text-center text-slate-500">Calculando estadísticas…</p>
    <div v-else-if="error" class="rounded-xl bg-red-50 p-6 text-center text-red-700">
      {{ error }} <button class="ml-2 font-semibold underline" @click="cargar">Reintentar</button>
    </div>

    <template v-else-if="datos">
      <!-- ===== Indicadores principales ===== -->
      <div class="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <div class="rounded-2xl bg-white p-5 shadow-sm">
          <p class="text-sm text-slate-500">Ventas (órdenes pagadas)</p>
          <p class="mt-1 text-2xl font-bold tabular-nums text-emerald-600">{{ formatearBs(datos.ventasPagadas) }}</p>
          <p class="text-xs text-slate-500">{{ datos.ordenesPagadas }} orden(es) pagada(s)</p>
        </div>
        <div class="rounded-2xl bg-white p-5 shadow-sm">
          <p class="text-sm text-slate-500">Pendiente de cobro</p>
          <p class="mt-1 text-2xl font-bold tabular-nums text-amber-600">{{ formatearBs(datos.montoPendiente) }}</p>
          <p class="text-xs text-slate-500">{{ datos.ordenesPendientes }} orden(es) pendiente(s) de pago</p>
        </div>
        <div class="rounded-2xl bg-white p-5 shadow-sm">
          <p class="text-sm text-slate-500">Ticket promedio</p>
          <p class="mt-1 text-2xl font-bold tabular-nums">{{ formatearBs(datos.ticketPromedio) }}</p>
          <p class="text-xs text-slate-500">{{ datos.ordenesTotales }} orden(es) en total</p>
        </div>
        <div class="rounded-2xl bg-white p-5 shadow-sm">
          <p class="text-sm text-slate-500">Catálogo y comunidad</p>
          <p class="mt-1 text-2xl font-bold tabular-nums">{{ datos.productos }} <span class="text-base font-medium text-slate-500">productos</span></p>
          <p class="text-xs text-slate-500">{{ datos.estudiantes }} estudiante(s) registrado(s)</p>
        </div>
      </div>

      <div class="mt-6 grid gap-6 lg:grid-cols-3">
        <!-- ===== Ventas de los últimos 7 días (barras apiladas: pagado + pendiente) ===== -->
        <div class="rounded-2xl bg-white p-5 shadow-sm lg:col-span-2">
          <div class="mb-4 flex flex-wrap items-center justify-between gap-2">
            <h2 class="font-bold">Ventas de los últimos 7 días</h2>
            <div class="flex gap-3 text-xs text-slate-600">
              <span class="flex items-center gap-1"><span class="inline-block h-3 w-3 rounded-sm bg-emerald-500" /> Pagado</span>
              <span class="flex items-center gap-1"><span class="inline-block h-3 w-3 rounded-sm bg-amber-400" /> Pendiente</span>
            </div>
          </div>
          <div class="flex h-48 items-end gap-2 border-b border-slate-200">
            <div v-for="d in datos.ventasUltimos7Dias" :key="d.fecha" class="flex h-full flex-1 flex-col justify-end"
                 :title="`${estadisticasController.etiquetaDia(d.fecha)}: pagado ${formatearBs(d.pagado)}, pendiente ${formatearBs(d.pendiente)}, ${d.ordenes} orden(es)`">
              <div class="w-full rounded-t-sm bg-amber-400" :style="{ height: alto(d.pendiente) }" />
              <div class="w-full bg-emerald-500" :style="{ height: alto(d.pagado) }" />
            </div>
          </div>
          <div class="mt-2 flex gap-2">
            <span v-for="d in datos.ventasUltimos7Dias" :key="d.fecha" class="flex-1 text-center text-xs text-slate-500">
              {{ estadisticasController.etiquetaDia(d.fecha) }}
            </span>
          </div>
          <p v-if="datos.ventasUltimos7Dias.every(d => d.ordenes === 0)" class="mt-3 text-center text-sm text-slate-500">
            Aún no hay órdenes en los últimos 7 días.
          </p>
        </div>

        <!-- ===== Alertas de inventario ===== -->
        <div class="rounded-2xl bg-white p-5 shadow-sm">
          <h2 class="font-bold">Inventario</h2>
          <p class="mb-3 text-sm text-slate-500">
            <span :class="datos.agotados > 0 ? 'font-semibold text-red-600' : ''">{{ datos.agotados }} agotado(s)</span>
            · stock bajo: {{ datos.umbralStockBajo }} unidades o menos
          </p>
          <p v-if="datos.stockBajo.length === 0" class="rounded-lg bg-emerald-50 p-3 text-sm text-emerald-700">Todo el inventario está en niveles adecuados.</p>
          <ul v-else class="divide-y divide-slate-100">
            <li v-for="p in datos.stockBajo" :key="p.id" class="flex items-center justify-between gap-2 py-2">
              <div class="min-w-0">
                <p class="truncate text-sm font-medium">{{ p.nombre }}</p>
                <p class="text-xs text-slate-500">{{ p.codigo }}</p>
              </div>
              <span :class="['rounded-full px-2 py-0.5 text-xs font-bold tabular-nums', p.stock === 0 ? 'bg-red-100 text-red-700' : 'bg-amber-100 text-amber-800']">
                {{ p.stock === 0 ? 'Agotado' : `${p.stock} und.` }}
              </span>
            </li>
          </ul>
        </div>
      </div>

      <div class="mt-6 grid gap-6 lg:grid-cols-3">
        <!-- ===== Más vendidos ===== -->
        <div class="rounded-2xl bg-white p-5 shadow-sm">
          <h2 class="mb-3 font-bold">Productos más vendidos</h2>
          <p v-if="datos.masVendidos.length === 0" class="text-sm text-slate-500">Todavía no hay ventas registradas.</p>
          <ol v-else class="space-y-2">
            <li v-for="(p, i) in datos.masVendidos" :key="p.productoId" class="flex items-center gap-3">
              <span class="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-marca-50 text-sm font-bold text-marca-700">{{ i + 1 }}</span>
              <div class="min-w-0 flex-1">
                <p class="truncate text-sm font-medium">{{ p.nombre }}</p>
                <p class="text-xs text-slate-500 tabular-nums">{{ p.unidades }} und. · {{ formatearBs(p.ingresos) }}</p>
              </div>
            </li>
          </ol>
          <div class="mt-4 border-t border-slate-100 pt-3 text-sm">
            <p class="text-slate-500">Reseñas</p>
            <p class="font-semibold">
              {{ datos.resenas }} reseña(s)
              <template v-if="datos.calificacionPromedio !== null"> · <span class="text-amber-500">★</span> {{ datos.calificacionPromedio }} / 5</template>
            </p>
          </div>
        </div>

        <!-- ===== Órdenes recientes de todos los estudiantes ===== -->
        <div class="rounded-2xl bg-white p-5 shadow-sm lg:col-span-2">
          <h2 class="mb-3 font-bold">Órdenes recientes</h2>
          <p v-if="datos.ordenesRecientes.length === 0" class="text-sm text-slate-500">Aún no hay órdenes registradas.</p>
          <div v-else class="overflow-x-auto">
            <table class="w-full min-w-[520px] text-left text-sm">
              <thead class="text-xs uppercase text-slate-500">
                <tr><th class="py-2">Orden</th><th class="py-2">Estudiante</th><th class="py-2">Fecha</th><th class="py-2">Estado</th><th class="py-2 text-right">Total</th></tr>
              </thead>
              <tbody class="divide-y divide-slate-100">
                <tr v-for="o in datos.ordenesRecientes" :key="o.id">
                  <td class="py-2 font-medium">{{ o.numeroOrden }}</td>
                  <td class="py-2">{{ o.cliente }}</td>
                  <td class="py-2 text-slate-500">{{ formatearFecha(o.fecha) }}</td>
                  <td class="py-2"><span :class="['rounded-full px-2 py-0.5 text-xs font-bold', claseEstado(o.estado)]">{{ o.estadoEtiqueta }}</span></td>
                  <td class="py-2 text-right font-semibold tabular-nums">{{ formatearBs(o.total) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </template>
  </section>
</template>
