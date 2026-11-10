<!--
  =====================================================================
  App — Vista raíz / Dashboard (capa VIEW).
  ---------------------------------------------------------------------
  Responsabilidad única (SRP): el "marco" de la aplicación: encabezado
  con navegación responsiva, ícono del carrito, Dashboard (menú principal)
  y conmutación entre las vistas de cada módulo.

  Navegación por ROL:
    • Visitante:     Inicio, Catálogo, Carrito (en el navegador), Ingresar.
    • Estudiante:    Inicio, Catálogo, Carrito, Mis compras, Mi perfil.
    • Administrador: Panel (estadísticas), Catálogo (gestión), Mi perfil.
      El admin NO compra: no ve carrito, compras ni formulario de reseñas.

  Cada vista emite `navegar(destino)`; App aplica las reglas de acceso y
  decide qué mostrar. Así ninguna vista conoce a las demás (bajo acoplamiento).
  =====================================================================
-->
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import ProductoView from './ProductoView.vue'
import UsuarioView from './UsuarioView.vue'
import CarritoView from './CarritoView.vue'
import CompraView from './CompraView.vue'
import EstadisticasView from './EstadisticasView.vue'
import DialogoView from './DialogoView.vue'
import { usuarioController } from '../control/UsuarioClientController'
import { carritoController } from '../control/CarritoClientController'
import { formatearBs } from '../control/ApiClient'
import type { Destino } from '../control/NavegacionController'

const vista = ref<Destino>('inicio')
const menuAbierto = ref(false)
/** Destino que el visitante intentó abrir antes de iniciar sesión (p. ej. "compra"). */
const destinoPendiente = ref<Destino | null>(null)
/** Aviso tras unir el carrito del navegador con el de la cuenta. */
const avisoFusion = ref<string | null>(null)

const sesion = usuarioController.estado
const carrito = carritoController.estado
const autenticado = computed(() => sesion.usuario !== null)
const esAdmin = computed(() => sesion.usuario?.rol === 'ADMINISTRADOR')

/** Vistas exclusivas del estudiante (comprar e historial). */
const soloEstudiante: Destino[] = ['compra', 'compras']
/** Vistas que el administrador no usa (no compra). */
const prohibidasAdmin: Destino[] = ['carrito', 'compra', 'compras']

/**
 * Cambia de vista aplicando las reglas de acceso:
 *  • Visitante que pide una vista con sesión → se guarda el destino y va a "Mi cuenta".
 *  • Administrador que pide una vista de compras → vuelve a su Panel.
 * @param destino vista solicitada por el usuario o por otra vista
 */
function navegar(destino: Destino): void {
  let final = destino
  if (!autenticado.value && (soloEstudiante.includes(destino) || destino === 'perfil')) {
    destinoPendiente.value = destino
    final = 'cuenta'
  } else if (esAdmin.value && prohibidasAdmin.includes(destino)) {
    final = 'inicio'
  }
  vista.value = final
  menuAbierto.value = false
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

/**
 * Configura el carrito según el rol:
 *  • Estudiante: une el carrito del navegador con el de su cuenta y usa el guardado en BD.
 *  • Administrador: carrito deshabilitado.
 *  • Visitante: carrito del navegador.
 */
async function prepararCarrito(): Promise<void> {
  if (!autenticado.value) return carritoController.usarModo('invitado')
  if (esAdmin.value) return carritoController.usarModo('deshabilitado')
  await carritoController.usarModo('estudiante')
  try {
    avisoFusion.value = await carritoController.fusionarCarritoInvitado()
  } catch (e) {
    avisoFusion.value = `No se pudo pasar tu carrito a tu cuenta: ${(e as Error).message}`
  }
}

/** Tras iniciar sesión: prepara el carrito y retoma el destino pendiente (si aplica a su rol). */
async function alIniciarSesion(): Promise<void> {
  await prepararCarrito()
  const pendiente = destinoPendiente.value
  destinoPendiente.value = null
  navegar(pendiente ?? 'inicio')
}

/** Cierra la sesión: el carrito vuelve a ser el del navegador (vacío). */
async function cerrarSesion(): Promise<void> {
  await usuarioController.cerrarSesion().catch(() => undefined)
  await carritoController.usarModo('invitado')
  navegar('inicio')
}

/** Al cargar la página: recupera la sesión si la cookie sigue vigente y prepara el carrito. */
onMounted(async () => {
  await usuarioController.restaurarSesion()
  await prepararCarrito()
})

/** Opciones del menú según el rol (datos, no lógica: añadir una opción no modifica la plantilla). */
const opcionesMenu = computed<{ destino: Destino; texto: string }[]>(() => {
  if (esAdmin.value) {
    return [
      { destino: 'inicio', texto: 'Panel' },
      { destino: 'productos', texto: 'Catálogo' },
      { destino: 'perfil', texto: 'Mi perfil' },
    ]
  }
  return [
    { destino: 'inicio', texto: 'Inicio' },
    { destino: 'productos', texto: 'Catálogo' },
    ...(autenticado.value
      ? [{ destino: 'compras' as Destino, texto: 'Mis compras' }, { destino: 'perfil' as Destino, texto: 'Mi perfil' }]
      : []),
  ]
})

/** Tarjetas del Dashboard del estudiante/visitante. */
const tarjetas = computed(() => [
  { destino: 'productos' as Destino, icono: '🛍️', titulo: 'Catálogo', texto: 'Busca insumos y consulta precios en Bs.' },
  { destino: 'carrito' as Destino, icono: '🛒', titulo: 'Mi carrito', texto: `${carrito.cantidadTotal} unidad(es) en tu carrito` },
  { destino: 'compras' as Destino, icono: '📦', titulo: 'Mis compras', texto: autenticado.value ? 'Historial y facturas de tus órdenes' : 'Inicia sesión para ver tus compras' },
  { destino: 'perfil' as Destino, icono: '👤', titulo: 'Mi perfil', texto: autenticado.value ? 'Tus datos de la cuenta UCAB' : 'Inicia sesión para ver tu perfil' },
])
</script>

<template>
  <div class="flex min-h-screen flex-col">
    <!-- ===== Encabezado responsivo ===== -->
    <header class="sticky top-0 z-40 bg-white shadow-sm">
      <div class="mx-auto flex max-w-6xl items-center justify-between gap-3 px-4 py-3">
        <button class="text-xl font-extrabold text-marca-600" @click="navegar('inicio')">
          CampusMarket<span v-if="esAdmin" class="ml-2 rounded-full bg-marca-50 px-2 py-0.5 align-middle text-xs font-bold text-marca-700">Admin</span>
        </button>

        <div class="flex items-center gap-2">
          <!-- Ícono del carrito (visitante y estudiante). Criterio 1 de "Agregar al carrito":
               se actualiza al instante con la cantidad de artículos y el subtotal acumulado en Bs. -->
          <button v-if="!esAdmin" class="flex items-center gap-2 rounded-lg p-2 hover:bg-slate-100"
                  :aria-label="`Ver carrito: ${carrito.cantidadTotal} artículo(s), ${formatearBs(carrito.total)}`" @click="navegar('carrito')">
            <span class="relative">
              <span class="text-2xl">🛒</span>
              <span v-if="carrito.cantidadTotal > 0"
                    class="absolute -right-2 -top-1 rounded-full bg-red-600 px-1.5 text-xs font-bold text-white">
                {{ carrito.cantidadTotal }}
              </span>
            </span>
            <span v-if="carrito.cantidadTotal > 0" class="text-sm font-bold tabular-nums text-emerald-700">{{ formatearBs(carrito.total) }}</span>
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
      <!-- Panel del administrador (estadísticas del negocio) -->
      <EstadisticasView v-if="vista === 'inicio' && esAdmin" @navegar="navegar" />

      <!-- Dashboard / Menú principal del estudiante o visitante -->
      <section v-else-if="vista === 'inicio'">
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
      <UsuarioView v-else modo="acceso" :motivo="destinoPendiente" @navegar="navegar" @sesion-iniciada="alIniciarSesion" />
    </main>

    <footer class="py-4 text-center text-xs text-slate-400">CampusMarket · UCAB · Gran Caracas</footer>

    <!-- Resultado de unir el carrito del navegador con el de la cuenta -->
    <DialogoView v-if="avisoFusion" titulo="Carrito actualizado" :mensaje="avisoFusion" tipo="info" @accion="avisoFusion = null" />
  </div>
</template>
