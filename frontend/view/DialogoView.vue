<!--
  =====================================================================
  DialogoView — Componente visual reutilizable (capa VIEW).
  ---------------------------------------------------------------------
  Transversal: lo usan las HU que piden "confirmación", "mensaje de éxito"
  o "alerta" (HU-03, HU-04, HU-05, HU-02...).

  Responsabilidad única (SRP): mostrar un cuadro modal con título, mensaje
  y botones. NO decide qué hacer: emite el valor del botón pulsado y la
  vista que lo usa resuelve el flujo (bajo acoplamiento, OCP: nuevos
  diálogos = nuevos botones, sin modificar este componente).
  =====================================================================
-->
<script setup lang="ts">
import type { BotonDialogo } from '../control/NavegacionController'

withDefaults(defineProps<{
  titulo: string
  mensaje?: string
  tipo?: 'info' | 'exito' | 'error' | 'confirmar'
  botones?: BotonDialogo[]
}>(), {
  mensaje: '',
  tipo: 'info',
  botones: () => [{ texto: 'Aceptar', valor: 'ok', estilo: 'primario' }],
})

/** Emite el `valor` del botón pulsado (p. ej. "confirmar", "cancelar", "salir"). */
const emit = defineEmits<{ accion: [valor: string] }>()

/** Ícono y color según el tipo de mensaje (solo presentación). */
const iconos = { info: 'ℹ️', exito: '✅', error: '⚠️', confirmar: '❓' } as const

/** Clases Tailwind por estilo de botón. */
function claseBoton(estilo: BotonDialogo['estilo'] = 'primario'): string {
  return {
    primario: 'bg-marca-600 text-white hover:bg-marca-700',
    secundario: 'bg-slate-200 text-slate-800 hover:bg-slate-300',
    peligro: 'bg-red-600 text-white hover:bg-red-700',
  }[estilo]
}
</script>

<template>
  <!-- role="dialog" + aria-modal: accesibilidad para lectores de pantalla -->
  <div class="fixed inset-0 z-50 flex items-end justify-center bg-black/50 p-4 sm:items-center" role="dialog" aria-modal="true">
    <div class="w-full max-w-md rounded-2xl bg-white p-6 shadow-xl">
      <div class="flex items-start gap-3">
        <span class="text-2xl" aria-hidden="true">{{ iconos[tipo] }}</span>
        <div class="flex-1">
          <h3 class="text-lg font-bold text-slate-900">{{ titulo }}</h3>
          <p v-if="mensaje" class="mt-1 whitespace-pre-line text-slate-600">{{ mensaje }}</p>
          <!-- Contenido adicional opcional (p. ej. un resumen) -->
          <slot />
        </div>
      </div>
      <div class="mt-6 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
        <button
          v-for="b in botones" :key="b.valor" type="button"
          :class="['rounded-lg px-4 py-2 font-semibold transition', claseBoton(b.estilo)]"
          @click="emit('accion', b.valor)"
        >{{ b.texto }}</button>
      </div>
    </div>
  </div>
</template>
