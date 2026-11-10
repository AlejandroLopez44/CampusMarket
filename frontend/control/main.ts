/**
 * main.ts — Punto de entrada del frontend (capa CONTROL).
 * Responsabilidad: crear la aplicación Vue, cargar los estilos globales
 * (Tailwind) y montar la vista raíz. No contiene lógica de negocio.
 */
import { createApp } from 'vue'
import '../view/estilos.css'
import App from '../view/App.vue'

createApp(App).mount('#app')
