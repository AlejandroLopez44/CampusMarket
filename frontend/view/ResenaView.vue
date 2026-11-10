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
  <div class="fixed inset-0 z-40 flex items-end justify-center bg-black/50 p-4 sm:items-center" @click.self="emit('cerrar')">
    <div class="max-h-[90vh] w-full max-w-lg overflow-y-auto rounded-2xl bg-white p-6 shadow-xl">
      <div class="mb-4 flex items-start justify-between gap-2">
        <h2 class="text-xl font-bold">Reseñas de {{ productoNombre }}</h2>
        <button class="rounded-lg px-2 text-xl text-slate-500 hover:bg-slate-100" aria-label="Cerrar" @click="emit('cerrar')">✕</button>
      </div>

      <!-- HU-01: lista o estado vacío -->
      <p v-if="cargando" class="text-slate-500">Buscando reseñas…</p>
      <p v-else-if="errorCarga" class="rounded-lg bg-red-50 p-3 text-red-700">{{ errorCarga }}</p>
      <p v-else-if="datos?.vacia" class="rounded-lg bg-slate-100 p-4 text-center text-slate-600">{{ datos.mensaje }}</p>
      <ul v-else class="space-y-3">
        <li v-for="r in datos?.resenas" :key="r.id" class="rounded-xl border border-slate-200 p-3">
          <div class="flex flex-wrap items-center justify-between gap-2">
            <strong>{{ r.autor }}</strong>
            <span class="text-sm text-slate-500">{{ formatearFecha(r.fecha) }}</span>
          </div>
          <div class="text-amber-500" :aria-label="`${r.calificacion} de 5 estrellas`">
            {{ '★'.repeat(r.calificacion) }}<span class="text-slate-300">{{ '★'.repeat(5 - r.calificacion) }}</span>
          </div>
          <p class="mt-1 whitespace-pre-line text-slate-700">{{ r.texto }}</p>
        </li>
      </ul>

      <hr class="my-5 border-slate-200" />

      <!-- HU-10: insertar reseña (solo estudiantes) -->
      <div v-if="esEstudiante">
        <h3 class="mb-2 font-bold">Escribir una reseña</h3>
        <p v-if="mensaje" :class="['mb-3 rounded-lg p-3 text-sm', mensaje.tipo === 'exito' ? 'bg-emerald-50 text-emerald-700' : 'bg-red-50 text-red-700']">
          {{ mensaje.texto }}
        </p>
        <form class="space-y-3" @submit.prevent="publicar">
          <div class="flex gap-1" role="radiogroup" aria-label="Calificación">
            <button v-for="n in 5" :key="n" type="button" :aria-label="`${n} estrellas`"
                    :class="['text-3xl leading-none', n <= calificacion ? 'text-amber-500' : 'text-slate-300']"
                    @click="calificacion = n">★</button>
          </div>
          <textarea v-model="texto" rows="3" :maxlength="MAX_CARACTERES_RESENA" placeholder="¿Qué te pareció el producto?"
                    class="w-full rounded-lg border border-slate-300 px-3 py-2 outline-none focus:ring-2 focus:ring-marca-500" />
          <div class="flex items-center justify-between">
            <span :class="['text-xs', restantes < 50 ? 'text-red-600' : 'text-slate-500']">{{ restantes }} caracteres restantes</span>
            <button type="submit" :disabled="enviando" class="rounded-lg bg-marca-600 px-4 py-2 font-semibold text-white hover:bg-marca-700 disabled:opacity-50">
              Publicar reseña
            </button>
          </div>
        </form>
      </div>
      <p v-else-if="rol === 'ADMINISTRADOR'" class="text-sm text-slate-500">Vista de administrador: las reseñas se muestran en modo lectura.</p>
      <p v-else class="text-sm text-slate-500">Inicia sesión como estudiante para escribir una reseña de un producto que hayas comprado.</p>

      <div class="mt-5 text-right">
        <button class="rounded-lg bg-slate-200 px-4 py-2 font-semibold hover:bg-slate-300" @click="emit('cerrar')">OK</button>
      </div>
    </div>
  </div>
</template>
