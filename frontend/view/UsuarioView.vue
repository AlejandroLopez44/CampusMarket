<!--
  =====================================================================
  UsuarioView — Capa VIEW del módulo Usuario.
  ---------------------------------------------------------------------
  modo="acceso": HU-07 Registro de Cuenta + Inicio de sesión.
  modo="perfil": HU-02 Consultar Perfil (solo lectura de la sesión activa).

  HU-02: muestra Nombre, Correo UCAB, Rol y Teléfono. Si la BD falla, muestra
  "Error al cargar la información del perfil. Intente nuevamente" con opción
  de reintentar, y siempre ofrece volver limpiamente al Dashboard.
  =====================================================================
-->
<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import Icono from './Icono.vue'
import { usuarioController, type Perfil, type RegistroDatos } from '../control/UsuarioClientController'
import { ApiError } from '../control/ApiClient'
import type { Destino } from '../control/NavegacionController'

/** Máximo de caracteres del teléfono, tomado del controlador para no repetir el número. */
const MAX_TELEFONO = usuarioController.maxTelefono

/**
 * @prop modo   'acceso' (registro/login) o 'perfil' (HU-02)
 * @prop motivo vista que el visitante intentó abrir sin sesión (p. ej. 'compra');
 *              sirve para explicarle por qué debe iniciar sesión.
 */
const props = defineProps<{ modo: 'acceso' | 'perfil'; motivo?: Destino | null }>()

/** Texto que explica por qué se pide iniciar sesión (o null si entró por su cuenta). */
const textoMotivo = computed(() => ({
  compra: 'Inicia sesión o regístrate para generar tu compra. Tu carrito se conservará.',
  compras: 'Inicia sesión para ver tus compras.',
  perfil: 'Inicia sesión para ver tu perfil.',
} as Partial<Record<Destino, string>>)[props.motivo ?? 'inicio'] ?? null)
const emit = defineEmits<{ navegar: [destino: Destino]; 'sesion-iniciada': [] }>()

// =================== Acceso (registro / login) ===================
const pestana = ref<'registro' | 'login'>('login')
const procesando = ref(false)
const aviso = ref<{ tipo: 'exito' | 'error'; texto: string } | null>(null)

const registro = reactive<RegistroDatos>({ nombre: '', correo: '', telefono: '', password: '', confirmarPassword: '' })
const erroresRegistro = ref<Record<string, string>>({})
const login = reactive({ correo: '', password: '' })

/** Cambia de pestaña limpiando mensajes previos. */
function cambiarPestana(p: 'registro' | 'login'): void {
  pestana.value = p
  aviso.value = null
}

/**
 * HU-07: valida en cliente (dominio @est.ucab.edu.ve, contraseña, confirmación)
 * y luego registra. El servidor repite las validaciones (correo duplicado → 409).
 * Al éxito, pasa a "Iniciar sesión" con el correo ya escrito.
 */
async function registrar(): Promise<void> {
  aviso.value = null
  erroresRegistro.value = usuarioController.validarRegistro(registro)
  if (Object.keys(erroresRegistro.value).length > 0) return

  procesando.value = true
  try {
    const r = await usuarioController.registrar(registro)
    login.correo = r.datos.correo
    Object.assign(registro, { nombre: '', correo: '', telefono: '', password: '', confirmarPassword: '' })
    pestana.value = 'login'
    aviso.value = { tipo: 'exito', texto: `${r.mensaje} Ya puedes iniciar sesión.` }
  } catch (e) {
    if (e instanceof ApiError) erroresRegistro.value = e.errores
    aviso.value = { tipo: 'error', texto: (e as Error).message }
  } finally {
    procesando.value = false
  }
}

/** Inicia sesión y notifica a App para sincronizar carrito y volver al Dashboard. */
async function iniciarSesion(): Promise<void> {
  aviso.value = null
  procesando.value = true
  try {
    await usuarioController.iniciarSesion(login.correo, login.password)
    login.password = ''
    emit('sesion-iniciada')
  } catch (e) {
    aviso.value = { tipo: 'error', texto: (e as Error).message }
  } finally {
    procesando.value = false
  }
}

// =================== Perfil (HU-02) ===================
const perfil = ref<Perfil | null>(null)
const cargandoPerfil = ref(false)
const errorPerfil = ref('')

/** Lee el perfil desde la BD. Cualquier error muestra el mensaje de la HU con opción de reintento. */
async function cargarPerfil(): Promise<void> {
  cargandoPerfil.value = true
  errorPerfil.value = ''
  try {
    perfil.value = await usuarioController.consultarPerfil()
  } catch (e) {
    if (e instanceof ApiError && e.status === 401) {
      emit('navegar', 'cuenta') // Sesión vencida: se pide iniciar sesión otra vez
      return
    }
    errorPerfil.value = 'Error al cargar la información del perfil. Intente nuevamente'
  } finally {
    cargandoPerfil.value = false
  }
}

onMounted(() => {
  if (props.modo === 'perfil') cargarPerfil()
})

/** Clases del sistema visual para un campo (en rojo si tiene error). */
const claseCampo = (error?: string) => ['campo', error ? 'campo-error' : '']

/** Muestra u oculta las contraseñas escritas (ayuda a no equivocarse, sobre todo en el celular). */
const verClave = ref(false)
</script>

<template>
  <!-- ======================= PERFIL (HU-02) ======================= -->
  <section v-if="modo === 'perfil'" class="mx-auto max-w-2xl">
    <div class="mb-6">
      <p class="eyebrow">Tu cuenta</p>
      <h1 class="mt-1 text-3xl font-extrabold">Mi perfil</h1>
    </div>

    <div v-if="cargandoPerfil" class="tarjeta flex items-center gap-4 p-6" aria-label="Cargando perfil">
      <div class="h-16 w-16 animate-pulse rounded-full bg-slate-200" />
      <div class="flex-1 space-y-2"><div class="h-4 w-1/2 animate-pulse rounded bg-slate-200" /><div class="h-3 w-1/3 animate-pulse rounded bg-slate-200" /></div>
    </div>

    <div v-else-if="errorPerfil" class="tarjeta flex flex-col items-center gap-3 p-8 text-center">
      <span class="flex h-12 w-12 items-center justify-center rounded-full bg-red-50 text-red-600"><Icono nombre="alerta" :tamano="24" /></span>
      <p class="font-semibold text-red-700">{{ errorPerfil }}</p>
      <button class="btn-peligro" @click="cargarPerfil"><Icono nombre="actualizar" :tamano="16" /> Intentar nuevamente</button>
    </div>

    <div v-else-if="perfil" class="tarjeta overflow-hidden">
      <!-- Cabecera con avatar -->
      <div class="relative bg-marca-900 px-6 pb-6 pt-8 text-white">
        <div class="pointer-events-none absolute -right-10 -top-16 h-48 w-48 rounded-full bg-marca-500/40 blur-3xl" />
        <div class="relative flex items-center gap-4">
          <div class="flex h-16 w-16 items-center justify-center rounded-2xl bg-acento-400 font-titulo text-2xl font-extrabold text-tinta shadow-suave">
            {{ perfil.nombre.charAt(0).toUpperCase() }}
          </div>
          <div class="min-w-0">
            <h2 class="truncate text-xl font-extrabold">{{ perfil.nombre }}</h2>
            <span class="insignia mt-1 bg-white/15 text-white">{{ perfil.rol === 'ADMINISTRADOR' ? 'Administrador' : 'Estudiante' }}</span>
          </div>
        </div>
      </div>
      <!-- Datos de solo lectura: no hay campos editables (HU-02) -->
      <dl class="divide-y divide-slate-100">
        <div class="flex items-center gap-4 px-6 py-4"><span class="text-slate-400"><Icono nombre="perfil" /></span><div class="min-w-0"><dt class="text-xs font-bold uppercase tracking-wider text-slate-500">Nombre</dt><dd class="font-semibold">{{ perfil.nombre }}</dd></div></div>
        <div class="flex items-center gap-4 px-6 py-4"><span class="text-slate-400"><Icono nombre="correo" /></span><div class="min-w-0"><dt class="text-xs font-bold uppercase tracking-wider text-slate-500">Correo UCAB</dt><dd class="break-all font-semibold">{{ perfil.correo }}</dd></div></div>
        <div class="flex items-center gap-4 px-6 py-4"><span class="text-slate-400"><Icono nombre="escudo" /></span><div class="min-w-0"><dt class="text-xs font-bold uppercase tracking-wider text-slate-500">Rol</dt><dd class="font-semibold">{{ perfil.rol === 'ADMINISTRADOR' ? 'Administrador' : 'Estudiante' }}</dd></div></div>
        <div class="flex items-center gap-4 px-6 py-4"><span class="text-slate-400"><Icono nombre="telefono" /></span><div class="min-w-0"><dt class="text-xs font-bold uppercase tracking-wider text-slate-500">Teléfono</dt><dd class="font-semibold">{{ perfil.telefono || 'No registrado' }}</dd></div></div>
      </dl>
    </div>

    <!-- Criterio 3 de la HU: botón "Regresar" siempre visible que vuelve al menú principal (Dashboard) -->
    <button class="btn-secundario mt-6 w-full" @click="emit('navegar', 'inicio')">
      <Icono nombre="izquierda" :tamano="16" /> Regresar al menú principal
    </button>
  </section>

  <!-- ======================= ACCESO (login / registro HU-07) ======================= -->
  <section v-else class="mx-auto grid max-w-5xl overflow-hidden rounded-3xl bg-white shadow-elevada lg:grid-cols-2">
    <!-- Panel de marca (solo en pantallas grandes) -->
    <div class="relative hidden overflow-hidden bg-marca-900 p-10 text-white lg:flex lg:flex-col lg:justify-between">
      <div class="pointer-events-none absolute -right-20 -top-24 h-80 w-80 rounded-full bg-marca-500/40 blur-3xl" />
      <div class="pointer-events-none absolute -bottom-24 -left-10 h-72 w-72 rounded-full bg-acento-400/25 blur-3xl" />
      <div class="relative flex items-center gap-2.5">
        <span class="flex h-10 w-10 items-center justify-center rounded-xl bg-white/10 font-titulo font-extrabold text-acento-400">CM</span>
        <span class="font-titulo text-xl font-extrabold">CampusMarket</span>
      </div>
      <div class="relative">
        <h2 class="text-3xl font-extrabold leading-tight">Tu mercado universitario, <span class="text-acento-400">con tu cuenta UCAB.</span></h2>
        <ul class="mt-6 space-y-3 text-marca-100">
          <li class="flex items-center gap-3"><span class="flex h-8 w-8 items-center justify-center rounded-lg bg-white/10"><Icono nombre="etiqueta" :tamano="18" /></span>Precios claros en bolívares</li>
          <li class="flex items-center gap-3"><span class="flex h-8 w-8 items-center justify-center rounded-lg bg-white/10"><Icono nombre="rayo" :tamano="18" /></span>Stock verificado en tiempo real</li>
          <li class="flex items-center gap-3"><span class="flex h-8 w-8 items-center justify-center rounded-lg bg-white/10"><Icono nombre="estrella" :tamano="18" /></span>Reseñas de otros estudiantes</li>
        </ul>
      </div>
      <p class="relative text-sm text-marca-200">Solo para correos @est.ucab.edu.ve</p>
    </div>

    <!-- Formularios -->
    <div class="p-6 sm:p-10">
      <h1 class="text-2xl font-extrabold">{{ pestana === 'login' ? 'Bienvenido de vuelta' : 'Crea tu cuenta' }}</h1>
      <p class="mt-1 text-sm text-slate-500">{{ pestana === 'login' ? 'Ingresa con tu correo institucional.' : 'Regístrate en menos de un minuto.' }}</p>

      <!-- Selector de pestaña -->
      <div class="mt-6 grid grid-cols-2 rounded-xl bg-slate-100 p-1" role="tablist">
        <button role="tab" :aria-selected="pestana === 'login'" :class="['rounded-lg py-2 text-sm font-bold transition', pestana === 'login' ? 'bg-white text-marca-800 shadow-suave' : 'text-slate-500 hover:text-tinta']" @click="cambiarPestana('login')">Iniciar sesión</button>
        <button role="tab" :aria-selected="pestana === 'registro'" :class="['rounded-lg py-2 text-sm font-bold transition', pestana === 'registro' ? 'bg-white text-marca-800 shadow-suave' : 'text-slate-500 hover:text-tinta']" @click="cambiarPestana('registro')">Registrarse</button>
      </div>

      <!-- Por qué se pide iniciar sesión (p. ej. el visitante pulsó "Generar compra") -->
      <p v-if="textoMotivo && !aviso" class="mt-5 flex items-start gap-2 rounded-xl bg-marca-50 p-3 text-sm text-marca-800"><Icono nombre="info" :tamano="18" /> {{ textoMotivo }}</p>
      <p v-if="aviso" :class="['mt-5 flex items-start gap-2 rounded-xl p-3 text-sm font-medium', aviso.tipo === 'exito' ? 'bg-emerald-50 text-emerald-800' : 'bg-red-50 text-red-700']">
        <Icono :nombre="aviso.tipo === 'exito' ? 'check' : 'alerta'" :tamano="18" /> {{ aviso.texto }}
      </p>

      <!-- Login -->
      <form v-if="pestana === 'login'" class="mt-6 space-y-4" @submit.prevent="iniciarSesion">
        <div>
          <label class="etiqueta" for="l-correo">Correo institucional</label>
          <input id="l-correo" v-model="login.correo" type="email" autocomplete="username" placeholder="usuario@est.ucab.edu.ve" required :class="claseCampo()" />
        </div>
        <div>
          <label class="etiqueta" for="l-pass">Contraseña</label>
          <div class="relative">
            <input id="l-pass" v-model="login.password" :type="verClave ? 'text' : 'password'" autocomplete="current-password" required :class="[...claseCampo(), '!pr-11']" />
            <button type="button" class="absolute right-2 top-1/2 -translate-y-1/2 rounded-lg p-1.5 text-slate-400 hover:text-tinta" :aria-label="verClave ? 'Ocultar contraseña' : 'Mostrar contraseña'" @click="verClave = !verClave">
              <Icono :nombre="verClave ? 'ojoNo' : 'ojo'" :tamano="18" />
            </button>
          </div>
        </div>
        <button type="submit" :disabled="procesando" class="btn-primario w-full !py-3">{{ procesando ? 'Entrando…' : 'Entrar' }}</button>
        <p class="text-center text-sm text-slate-500">¿No tienes cuenta? <button type="button" class="font-semibold text-marca-700 hover:underline" @click="cambiarPestana('registro')">Regístrate</button></p>
      </form>

      <!-- Registro (HU-07) -->
      <form v-else class="mt-6 space-y-4" novalidate @submit.prevent="registrar">
        <div>
          <label class="etiqueta" for="r-nombre">Nombre completo *</label>
          <input id="r-nombre" v-model="registro.nombre" maxlength="100" autocomplete="name" placeholder="Ej. Ana Rodríguez" :class="claseCampo(erroresRegistro.nombre)" />
          <p v-if="erroresRegistro.nombre" class="error-texto">{{ erroresRegistro.nombre }}</p>
        </div>
        <div>
          <label class="etiqueta" for="r-correo">Correo institucional *</label>
          <input id="r-correo" v-model="registro.correo" type="email" autocomplete="email" placeholder="usuario@est.ucab.edu.ve" :class="claseCampo(erroresRegistro.correo)" />
          <p v-if="erroresRegistro.correo" class="error-texto">{{ erroresRegistro.correo }}</p>
        </div>
        <div>
          <label class="etiqueta" for="r-tel">Teléfono</label>
          <!-- maxlength impide escribir más de 12 caracteres ("0414-1234567"); inputmode muestra teclado numérico en el celular -->
          <input id="r-tel" v-model="registro.telefono" type="tel" inputmode="tel" autocomplete="tel" placeholder="0414-1234567"
                 :maxlength="MAX_TELEFONO" :class="claseCampo(erroresRegistro.telefono)" />
          <p v-if="erroresRegistro.telefono" class="error-texto">{{ erroresRegistro.telefono }}</p>
          <p v-else class="ayuda">Opcional. Códigos: 0412, 0414, 0416, 0424, 0426 o 0212.</p>
        </div>
        <div class="grid gap-4 sm:grid-cols-2">
          <div>
            <label class="etiqueta" for="r-pass">Contraseña *</label>
            <input id="r-pass" v-model="registro.password" :type="verClave ? 'text' : 'password'" autocomplete="new-password" :class="claseCampo(erroresRegistro.password)" />
            <p v-if="erroresRegistro.password" class="error-texto">{{ erroresRegistro.password }}</p>
            <p v-else class="ayuda">Mínimo 8 caracteres.</p>
          </div>
          <div>
            <label class="etiqueta" for="r-pass2">Confirmar contraseña *</label>
            <input id="r-pass2" v-model="registro.confirmarPassword" :type="verClave ? 'text' : 'password'" autocomplete="new-password" :class="claseCampo(erroresRegistro.confirmarPassword)" />
            <p v-if="erroresRegistro.confirmarPassword" class="error-texto">{{ erroresRegistro.confirmarPassword }}</p>
          </div>
        </div>
        <label class="flex items-center gap-2 text-sm text-slate-600"><input v-model="verClave" type="checkbox" class="h-4 w-4 accent-marca-700" /> Mostrar contraseñas</label>
        <button type="submit" :disabled="procesando" class="btn-primario w-full !py-3"><Icono nombre="usuarioMas" :tamano="18" /> {{ procesando ? 'Creando cuenta…' : 'Crear cuenta' }}</button>
      </form>
    </div>
  </section>
</template>
