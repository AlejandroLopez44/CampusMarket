<!--
  =====================================================================
  CarritoView — Capa VIEW del módulo Carrito.
  ---------------------------------------------------------------------
  HU-08 Consultar Carrito: lista ítems, cantidades, subtotales y el total
  acumulado en Bs. Desde aquí se inicia HU-05 (Generar Compra).

  Funciona para el VISITANTE (carrito en su navegador) y para el ESTUDIANTE
  (carrito guardado en la BD). En ambos casos, precios y totales vienen
  del servidor. "Generar compra" exige sesión: App lleva al visitante a
  iniciar sesión y luego lo devuelve a la compra con su carrito intacto.
  Modificar cantidades / eliminar ítems queda fuera del Sprint 1.
  =====================================================================
-->
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import Icono from './Icono.vue'
import { carritoController } from '../control/CarritoClientController'
import { formatearBs } from '../control/ApiClient'
import type { Destino } from '../control/NavegacionController'

const emit = defineEmits<{ navegar: [destino: Destino] }>()

const carrito = carritoController.estado // Estado reactivo compartido con el ícono del encabezado
const cargando = ref(true)
const error = ref('')

/** Consulta el carrito (navegador o BD, según el modo) con precios vigentes. */
async function cargar(): Promise<void> {
  cargando.value = true
  error.value = ''
  try {
    await carritoController.consultar()
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    cargando.value = false
  }
}

onMounted(cargar)
</script>

<template>
  <section>
    <div class="mb-6">
      <p class="eyebrow">Tu compra</p>
      <h1 class="mt-1 text-3xl font-extrabold">Carrito de compras</h1>
    </div>

    <!-- Progreso del proceso de compra: el usuario sabe en qué paso está -->
    <ol class="mb-6 flex items-center gap-2 text-xs font-semibold sm:text-sm" aria-label="Pasos de la compra">
      <li class="flex items-center gap-2 text-marca-700"><span class="flex h-6 w-6 items-center justify-center rounded-full bg-marca-700 text-white">1</span>Carrito</li>
      <li class="h-px flex-1 bg-slate-300" aria-hidden="true" />
      <li class="flex items-center gap-2 text-slate-400"><span class="flex h-6 w-6 items-center justify-center rounded-full bg-slate-200">2</span>Resumen</li>
      <li class="h-px flex-1 bg-slate-300" aria-hidden="true" />
      <li class="flex items-center gap-2 text-slate-400"><span class="flex h-6 w-6 items-center justify-center rounded-full bg-slate-200">3</span>Factura</li>
    </ol>

    <div v-if="cargando" class="tarjeta space-y-4 p-6" aria-label="Cargando carrito">
      <div v-for="n in 2" :key="n" class="flex gap-4"><div class="h-20 w-20 animate-pulse rounded-xl bg-slate-200" /><div class="flex-1 space-y-2"><div class="h-4 w-1/2 animate-pulse rounded bg-slate-200" /><div class="h-3 w-1/3 animate-pulse rounded bg-slate-200" /></div></div>
    </div>

    <div v-else-if="error" class="tarjeta flex flex-col items-center gap-3 p-8 text-center">
      <span class="flex h-12 w-12 items-center justify-center rounded-full bg-red-50 text-red-600"><Icono nombre="alerta" :tamano="24" /></span>
      <p class="font-semibold text-red-700">{{ error }}</p>
      <button class="btn-secundario" @click="cargar"><Icono nombre="actualizar" :tamano="16" /> Reintentar</button>
    </div>

    <!-- Estado vacío con una invitación clara a seguir -->
    <div v-else-if="carrito.vacio" class="tarjeta flex flex-col items-center gap-3 px-6 py-14 text-center">
      <span class="flex h-16 w-16 items-center justify-center rounded-full bg-marca-50 text-marca-600"><Icono nombre="carrito" :tamano="30" /></span>
      <p class="text-xl font-bold">Tu carrito está vacío</p>
      <p class="max-w-sm text-slate-500">Agrega cuadernos, calculadoras y otros insumos desde el catálogo.</p>
      <button class="btn-primario mt-2" @click="emit('navegar', 'productos')"><Icono nombre="catalogo" :tamano="18" /> Ir al catálogo</button>
    </div>

    <div v-else class="grid gap-6 lg:grid-cols-[1fr_22rem] lg:items-start">
      <!-- Lista de productos -->
      <div class="space-y-3">
        <!-- Aviso al visitante: el carrito vive en este navegador hasta que inicie sesión -->
        <p v-if="carritoController.esInvitado" class="flex items-start gap-3 rounded-2xl border border-marca-100 bg-marca-50 p-4 text-sm text-marca-800">
          <Icono nombre="info" :tamano="20" />
          <span>Estás comprando como visitante: tu carrito se guarda en este navegador. Para generar la compra deberás iniciar sesión o registrarte.</span>
        </p>

        <ul class="tarjeta divide-y divide-slate-100">
          <li v-for="i in carrito.items" :key="i.productoId" class="flex gap-4 p-4">
            <img :src="i.imagenUrl" :alt="i.nombre" class="h-20 w-20 shrink-0 rounded-xl object-cover" />
            <div class="min-w-0 flex-1">
              <div class="flex flex-wrap items-start justify-between gap-2">
                <h2 class="font-sans font-bold leading-snug">{{ i.nombre }}</h2>
                <p class="font-titulo font-bold text-marca-800">{{ formatearBs(i.subtotal) }}</p>
              </div>
              <p class="mt-1 text-sm text-slate-500">{{ formatearBs(i.precioUnitario) }} c/u · <span class="font-semibold text-tinta">Cantidad: {{ i.cantidad }}</span></p>
              <!-- El stock pudo bajar desde que se agregó: se avisa antes de comprar -->
              <p v-if="i.cantidad > i.stockActual" class="mt-2 flex items-start gap-2 rounded-lg bg-red-50 p-2 text-sm font-semibold text-red-700">
                <Icono nombre="alerta" :tamano="16" /> Solo quedan {{ i.stockActual }} unidad(es): la compra no podrá completarse con esta cantidad.
              </p>
            </div>
          </li>
        </ul>
      </div>

      <!-- Resumen (fijo al costado en escritorio) -->
      <aside class="tarjeta p-5 lg:sticky lg:top-24">
        <h2 class="text-lg font-bold">Resumen</h2>
        <dl class="mt-4 space-y-2 text-sm">
          <div class="flex justify-between"><dt class="text-slate-500">Artículos</dt><dd class="font-semibold tabular-nums">{{ carrito.cantidadTotal }}</dd></div>
          <div class="flex justify-between"><dt class="text-slate-500">Productos distintos</dt><dd class="font-semibold tabular-nums">{{ carrito.items.length }}</dd></div>
        </dl>
        <div class="mt-4 flex items-end justify-between border-t border-slate-200 pt-4">
          <span class="font-semibold">Total</span>
          <span class="whitespace-nowrap font-titulo text-2xl font-extrabold text-marca-800">{{ formatearBs(carrito.total) }}</span>
        </div>
        <div class="mt-5 flex flex-col gap-2">
          <button class="btn-primario !py-3" @click="emit('navegar', 'compra')">
            {{ carritoController.esInvitado ? 'Iniciar sesión y generar compra' : 'Generar compra' }} <Icono nombre="derecha" :tamano="18" />
          </button>
          <button class="btn-secundario" @click="emit('navegar', 'productos')"><Icono nombre="izquierda" :tamano="16" /> Seguir comprando</button>
        </div>
        <p class="mt-4 flex items-center gap-2 text-xs text-slate-500"><Icono nombre="escudo" :tamano="16" /> El stock se verifica de nuevo al generar la orden.</p>
      </aside>
    </div>
  </section>
</template>
