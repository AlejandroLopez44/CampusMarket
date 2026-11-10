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
import Icono from './Icono.vue'
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

/** Ícono y colores según el tipo de mensaje (solo presentación). */
const apariencia = {
  info: { icono: 'info', clase: 'bg-marca-50 text-marca-700' },
  exito: { icono: 'check', clase: 'bg-emerald-50 text-emerald-600' },
  error: { icono: 'alerta', clase: 'bg-red-50 text-red-600' },
  confirmar: { icono: 'pregunta', clase: 'bg-acento-100 text-amber-700' },
} as const

/** Clase del sistema visual por estilo de botón. */
function claseBoton(estilo: BotonDialogo['estilo'] = 'primario'): string {
  return { primario: 'btn-primario', secundario: 'btn-secundario', peligro: 'btn-peligro' }[estilo]
}
</script>

<template>
  <!-- role="dialog" + aria-modal: accesibilidad para lectores de pantalla -->
  <div class="animar-fondo fixed inset-0 z-50 flex items-end justify-center bg-tinta/60 p-4 backdrop-blur-sm sm:items-center" role="dialog" aria-modal="true" :aria-label="titulo">
    <div class="animar-entrada w-full max-w-md rounded-3xl bg-white p-6 shadow-elevada">
      <div class="flex items-start gap-4">
        <span :class="['flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl', apariencia[tipo].clase]">
          <Icono :nombre="apariencia[tipo].icono" :tamano="22" :grosor="2" />
        </span>
        <div class="min-w-0 flex-1 pt-1">
          <h3 class="text-lg font-bold text-tinta">{{ titulo }}</h3>
          <p v-if="mensaje" class="mt-1.5 whitespace-pre-line text-sm leading-relaxed text-slate-600">{{ mensaje }}</p>
          <!-- Contenido adicional opcional (p. ej. un resumen) -->
          <slot />
        </div>
      </div>
      <div class="mt-6 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
        <button v-for="b in botones" :key="b.valor" type="button" :class="claseBoton(b.estilo)" @click="emit('accion', b.valor)">{{ b.texto }}</button>
      </div>
    </div>
  </div>
</template>
