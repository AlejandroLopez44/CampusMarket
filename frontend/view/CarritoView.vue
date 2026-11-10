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
  <section class="mx-auto max-w-3xl rounded-2xl bg-white p-4 shadow-sm sm:p-6">
    <h1 class="mb-4 text-2xl font-bold">Tu carrito de compras</h1>

    <p v-if="cargando" class="py-10 text-center text-slate-500">Cargando carrito…</p>

    <div v-else-if="error" class="rounded-xl bg-red-50 p-6 text-center text-red-700">
      {{ error }} <button class="ml-2 font-semibold underline" @click="cargar">Reintentar</button>
    </div>

    <div v-else-if="carrito.vacio" class="rounded-xl bg-slate-100 p-8 text-center text-slate-600">
      <p>Tu carrito está vacío.</p>
      <button class="mt-4 rounded-lg bg-marca-600 px-4 py-2 font-semibold text-white hover:bg-marca-700" @click="emit('navegar', 'productos')">Ir al catálogo</button>
    </div>

    <template v-else>
      <!-- Aviso al visitante: el carrito vive en este navegador hasta que inicie sesión -->
      <p v-if="carritoController.esInvitado" class="mb-3 rounded-lg bg-marca-50 p-3 text-sm text-marca-700">
        Estás comprando como visitante: tu carrito se guarda en este navegador. Para generar la compra deberás iniciar sesión o registrarte.
      </p>

      <ul class="divide-y divide-slate-100">
        <li v-for="i in carrito.items" :key="i.productoId" class="flex flex-wrap items-center gap-3 py-4 sm:flex-nowrap">
          <img :src="i.imagenUrl" :alt="i.nombre" class="h-16 w-16 rounded-lg object-cover" />
          <div class="min-w-0 flex-1">
            <h2 class="font-semibold">{{ i.nombre }}</h2>
            <p class="text-sm text-slate-500">{{ formatearBs(i.precioUnitario) }} c/u · Cantidad: {{ i.cantidad }}</p>
            <!-- El stock pudo bajar desde que se agregó: se avisa antes de comprar -->
            <p v-if="i.cantidad > i.stockActual" class="text-sm font-semibold text-red-600">
              Solo quedan {{ i.stockActual }} unidad(es): la compra no podrá completarse con esta cantidad.
            </p>
          </div>
          <p class="w-full text-right font-bold sm:w-auto">{{ formatearBs(i.subtotal) }}</p>
        </li>
      </ul>

      <div class="mt-2 flex items-center justify-between border-t-2 border-slate-200 pt-4">
        <span class="text-lg font-semibold">Total ({{ carrito.cantidadTotal }} unidades)</span>
        <span class="text-2xl font-bold text-emerald-600">{{ formatearBs(carrito.total) }}</span>
      </div>

      <div class="mt-6 flex flex-col gap-2 sm:flex-row sm:justify-end">
        <button class="rounded-lg bg-slate-200 px-4 py-2 font-semibold hover:bg-slate-300" @click="emit('navegar', 'productos')">Seguir comprando</button>
        <button class="rounded-lg bg-marca-600 px-6 py-2 font-semibold text-white hover:bg-marca-700" @click="emit('navegar', 'compra')">
          {{ carritoController.esInvitado ? 'Iniciar sesión y generar compra' : 'Generar compra' }}
        </button>
      </div>
    </template>
  </section>
</template>
