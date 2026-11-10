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

const claseCampo = (error?: string) =>
  ['w-full rounded-lg border px-3 py-2 outline-none focus:ring-2 focus:ring-marca-500', error ? 'border-red-500' : 'border-slate-300']
</script>

<template>
  <!-- ======================= PERFIL ======================= -->
  <section v-if="modo === 'perfil'" class="mx-auto max-w-lg">
    <h1 class="mb-4 text-2xl font-bold">Mi perfil</h1>

    <p v-if="cargandoPerfil" class="py-10 text-center text-slate-500">Cargando perfil…</p>

    <div v-else-if="errorPerfil" class="rounded-2xl bg-red-50 p-6 text-center">
      <p class="font-semibold text-red-700">{{ errorPerfil }}</p>
      <button class="mt-4 rounded-lg bg-red-600 px-4 py-2 font-semibold text-white hover:bg-red-700" @click="cargarPerfil">Intentar nuevamente</button>
    </div>

    <div v-else-if="perfil" class="rounded-2xl bg-white p-6 shadow-sm">
      <div class="mb-5 flex items-center gap-4">
        <div class="flex h-16 w-16 items-center justify-center rounded-full bg-marca-600 text-2xl font-bold text-white">
          {{ perfil.nombre.charAt(0).toUpperCase() }}
        </div>
        <div>
          <h2 class="text-xl font-bold">{{ perfil.nombre }}</h2>
          <span class="rounded-full bg-marca-50 px-3 py-0.5 text-xs font-bold text-marca-700">{{ perfil.rol === 'ADMINISTRADOR' ? 'Administrador' : 'Estudiante' }}</span>
        </div>
      </div>
      <!-- Datos de solo lectura: no hay inputs editables (HU-02) -->
      <dl class="divide-y divide-slate-100">
        <div class="flex flex-col py-3 sm:flex-row sm:justify-between"><dt class="text-slate-500">Nombre</dt><dd class="font-medium">{{ perfil.nombre }}</dd></div>
        <div class="flex flex-col py-3 sm:flex-row sm:justify-between"><dt class="text-slate-500">Correo UCAB</dt><dd class="font-medium break-all">{{ perfil.correo }}</dd></div>
        <div class="flex flex-col py-3 sm:flex-row sm:justify-between"><dt class="text-slate-500">Rol</dt><dd class="font-medium">{{ perfil.rol === 'ADMINISTRADOR' ? 'Administrador' : 'Estudiante' }}</dd></div>
        <div class="flex flex-col py-3 sm:flex-row sm:justify-between"><dt class="text-slate-500">Teléfono</dt><dd class="font-medium">{{ perfil.telefono || 'No registrado' }}</dd></div>
      </dl>
    </div>

    <!-- Criterio 3 de la HU: botón "Regresar" siempre visible que vuelve al menú principal (Dashboard) -->
    <button class="mt-6 w-full rounded-lg bg-slate-200 px-4 py-2 font-semibold hover:bg-slate-300" @click="emit('navegar', 'inicio')">
      ← Regresar al menú principal
    </button>
  </section>

  <!-- ======================= ACCESO ======================= -->
  <section v-else class="mx-auto max-w-md rounded-2xl bg-white p-6 shadow-sm">
    <div class="mb-5 flex justify-center gap-2">
      <button :class="['border-b-4 px-4 py-2 font-bold', pestana === 'login' ? 'border-marca-600 text-marca-600' : 'border-transparent text-slate-500']" @click="cambiarPestana('login')">Iniciar sesión</button>
      <button :class="['border-b-4 px-4 py-2 font-bold', pestana === 'registro' ? 'border-marca-600 text-marca-600' : 'border-transparent text-slate-500']" @click="cambiarPestana('registro')">Registrarse</button>
    </div>

    <!-- Por qué se pide iniciar sesión (p. ej. el visitante pulsó "Generar compra") -->
    <p v-if="textoMotivo && !aviso" class="mb-4 rounded-lg bg-marca-50 p-3 text-sm text-marca-700">{{ textoMotivo }}</p>
    <p v-if="aviso" :class="['mb-4 rounded-lg p-3 text-sm', aviso.tipo === 'exito' ? 'bg-emerald-50 text-emerald-700' : 'bg-red-50 text-red-700']">{{ aviso.texto }}</p>

    <!-- Login -->
    <form v-if="pestana === 'login'" class="space-y-4" @submit.prevent="iniciarSesion">
      <div>
        <label class="text-sm font-medium" for="l-correo">Correo institucional</label>
        <input id="l-correo" v-model="login.correo" type="email" autocomplete="username" placeholder="usuario@est.ucab.edu.ve" required :class="claseCampo()" />
      </div>
      <div>
        <label class="text-sm font-medium" for="l-pass">Contraseña</label>
        <input id="l-pass" v-model="login.password" type="password" autocomplete="current-password" required :class="claseCampo()" />
      </div>
      <button type="submit" :disabled="procesando" class="w-full rounded-lg bg-marca-600 py-2 font-semibold text-white hover:bg-marca-700 disabled:opacity-50">Entrar</button>
    </form>

    <!-- Registro (HU-07) -->
    <form v-else class="space-y-4" novalidate @submit.prevent="registrar">
      <div>
        <label class="text-sm font-medium" for="r-nombre">Nombre completo *</label>
        <input id="r-nombre" v-model="registro.nombre" maxlength="100" :class="claseCampo(erroresRegistro.nombre)" />
        <p v-if="erroresRegistro.nombre" class="text-sm text-red-600">{{ erroresRegistro.nombre }}</p>
      </div>
      <div>
        <label class="text-sm font-medium" for="r-correo">Correo institucional *</label>
        <input id="r-correo" v-model="registro.correo" type="email" placeholder="usuario@est.ucab.edu.ve" :class="claseCampo(erroresRegistro.correo)" />
        <p v-if="erroresRegistro.correo" class="text-sm text-red-600">{{ erroresRegistro.correo }}</p>
      </div>
      <div>
        <label class="text-sm font-medium" for="r-tel">Teléfono</label>
        <!-- maxlength impide escribir más de 12 caracteres ("0414-1234567"); inputmode muestra teclado numérico en el celular -->
        <input id="r-tel" v-model="registro.telefono" type="tel" inputmode="tel" placeholder="0414-1234567"
               :maxlength="MAX_TELEFONO" :class="claseCampo(erroresRegistro.telefono)" />
        <p v-if="erroresRegistro.telefono" class="text-sm text-red-600">{{ erroresRegistro.telefono }}</p>
        <p v-else class="text-xs text-slate-500">Opcional. Códigos: 0412, 0414, 0416, 0424, 0426 o 0212.</p>
      </div>
      <div>
        <label class="text-sm font-medium" for="r-pass">Contraseña * (mín. 8 caracteres)</label>
        <input id="r-pass" v-model="registro.password" type="password" autocomplete="new-password" :class="claseCampo(erroresRegistro.password)" />
        <p v-if="erroresRegistro.password" class="text-sm text-red-600">{{ erroresRegistro.password }}</p>
      </div>
      <div>
        <label class="text-sm font-medium" for="r-pass2">Confirmar contraseña *</label>
        <input id="r-pass2" v-model="registro.confirmarPassword" type="password" autocomplete="new-password" :class="claseCampo(erroresRegistro.confirmarPassword)" />
        <p v-if="erroresRegistro.confirmarPassword" class="text-sm text-red-600">{{ erroresRegistro.confirmarPassword }}</p>
      </div>
      <button type="submit" :disabled="procesando" class="w-full rounded-lg bg-marca-600 py-2 font-semibold text-white hover:bg-marca-700 disabled:opacity-50">Crear cuenta</button>
    </form>
  </section>
</template>
