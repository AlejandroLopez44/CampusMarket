<!--
  =====================================================================
  App — Vista raíz / Dashboard (capa VIEW).
  ---------------------------------------------------------------------
  Responsabilidad única (SRP): el "marco" de la aplicación: encabezado
  con navegación responsiva, ícono del carrito, Dashboard (menú principal)
  y conmutación entre las vistas de cada módulo. No llama a la API salvo
  para restaurar la sesión y el carrito al cargar.

  Navegación: cada vista emite `navegar(destino)`; App decide qué mostrar.
  Así ninguna vista conoce a las demás (bajo acoplamiento).
  =====================================================================
-->
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import ProductoView from './ProductoView.vue'
import UsuarioView from './UsuarioView.vue'
import CarritoView from './CarritoView.vue'
import CompraView from './CompraView.vue'
import { usuarioController } from '../control/UsuarioClientController'
import { carritoController } from '../control/CarritoClientController'
import type { Destino } from '../control/NavegacionController'

const vista = ref<Destino>('inicio')
const menuAbierto = ref(false)

const sesion = usuarioController.estado
const carrito = carritoController.estado
const autenticado = computed(() => sesion.usuario !== null)

/** Vistas que exigen sesión: si no la hay, se redirige a "Mi cuenta". */
const requierenSesion: Destino[] = ['carrito', 'compra', 'compras', 'perfil']

/**
 * Cambia de vista aplicando la regla de sesión.
 * @param destino vista solicitada por el usuario o por otra vista
 */
function navegar(destino: Destino): void {
  vista.value = requierenSesion.includes(destino) && !autenticado.value ? 'cuenta' : destino
  menuAbierto.value = false
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

/** Tras iniciar sesión: sincroniza el carrito desde la BD y vuelve al Dashboard. */
async function alIniciarSesion(): Promise<void> {
  await carritoController.consultar().catch(() => carritoController.limpiar())
  navegar('inicio')
}

/** Cierra la sesión y limpia el estado local. */
async function cerrarSesion(): Promise<void> {
  await usuarioController.cerrarSesion().catch(() => undefined)
  carritoController.limpiar()
  navegar('inicio')
}

/** Al cargar la página: si la cookie de sesión sigue vigente, se recupera el usuario y su carrito. */
onMounted(async () => {
  await usuarioController.restaurarSesion()
  if (autenticado.value) await carritoController.consultar().catch(() => undefined)
})

/** Opciones del menú (datos, no lógica: añadir una opción no modifica la plantilla). */
const opcionesMenu = computed(() => [
  { destino: 'inicio' as Destino, texto: 'Inicio' },
  { destino: 'productos' as Destino, texto: 'Catálogo' },
  ...(autenticado.value ? [{ destino: 'compras' as Destino, texto: 'Mis compras' }, { destino: 'perfil' as Destino, texto: 'Mi perfil' }] : []),
])

/** Tarjetas del Dashboard (menú principal). */
const tarjetas = computed(() => [
  { destino: 'productos' as Destino, icono: '🛍️', titulo: 'Catálogo', texto: 'Busca insumos y consulta precios en Bs.' },
  { destino: 'carrito' as Destino, icono: '🛒', titulo: 'Mi carrito', texto: `${carrito.cantidadTotal} unidad(es) en tu carrito` },
  { destino: 'compras' as Destino, icono: '📦', titulo: 'Mis compras', texto: 'Historial de tus órdenes' },
  { destino: 'perfil' as Destino, icono: '👤', titulo: 'Mi perfil', texto: 'Tus datos de la cuenta UCAB' },
])
</script>

<template>
  <div class="flex min-h-screen flex-col">
    <!-- ===== Encabezado responsivo ===== -->
    <header class="sticky top-0 z-40 bg-white shadow-sm">
      <div class="mx-auto flex max-w-6xl items-center justify-between gap-3 px-4 py-3">
        <button class="text-xl font-extrabold text-marca-600" @click="navegar('inicio')">CampusMarket</button>

        <div class="flex items-center gap-2">
          <!-- Ícono del carrito: refleja cantidadTotal del estado compartido (HU-04/HU-08) -->
          <button class="relative rounded-lg p-2 hover:bg-slate-100" aria-label="Ver carrito" @click="navegar('carrito')">
            <span class="text-2xl">🛒</span>
            <span v-if="carrito.cantidadTotal > 0"
                  class="absolute -right-1 -top-1 rounded-full bg-red-600 px-1.5 text-xs font-bold text-white">
              {{ carrito.cantidadTotal }}
            </span>
          </button>
          <!-- Botón hamburguesa solo en móvil -->
          <button class="rounded-lg p-2 hover:bg-slate-100 md:hidden" aria-label="Abrir menú" @click="menuAbierto = !menuAbierto">☰</button>
        </div>
      </div>

      <nav :class="['mx-auto max-w-6xl px-4 pb-3 md:flex md:items-center md:gap-2', menuAbierto ? 'block' : 'hidden md:flex']">
        <button v-for="o in opcionesMenu" :key="o.destino"
                :class="['block w-full rounded-lg px-3 py-2 text-left font-medium md:w-auto',
                         vista === o.destino ? 'bg-marca-50 text-marca-700' : 'hover:bg-slate-100']"
                @click="navegar(o.destino)">{{ o.texto }}</button>
        <span class="hidden flex-1 md:block" />
        <template v-if="autenticado">
          <span class="block px-3 py-2 text-sm text-slate-500">Hola, {{ sesion.usuario?.nombre }}</span>
          <button class="block w-full rounded-lg px-3 py-2 text-left font-medium text-red-600 hover:bg-red-50 md:w-auto" @click="cerrarSesion">Cerrar sesión</button>
        </template>
        <button v-else class="block w-full rounded-lg bg-marca-600 px-3 py-2 text-left font-semibold text-white md:w-auto" @click="navegar('cuenta')">Ingresar / Registrarse</button>
      </nav>
    </header>

    <!-- ===== Contenido ===== -->
    <main class="mx-auto w-full max-w-6xl flex-1 px-4 py-6">
      <!-- Dashboard / Menú principal -->
      <section v-if="vista === 'inicio'">
        <h1 class="text-2xl font-bold">Bienvenido a CampusMarket</h1>
        <p class="mb-6 text-slate-500">Insumos estudiantiles para la comunidad UCAB · Precios en bolívares</p>
        <div class="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <button v-for="t in tarjetas" :key="t.destino" class="rounded-2xl bg-white p-5 text-left shadow-sm transition hover:-translate-y-1 hover:shadow-md" @click="navegar(t.destino)">
            <div class="text-3xl">{{ t.icono }}</div>
            <h2 class="mt-2 font-bold">{{ t.titulo }}</h2>
            <p class="text-sm text-slate-500">{{ t.texto }}</p>
          </button>
        </div>
      </section>

      <ProductoView v-else-if="vista === 'productos'" @navegar="navegar" />
      <CarritoView v-else-if="vista === 'carrito'" @navegar="navegar" />
      <CompraView v-else-if="vista === 'compra'" modo="generar" @navegar="navegar" />
      <CompraView v-else-if="vista === 'compras'" modo="historial" @navegar="navegar" />
      <UsuarioView v-else-if="vista === 'perfil'" modo="perfil" @navegar="navegar" />
      <UsuarioView v-else modo="acceso" @navegar="navegar" @sesion-iniciada="alIniciarSesion" />
    </main>

    <footer class="py-4 text-center text-xs text-slate-400">CampusMarket · UCAB · Gran Caracas</footer>
  </div>
</template>
