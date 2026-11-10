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
import Icono from './Icono.vue'
import { usuarioController } from '../control/UsuarioClientController'
import { carritoController } from '../control/CarritoClientController'
import { productoController, type Producto } from '../control/ProductoClientController'
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

// ------------------------------------------------------------------
// Inicio (estudiante / visitante): buscador, categorías y destacados
// ------------------------------------------------------------------
/** Productos destacados en el inicio (los primeros con stock). */
const destacados = ref<Producto[]>([])
/** Texto escrito en el buscador del inicio. */
const busquedaInicio = ref('')
/** Filtro con el que se abrirá el catálogo (desde el buscador o una categoría del inicio). */
const filtroCatalogo = ref({ texto: '', categoria: '' })
/** Cambia en cada búsqueda para que el catálogo se vuelva a montar con el filtro nuevo. */
const claveCatalogo = ref(0)

/** Abre el catálogo ya filtrado (búsqueda o categoría elegida en el inicio). */
function abrirCatalogo(texto = '', categoria = ''): void {
  filtroCatalogo.value = { texto, categoria }
  claveCatalogo.value++
  navegar('productos')
}

/** Carga los destacados (falla en silencio: el inicio sigue funcionando sin ellos). */
async function cargarDestacados(): Promise<void> {
  try {
    destacados.value = (await productoController.listar()).filter(p => p.stock > 0).slice(0, 4)
  } catch {
    destacados.value = []
  }
}

/** Al cargar la página: recupera la sesión si la cookie sigue vigente, prepara el carrito y los destacados. */
onMounted(async () => {
  await usuarioController.restaurarSesion()
  await prepararCarrito()
  if (!esAdmin.value) cargarDestacados()
})

/** Opciones del menú según el rol (datos, no lógica: añadir una opción no modifica la plantilla). */
const opcionesMenu = computed<{ destino: Destino; texto: string; icono: string }[]>(() => {
  if (esAdmin.value) {
    return [
      { destino: 'inicio', texto: 'Panel', icono: 'panel' },
      { destino: 'productos', texto: 'Catálogo', icono: 'catalogo' },
      { destino: 'perfil', texto: 'Mi perfil', icono: 'perfil' },
    ]
  }
  return [
    { destino: 'inicio', texto: 'Inicio', icono: 'inicio' },
    { destino: 'productos', texto: 'Catálogo', icono: 'catalogo' },
    ...(autenticado.value
      ? [{ destino: 'compras' as Destino, texto: 'Mis compras', icono: 'compras' }, { destino: 'perfil' as Destino, texto: 'Mi perfil', icono: 'perfil' }]
      : []),
  ]
})

/** Accesos rápidos del inicio del estudiante/visitante. */
const tarjetas = computed(() => [
  { destino: 'productos' as Destino, icono: 'catalogo', titulo: 'Catálogo', texto: 'Explora insumos con precios en Bs.' },
  { destino: 'carrito' as Destino, icono: 'carrito', titulo: 'Mi carrito', texto: carrito.cantidadTotal > 0 ? `${carrito.cantidadTotal} artículo(s) · ${formatearBs(carrito.total)}` : 'Aún está vacío' },
  { destino: 'compras' as Destino, icono: 'factura', titulo: 'Mis compras', texto: autenticado.value ? 'Historial y facturas' : 'Inicia sesión para verlas' },
  { destino: 'perfil' as Destino, icono: 'perfil', titulo: 'Mi perfil', texto: autenticado.value ? 'Tu cuenta UCAB' : 'Inicia sesión para verlo' },
])

/** Inicial del nombre para el avatar del encabezado. */
const inicial = computed(() => sesion.usuario?.nombre.trim().charAt(0).toUpperCase() ?? '')
/** Primer nombre para el saludo. */
const primerNombre = computed(() => sesion.usuario?.nombre.trim().split(/\s+/)[0] ?? '')
</script>

<template>
  <div class="flex min-h-screen flex-col">
    <!-- ===== Encabezado responsivo (fijo arriba, con efecto vidrio) ===== -->
    <header class="sticky top-0 z-40 border-b border-slate-200/70 bg-white/85 backdrop-blur-md">
      <div class="mx-auto flex max-w-6xl items-center gap-3 px-4 py-3">
        <!-- Marca -->
        <button class="flex items-center gap-2.5" aria-label="Ir al inicio" @click="navegar('inicio')">
          <span class="flex h-9 w-9 items-center justify-center rounded-xl bg-marca-800 font-titulo text-sm font-extrabold text-acento-400 shadow-suave">CM</span>
          <span class="font-titulo text-lg font-extrabold text-marca-900">Campus<span class="text-marca-600">Market</span></span>
          <span v-if="esAdmin" class="insignia hidden bg-acento-100 text-amber-800 sm:inline-flex">Administrador</span>
        </button>

        <!-- Navegación de escritorio -->
        <nav class="ml-4 hidden items-center gap-1 md:flex" aria-label="Principal">
          <button v-for="o in opcionesMenu" :key="o.destino"
                  :class="['flex items-center gap-2 rounded-xl px-3 py-2 text-sm font-semibold transition',
                           vista === o.destino ? 'bg-marca-50 text-marca-700' : 'text-slate-600 hover:bg-slate-100 hover:text-tinta']"
                  :aria-current="vista === o.destino ? 'page' : undefined"
                  @click="navegar(o.destino)">
            <Icono :nombre="o.icono" :tamano="18" /> {{ o.texto }}
          </button>
        </nav>

        <div class="ml-auto flex items-center gap-2">
          <!-- Ícono del carrito (visitante y estudiante). Criterio 1 de "Agregar al carrito":
               se actualiza al instante con la cantidad de artículos y el subtotal acumulado en Bs. -->
          <button v-if="!esAdmin"
                  :class="['flex items-center gap-2 rounded-xl px-2.5 py-2 transition sm:px-3',
                           carrito.cantidadTotal > 0 ? 'bg-marca-800 text-white shadow-suave hover:bg-marca-900' : 'text-slate-600 hover:bg-slate-100']"
                  :aria-label="`Ver carrito: ${carrito.cantidadTotal} artículo(s), ${formatearBs(carrito.total)}`" @click="navegar('carrito')">
            <span class="relative">
              <Icono nombre="carrito" :tamano="22" />
              <span v-if="carrito.cantidadTotal > 0"
                    class="absolute -right-2 -top-2 flex h-5 min-w-5 items-center justify-center rounded-full bg-acento-400 px-1 text-[11px] font-extrabold text-tinta">
                {{ carrito.cantidadTotal }}
              </span>
            </span>
            <span v-if="carrito.cantidadTotal > 0" class="whitespace-nowrap text-xs font-bold tabular-nums sm:text-sm">{{ formatearBs(carrito.total) }}</span>
          </button>

          <!-- Cuenta (escritorio) -->
          <template v-if="autenticado">
            <div class="hidden items-center gap-2 md:flex">
              <span class="flex h-9 w-9 items-center justify-center rounded-full bg-marca-100 text-sm font-bold text-marca-800" :title="sesion.usuario?.nombre">{{ inicial }}</span>
              <span class="hidden text-sm font-medium text-slate-600 lg:inline">Hola, {{ primerNombre }}</span>
              <button class="btn-fantasma !px-2.5 text-slate-500 hover:!text-red-600" title="Cerrar sesión" aria-label="Cerrar sesión" @click="cerrarSesion">
                <Icono nombre="salir" :tamano="18" />
              </button>
            </div>
          </template>
          <button v-else class="btn-primario hidden md:inline-flex" @click="navegar('cuenta')">Ingresar / Registrarse</button>

          <!-- Botón hamburguesa (solo móvil) -->
          <button class="rounded-xl p-2 text-slate-600 hover:bg-slate-100 md:hidden" :aria-expanded="menuAbierto" aria-label="Abrir menú" @click="menuAbierto = !menuAbierto">
            <Icono :nombre="menuAbierto ? 'cerrar' : 'menu'" :tamano="22" />
          </button>
        </div>
      </div>

      <!-- Menú desplegable de móvil -->
      <nav v-if="menuAbierto" class="animar-entrada border-t border-slate-200/70 px-4 pb-4 pt-2 md:hidden" aria-label="Principal (móvil)">
        <p v-if="autenticado" class="flex items-center gap-2 px-2 py-2 text-sm text-slate-600">
          <span class="flex h-8 w-8 items-center justify-center rounded-full bg-marca-100 text-sm font-bold text-marca-800">{{ inicial }}</span>
          Hola, {{ primerNombre }}
        </p>
        <button v-for="o in opcionesMenu" :key="o.destino"
                :class="['flex w-full items-center gap-3 rounded-xl px-3 py-3 text-left font-semibold',
                         vista === o.destino ? 'bg-marca-50 text-marca-700' : 'text-slate-700 hover:bg-slate-100']"
                @click="navegar(o.destino)">
          <Icono :nombre="o.icono" /> {{ o.texto }}
        </button>
        <button v-if="autenticado" class="mt-1 flex w-full items-center gap-3 rounded-xl px-3 py-3 text-left font-semibold text-red-600 hover:bg-red-50" @click="cerrarSesion">
          <Icono nombre="salir" /> Cerrar sesión
        </button>
        <button v-else class="btn-primario mt-2 w-full" @click="navegar('cuenta')">Ingresar / Registrarse</button>
      </nav>
    </header>

    <!-- ===== Contenido ===== -->
    <main class="mx-auto w-full max-w-6xl flex-1 px-4 py-6 sm:py-8">
      <!-- Panel del administrador (estadísticas del negocio) -->
      <EstadisticasView v-if="vista === 'inicio' && esAdmin" @navegar="navegar" />

      <!-- Inicio del estudiante o visitante -->
      <section v-else-if="vista === 'inicio'" class="space-y-8">
        <!-- Portada: saludo + buscador (lo más usado, al alcance inmediato) -->
        <div class="relative overflow-hidden rounded-3xl bg-marca-900 px-6 py-10 text-white shadow-elevada sm:px-10 sm:py-14">
          <!-- Formas decorativas -->
          <div class="pointer-events-none absolute -right-16 -top-20 h-72 w-72 rounded-full bg-marca-500/40 blur-3xl" />
          <div class="pointer-events-none absolute -bottom-24 right-1/3 h-64 w-64 rounded-full bg-acento-400/25 blur-3xl" />
          <div class="relative max-w-2xl">
            <p class="eyebrow !text-acento-300">Comunidad UCAB · Gran Caracas</p>
            <h1 class="mt-3 text-3xl font-extrabold leading-tight sm:text-5xl">
              {{ autenticado ? `Hola, ${primerNombre}.` : 'Todo para tu semestre,' }}
              <span class="text-acento-400">{{ autenticado ? '¿Qué necesitas hoy?' : 'en un solo lugar.' }}</span>
            </h1>
            <p class="mt-3 max-w-xl text-base text-marca-100 sm:text-lg">Cuadernos, calculadoras, libros y más, con precios en bolívares y stock en tiempo real.</p>
            <form class="mt-6 flex flex-col gap-2 sm:flex-row" role="search" @submit.prevent="abrirCatalogo(busquedaInicio)">
              <label class="sr-only" for="buscar-inicio">Buscar productos</label>
              <div class="relative flex-1">
                <span class="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400"><Icono nombre="buscar" /></span>
                <input id="buscar-inicio" v-model="busquedaInicio" type="search" placeholder="¿Qué buscas? Ej. calculadora"
                       class="w-full rounded-xl border-0 bg-white py-3.5 pl-11 pr-4 text-tinta shadow-suave outline-none placeholder:text-slate-400 focus:ring-4 focus:ring-acento-300" />
              </div>
              <button type="submit" class="btn-acento !py-3.5 !text-base">Buscar</button>
            </form>
          </div>
        </div>

        <!-- Categorías: acceso directo al catálogo filtrado -->
        <div>
          <h2 class="mb-3 text-lg font-bold">Explora por categoría</h2>
          <div class="flex flex-wrap gap-2">
            <button v-for="c in productoController.categorias" :key="c"
                    class="rounded-full border border-slate-200 bg-white px-4 py-2 text-sm font-semibold text-slate-700 shadow-suave transition hover:-translate-y-0.5 hover:border-marca-300 hover:text-marca-700"
                    @click="abrirCatalogo('', c)">{{ c }}</button>
          </div>
        </div>

        <!-- Destacados -->
        <div v-if="destacados.length">
          <div class="mb-3 flex items-end justify-between gap-2">
            <h2 class="text-lg font-bold">Disponibles ahora</h2>
            <button class="btn-fantasma !px-2" @click="abrirCatalogo()">Ver todo <Icono nombre="derecha" :tamano="16" /></button>
          </div>
          <div class="grid grid-cols-2 gap-3 sm:gap-4 lg:grid-cols-4">
            <button v-for="p in destacados" :key="p.id" class="tarjeta group overflow-hidden text-left transition hover:-translate-y-1 hover:shadow-elevada" @click="abrirCatalogo(p.nombre)">
              <div class="aspect-square overflow-hidden bg-slate-100">
                <img :src="p.imagenUrl" :alt="p.nombre" loading="lazy" class="h-full w-full object-cover transition duration-300 group-hover:scale-105" />
              </div>
              <div class="p-3">
                <p class="truncate text-sm font-semibold">{{ p.nombre }}</p>
                <p class="font-titulo text-base font-bold text-marca-800">{{ formatearBs(p.precio) }}</p>
              </div>
            </button>
          </div>
        </div>

        <!-- Accesos rápidos -->
        <div class="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <button v-for="t in tarjetas" :key="t.destino" class="tarjeta flex items-center gap-4 p-4 text-left transition hover:-translate-y-0.5 hover:shadow-elevada" @click="navegar(t.destino)">
            <span class="flex h-11 w-11 items-center justify-center rounded-xl bg-marca-50 text-marca-700"><Icono :nombre="t.icono" :tamano="22" /></span>
            <span class="min-w-0">
              <span class="block font-bold">{{ t.titulo }}</span>
              <span class="block truncate text-sm text-slate-500">{{ t.texto }}</span>
            </span>
          </button>
        </div>
      </section>

      <ProductoView v-else-if="vista === 'productos'" :key="claveCatalogo" :texto-inicial="filtroCatalogo.texto" :categoria-inicial="filtroCatalogo.categoria" @navegar="navegar" />
      <CarritoView v-else-if="vista === 'carrito'" @navegar="navegar" />
      <CompraView v-else-if="vista === 'compra'" modo="generar" @navegar="navegar" />
      <CompraView v-else-if="vista === 'compras'" modo="historial" @navegar="navegar" />
      <UsuarioView v-else-if="vista === 'perfil'" modo="perfil" @navegar="navegar" />
      <UsuarioView v-else modo="acceso" :motivo="destinoPendiente" @navegar="navegar" @sesion-iniciada="alIniciarSesion" />
    </main>

    <!-- ===== Pie de página ===== -->
    <footer class="mt-8 border-t border-slate-200/70 bg-white/60">
      <div class="mx-auto flex max-w-6xl flex-col gap-2 px-4 py-6 text-sm text-slate-500 sm:flex-row sm:items-center sm:justify-between">
        <p class="flex items-center gap-2"><span class="flex h-6 w-6 items-center justify-center rounded-md bg-marca-800 text-[10px] font-extrabold text-acento-400">CM</span> CampusMarket · Universidad Católica Andrés Bello</p>
        <p>Precios en bolívares · Gran Caracas</p>
      </div>
    </footer>

    <!-- Resultado de unir el carrito del navegador con el de la cuenta -->
    <DialogoView v-if="avisoFusion" titulo="Carrito actualizado" :mensaje="avisoFusion" tipo="info" @accion="avisoFusion = null" />
  </div>
</template>
