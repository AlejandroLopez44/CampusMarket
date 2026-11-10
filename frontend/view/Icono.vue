<!--
  =====================================================================
  Icono — Componente visual reutilizable (capa VIEW).
  ---------------------------------------------------------------------
  Responsabilidad única: dibujar un ícono SVG de trazo por su nombre.
  Reemplaza los emojis por íconos nítidos y consistentes en cualquier
  tamaño y color (heredan el color del texto con currentColor).
  OCP: agregar un ícono = agregar una entrada al mapa, sin tocar la plantilla.
  =====================================================================
-->
<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{ nombre: string; tamano?: number; grosor?: number }>(), { tamano: 20, grosor: 1.8 })

/** Trazos de cada ícono (estilo de línea, cuadrícula de 24 × 24). */
const trazos: Record<string, string[]> = {
  carrito: ['M3 4h2l2.4 11.2a2 2 0 0 0 2 1.6h7.7a2 2 0 0 0 2-1.5L21 8H6.2', 'M10 21a1 1 0 1 0 0-2 1 1 0 0 0 0 2Z', 'M17 21a1 1 0 1 0 0-2 1 1 0 0 0 0 2Z'],
  buscar: ['M11 19a8 8 0 1 0 0-16 8 8 0 0 0 0 16Z', 'm21 21-4.3-4.3'],
  inicio: ['M3 10.5 12 3l9 7.5', 'M5 9.5V20h14V9.5', 'M9.5 20v-6h5v6'],
  catalogo: ['M4 4h7v7H4z', 'M13 4h7v7h-7z', 'M4 13h7v7H4z', 'M13 13h7v7h-7z'],
  compras: ['M3 7.5 12 3l9 4.5v9L12 21l-9-4.5z', 'M3 7.5 12 12l9-4.5', 'M12 12v9'],
  perfil: ['M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8Z', 'M4 21a8 8 0 0 1 16 0'],
  panel: ['M4 20V10', 'M10 20V4', 'M16 20v-7', 'M22 20H2'],
  salir: ['M15 4h3a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-3', 'M10 17l-5-5 5-5', 'M5 12h11'],
  menu: ['M4 6h16', 'M4 12h16', 'M4 18h16'],
  cerrar: ['M6 6l12 12', 'M18 6 6 18'],
  estrella: ['m12 3 2.8 5.7 6.2.9-4.5 4.4 1 6.2L12 17.3 6.5 20.2l1-6.2L3 9.6l6.2-.9z'],
  check: ['m5 12.5 4.5 4.5L19 7'],
  alerta: ['M12 9v4', 'M12 17h.01', 'M10.3 3.9 2.4 17.5A2 2 0 0 0 4.1 20.5h15.8a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0Z'],
  info: ['M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18Z', 'M12 11v5', 'M12 8h.01'],
  pregunta: ['M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18Z', 'M9.5 9a2.5 2.5 0 1 1 3.3 2.4c-.5.2-.8.7-.8 1.2v.9', 'M12 17h.01'],
  izquierda: ['M15 18l-6-6 6-6'],
  derecha: ['M9 6l6 6-6 6'],
  mas: ['M12 5v14', 'M5 12h14'],
  menos: ['M5 12h14'],
  etiqueta: ['M3 12V4h8l10 10-8 8z', 'M7.5 7.5h.01'],
  escudo: ['M12 3 4 6v6c0 4.5 3.4 8.3 8 9 4.6-.7 8-4.5 8-9V6z', 'm9 12 2 2 4-4'],
  rayo: ['M13 2 4 14h7l-1 8 9-12h-7z'],
  factura: ['M6 3h12v18l-3-2-3 2-3-2-3 2z', 'M9 8h6', 'M9 12h6', 'M9 16h3'],
  basura: ['M4 7h16', 'M10 11v6', 'M14 11v6', 'M6 7l1 13h10l1-13', 'M9 7V4h6v3'],
  ojo: ['M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12Z', 'M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6Z'],
  ojoNo: ['M3 3l18 18', 'M10.6 5.1A10 10 0 0 1 12 5c6.4 0 10 7 10 7a17 17 0 0 1-3 3.8', 'M6.6 6.6A17 17 0 0 0 2 12s3.6 7 10 7a9.7 9.7 0 0 0 4.4-1', 'M9.9 9.9a3 3 0 0 0 4.2 4.2'],
  usuarioMas: ['M10 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8Z', 'M3 21a7 7 0 0 1 12.5-4.3', 'M19 14v6', 'M16 17h6'],
  caja: ['M21 8 12 3 3 8v8l9 5 9-5z', 'M3 8l9 5 9-5', 'M12 13v8'],
  reloj: ['M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18Z', 'M12 7v5l3 2'],
  billete: ['M3 6h18v12H3z', 'M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6Z', 'M6 9h.01', 'M18 15h.01'],
  usuarios: ['M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8Z', 'M2 21a7 7 0 0 1 14 0', 'M16 3.1a4 4 0 0 1 0 7.8', 'M22 21a7 7 0 0 0-4-6.3'],
  actualizar: ['M20 11a8 8 0 0 0-14.3-4.9L4 8', 'M4 4v4h4', 'M4 13a8 8 0 0 0 14.3 4.9L20 16', 'M20 20v-4h-4'],
  bolsa: ['M5 8h14l-1 13H6z', 'M9 8V6a3 3 0 0 1 6 0v2'],
  correo: ['M3 6h18v12H3z', 'm3 7 9 6 9-6'],
  telefono: ['M5 3h4l2 5-2.5 1.5a11 11 0 0 0 6 6L16 13l5 2v4a2 2 0 0 1-2 2A17 17 0 0 1 3 5a2 2 0 0 1 2-2Z'],
}

const lista = computed(() => trazos[props.nombre] ?? trazos.info)
</script>

<template>
  <svg :width="tamano" :height="tamano" viewBox="0 0 24 24" fill="none" stroke="currentColor" :stroke-width="grosor"
       stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" class="shrink-0">
    <path v-for="(d, i) in lista" :key="i" :d="d" />
  </svg>
</template>
