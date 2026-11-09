<!-- src/components/CatalogoProductos.vue -->
<script setup>
import { ref } from 'vue'
import { estadoProductos } from '../data/productos.js'
import { agregarProducto } from '../data/carrito.js'
import ModalResenas from './ModalResenas.vue'

const mensajeFeedback = ref('')
const productoSeleccionado = ref(null)

const manejarAgregar = (producto) => {
  agregarProducto(producto)
  mensajeFeedback.value = `"${producto.nombre}" agregado al carrito.`
  setTimeout(() => { mensajeFeedback.value = '' }, 2000)
}

const abrirResenas = (producto) => {
  productoSeleccionado.value = producto
}

const cerrarResenas = () => {
  productoSeleccionado.value = null
}
</script>

<template>
  <div class="catalogo-container">
    <h2>Catálogo de Productos</h2>
    <p class="subtitle">Encuentra los insumos que necesitas para tus clases</p>

    <!-- Mensaje flotante de confirmación -->
    <transition name="fade">
      <div v-if="mensajeFeedback" class="toast-feedback">
        {{ mensajeFeedback }}
      </div>
    </transition>

    <div class="grid-productos">
      <!-- Aquí usamos estadoProductos.lista -->
      <div v-for="producto in estadoProductos.lista" :key="producto.id" class="producto-card">
        <img :src="producto.imagen" :alt="producto.nombre" class="producto-img">
        
        <div class="producto-info">
          <span class="categoria">{{ producto.categoria }}</span>
          <h3>{{ producto.nombre }}</h3>
          <p class="precio">Bs. {{ producto.precio.toFixed(2) }}</p>
          
          <div class="acciones-card">
            <button @click="manejarAgregar(producto)" class="btn btn-primary">
              Agregar
            </button>
            <button @click="abrirResenas(producto)" class="btn btn-secondary">
              Reseñas
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- Modal de Reseñas -->
    <ModalResenas 
      v-if="productoSeleccionado" 
      :producto="productoSeleccionado" 
      @cerrar="cerrarResenas" 
    />
  </div>
</template>

<style scoped>
.catalogo-container { 
  max-width: 1000px; 
  margin: 0 auto; 
  position: relative; 
}

h2 { 
  text-align: center; 
  color: var(--text-color); 
  margin-bottom: 5px; 
}

.subtitle { 
  text-align: center; 
  color: #6B7280; 
  font-size: 14px; 
  margin-bottom: 30px; 
}

.grid-productos {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 20px;
}

.producto-card {
  background-color: var(--card-bg);
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 4px 6px rgba(0, 0, 0, 0.05);
  transition: transform 0.2s ease;
  display: flex;
  flex-direction: column;
}

.producto-card:hover { 
  transform: translateY(-5px); 
}

.producto-img { 
  width: 100%; 
  height: 160px; 
  object-fit: cover; 
  background-color: #E5E7EB; 
}

.producto-info { 
  padding: 15px; 
  display: flex; 
  flex-direction: column; 
  flex-grow: 1; 
}

.categoria { 
  font-size: 12px; 
  color: var(--primary-color); 
  font-weight: bold; 
  text-transform: uppercase; 
  margin-bottom: 5px; 
}

h3 { 
  font-size: 16px; 
  margin-bottom: 10px; 
  color: var(--text-color); 
}

.precio { 
  font-size: 18px; 
  font-weight: bold; 
  color: var(--success-color); 
  margin-bottom: 15px; 
  margin-top: auto; 
}

.acciones-card {
  display: flex;
  gap: 10px;
}

.acciones-card .btn {
  flex: 1;
  padding: 8px 10px;
  font-size: 14px;
}

.btn-secondary {
  background-color: #E5E7EB;
  color: var(--text-color);
}

.btn-secondary:hover { 
  background-color: #D1D5DB; 
}

/* Estilos para el mensaje flotante (Toast) */
.toast-feedback {
  position: fixed;
  bottom: 20px;
  right: 20px;
  background-color: var(--success-color);
  color: white;
  padding: 12px 24px;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0,0,0,0.15);
  font-weight: 500;
  z-index: 1000;
}

.fade-enter-active, .fade-leave-active { 
  transition: opacity 0.3s ease; 
}

.fade-enter-from, .fade-leave-to { 
  opacity: 0; 
}
</style>