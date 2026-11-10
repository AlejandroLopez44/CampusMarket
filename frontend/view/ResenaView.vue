<!--
  =====================================================================
  ResenaView — Capa VIEW del módulo Reseñas (modal).
  ---------------------------------------------------------------------
  HU-01 Consultar Reseña · HU-10 Insertar Reseña.

  Flujo HU-01 (diagrama "Consultar reseña"):
    el sistema busca reseñas → [existen] lista (autor, fecha, texto)
                             → [no existen] "No hay reseñas disponibles para este producto"
    [OK] / [Salir] → regresa a Consultar Producto.
  Flujo HU-10: formulario (solo con sesión) → validación local → el servidor
    valida compra previa y reseña única → mensaje de resultado.
  =====================================================================
-->
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { resenaController, MAX_CARACTERES_RESENA, type ListaResenas } from '../control/ResenaClientController'
import { usuarioController } from '../control/UsuarioClientController'
import { formatearFecha } from '../control/ApiClient'
import Icono from './Icono.vue'

const props = defineProps<{ productoId: number; productoNombre: string }>()
const emit = defineEmits<{ cerrar: [] }>()

const datos = ref<ListaResenas | null>(null)
const cargando = ref(true)
const errorCarga = ref('')

// Formulario HU-10
const texto = ref('')
const calificacion = ref(5)
const enviando = ref(false)
const mensaje = ref<{ tipo: 'exito' | 'error'; texto: string } | null>(null)

/** Solo el ESTUDIANTE escribe reseñas; el admin las ve en modo lectura (moderación). */
const rol = computed(() => usuarioController.estado.usuario?.rol ?? null)
const esEstudiante = computed(() => rol.value === 'ESTUDIANTE')
const restantes = computed(() => MAX_CARACTERES_RESENA - texto.value.length)

/** Calificación promedio de las reseñas listadas (null si no hay). Solo presentación. */
const promedio = computed(() => {
  const lista = datos.value?.resenas ?? []
  return lista.length ? lista.reduce((suma, r) => suma + r.calificacion, 0) / lista.length : null
})

/** Texto de ayuda para cada calificación al pasar el cursor por las estrellas. */
const etiquetasEstrellas = ['Muy malo', 'Malo', 'Regular', 'Bueno', 'Excelente']

/** HU-01: consulta las reseñas del producto (el mensaje de vacío lo provee el backend). */
async function cargar(): Promise<void> {
  cargando.value = true
  errorCarga.value = ''
  try {
    datos.value = await resenaController.consultar(props.productoId)
  } catch (e) {
    errorCarga.value = (e as Error).message
  } finally {
    cargando.value = false
  }
}

/**
 * HU-10: valida localmente (texto y calificación) y envía. Los errores de
 * negocio del servidor (403 sin compra, 409 reseña repetida) se muestran tal cual.
 */
async function publicar(): Promise<void> {
  const error = resenaController.validar(texto.value, calificacion.value)
  if (error) {
    mensaje.value = { tipo: 'error', texto: error }
    return
  }
  enviando.value = true
  try {
    const r = await resenaController.insertar(props.productoId, texto.value, calificacion.value)
    mensaje.value = { tipo: 'exito', texto: r.mensaje }
    texto.value = ''
    calificacion.value = 5
    await cargar() // Refresca la lista con la nueva reseña
  } catch (e) {
    mensaje.value = { tipo: 'error', texto: (e as Error).message }
  } finally {
    enviando.value = false
  }
}

onMounted(cargar)
</script>

<template>
  <div class="animar-fondo fixed inset-0 z-40 flex items-end justify-center bg-tinta/60 backdrop-blur-sm sm:items-center sm:p-4" @click.self="emit('cerrar')">
    <div class="animar-entrada flex max-h-[92vh] w-full max-w-xl flex-col overflow-hidden rounded-t-3xl bg-white shadow-elevada sm:rounded-3xl" role="dialog" aria-modal="true" :aria-label="`Reseñas de ${productoNombre}`">
      <!-- Cabecera con promedio -->
      <div class="flex items-start justify-between gap-3 border-b border-slate-100 p-6">
        <div class="min-w-0">
          <p class="eyebrow">Reseñas</p>
          <h2 class="mt-1 truncate text-xl font-extrabold">{{ productoNombre }}</h2>
          <p v-if="promedio !== null" class="mt-2 flex items-center gap-2 text-sm text-slate-600">
            <span class="flex text-acento-500">
              <Icono v-for="n in 5" :key="n" nombre="estrella" :tamano="16" :class="n <= Math.round(promedio) ? 'fill-current' : 'text-slate-300'" />
            </span>
            <strong class="text-tinta">{{ promedio.toFixed(1) }}</strong> de 5 · {{ datos?.resenas.length }} reseña(s)
          </p>
        </div>
        <button class="rounded-full p-1.5 text-slate-400 hover:bg-slate-100 hover:text-tinta" aria-label="Cerrar" @click="emit('cerrar')"><Icono nombre="cerrar" /></button>
      </div>

      <div class="flex-1 overflow-y-auto p-6">
        <!-- HU-01: lista o estado vacío -->
        <div v-if="cargando" class="space-y-3"><div v-for="n in 2" :key="n" class="h-20 animate-pulse rounded-2xl bg-slate-100" /></div>
        <p v-else-if="errorCarga" class="flex items-start gap-2 rounded-xl bg-red-50 p-3 text-red-700"><Icono nombre="alerta" :tamano="18" /> {{ errorCarga }}</p>
        <div v-else-if="datos?.vacia" class="flex flex-col items-center gap-2 rounded-2xl bg-slate-50 px-4 py-8 text-center">
          <span class="flex h-12 w-12 items-center justify-center rounded-full bg-white text-slate-400 shadow-suave"><Icono nombre="estrella" :tamano="22" /></span>
          <p class="font-semibold text-slate-600">{{ datos.mensaje }}</p>
        </div>
        <ul v-else class="space-y-3">
          <li v-for="r in datos?.resenas" :key="r.id" class="rounded-2xl border border-slate-200/70 p-4">
            <div class="flex items-center gap-3">
              <span class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-marca-100 text-sm font-bold text-marca-800">{{ r.autor.charAt(0).toUpperCase() }}</span>
              <div class="min-w-0 flex-1">
                <p class="truncate font-semibold">{{ r.autor }}</p>
                <p class="text-xs text-slate-500">{{ formatearFecha(r.fecha) }}</p>
              </div>
              <span class="flex text-acento-500" :aria-label="`${r.calificacion} de 5 estrellas`">
                <Icono v-for="n in 5" :key="n" nombre="estrella" :tamano="15" :class="n <= r.calificacion ? 'fill-current' : 'text-slate-300'" />
              </span>
            </div>
            <p class="mt-3 whitespace-pre-line text-sm leading-relaxed text-slate-700">{{ r.texto }}</p>
          </li>
        </ul>

        <!-- HU-10: insertar reseña (solo estudiantes) -->
        <div v-if="esEstudiante" class="mt-6 rounded-2xl bg-slate-50 p-5">
          <h3 class="font-bold">Escribe tu reseña</h3>
          <p class="text-xs text-slate-500">Solo puedes reseñar productos que hayas comprado (una reseña por producto).</p>
          <p v-if="mensaje" :class="['mt-3 flex items-start gap-2 rounded-xl p-3 text-sm font-medium', mensaje.tipo === 'exito' ? 'bg-emerald-50 text-emerald-800' : 'bg-red-50 text-red-700']">
            <Icono :nombre="mensaje.tipo === 'exito' ? 'check' : 'alerta'" :tamano="18" /> {{ mensaje.texto }}
          </p>
          <form class="mt-3 space-y-3" @submit.prevent="publicar">
            <div class="flex items-center gap-3">
              <div class="flex" role="radiogroup" aria-label="Calificación">
                <button v-for="n in 5" :key="n" type="button" role="radio" :aria-checked="calificacion === n" :aria-label="`${n} estrellas: ${etiquetasEstrellas[n - 1]}`"
                        :title="etiquetasEstrellas[n - 1]" class="p-0.5 transition hover:scale-110" @click="calificacion = n">
                  <Icono nombre="estrella" :tamano="28" :class="n <= calificacion ? 'fill-current text-acento-500' : 'text-slate-300'" />
                </button>
              </div>
              <span class="text-sm font-semibold text-slate-600">{{ etiquetasEstrellas[calificacion - 1] }}</span>
            </div>
            <textarea v-model="texto" rows="3" :maxlength="MAX_CARACTERES_RESENA" placeholder="¿Qué te pareció el producto?" class="campo" />
            <div class="flex items-center justify-between gap-3">
              <span :class="['text-xs tabular-nums', restantes < 50 ? 'font-semibold text-red-600' : 'text-slate-500']">{{ restantes }} caracteres restantes</span>
              <button type="submit" :disabled="enviando" class="btn-primario">{{ enviando ? 'Publicando…' : 'Publicar reseña' }}</button>
            </div>
          </form>
        </div>
        <p v-else-if="rol === 'ADMINISTRADOR'" class="mt-6 flex items-center gap-2 rounded-xl bg-slate-50 p-3 text-sm text-slate-600"><Icono nombre="escudo" :tamano="18" /> Vista de administrador: las reseñas se muestran en modo lectura.</p>
        <p v-else class="mt-6 flex items-center gap-2 rounded-xl bg-marca-50 p-3 text-sm text-marca-800"><Icono nombre="info" :tamano="18" /> Inicia sesión como estudiante para escribir una reseña de un producto que hayas comprado.</p>
      </div>

      <div class="border-t border-slate-100 p-4 text-right">
        <button class="btn-secundario" @click="emit('cerrar')">OK</button>
      </div>
    </div>
  </div>
</template>
