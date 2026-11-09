<!-- src/components/AdminProductos.vue -->
<script setup>
import { ref } from 'vue'
import { estadoProductos, agregarNuevoProducto, eliminarProducto, actualizarProducto } from '../data/productos.js'

// Modo: 'agregar' o 'editar'
const modo = ref('agregar')
const mensajeExito = ref('')

// Modelo del formulario
const productoForm = ref({
  id: null,
  nombre: '',
  precio: null,
  categoria: 'Papelería',
  imagen: ''
})

const prepararAgregar = () => {
  modo.value = 'agregar'
  productoForm.value = { id: null, nombre: '', precio: null, categoria: 'Papelería', imagen: '' }
}

const prepararEditar = (producto) => {
  modo.value = 'editar'
  // Hacemos una copia para no modificar la tabla directamente
  productoForm.value = { ...producto }
}

const guardarProducto = () => {
  const imagenFinal = productoForm.value.imagen || 'https://images.unsplash.com/photo-1531346878377-a5be20888e57?auto=format&fit=crop&q=80&w=300&h=300'
  
  const datos = {
    ...productoForm.value,
    precio: parseFloat(productoForm.value.precio),
    imagen: imagenFinal
  }

  if (modo.value === 'agregar') {
    agregarNuevoProducto(datos)
    mensajeExito.value = '¡Producto agregado exitosamente!'
  } else {
    actualizarProducto(datos)
    mensajeExito.value = '¡Producto actualizado exitosamente!'
  }

  prepararAgregar() // Limpiar y volver a modo agregar
  setTimeout(() => { mensajeExito.value = '' }, 3000)
}

const confirmarEliminar = (id, nombre) => {
  if (confirm(`¿Estás seguro de eliminar "${nombre}" del catálogo?`)) {
    eliminarProducto(id)
    mensajeExito.value = 'Producto eliminado.'
    setTimeout(() => { mensajeExito.value = '' }, 3000)
  }
}
</script>

<template>
  <div class="admin-container">
    <h2>Panel de Administrador</h2>
    <p class="subtitle">Gestiona el inventario de CampusMarket</p>

    <div v-if="mensajeExito" class="alert alert-success">
      {{ mensajeExito }}
    </div>

    <!-- Formulario -->
    <div class="form-section">
      <div class="form-header">
        <h3>{{ modo === 'agregar' ? 'Agregar Nuevo Producto' : 'Editar Producto' }}</h3>
        <button v-if="modo === 'editar'" @click="prepararAgregar" class="btn-cancelar">
          Cancelar Edición
        </button>
      </div>

      <form @submit.prevent="guardarProducto">
        <div class="form-row">
          <div class="form-group">
            <label>Nombre</label>
            <input type="text" v-model="productoForm.nombre" placeholder="Ej. Regla 30cm" required>
          </div>
          <div class="form-group">
            <label>Precio (Bs.)</label>
            <input type="number" step="0.01" v-model="productoForm.precio" placeholder="Ej. 50.00" required>
          </div>
        </div>

        <div class="form-row">
          <div class="form-group">
            <label>Categoría</label>
            <select v-model="productoForm.categoria" class="form-select">
              <option value="Papelería">Papelería</option>
              <option value="Tecnología">Tecnología</option>
              <option value="Accesorios">Accesorios</option>
              <option value="Libros">Libros</option>
            </select>
          </div>
          <div class="form-group">
            <label>URL de la Imagen (Opcional)</label>
            <input type="url" v-model="productoForm.imagen" placeholder="https://...">
          </div>
        </div>

        <button type="submit" class="btn btn-primary w-100">
          {{ modo === 'agregar' ? 'Agregar Producto' : 'Guardar Cambios' }}
        </button>
      </form>
    </div>

    <!-- Tabla de Inventario -->
    <div class="inventario-section">
      <h3>Inventario Actual ({{ estadoProductos.lista.length }} productos)</h3>
      
      <table class="tabla-inventario">
        <thead>
          <tr>
            <th>Producto</th>
            <th>Precio</th>
            <th>Categoría</th>
            <th>Acciones</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="producto in estadoProductos.lista" :key="producto.id">
            <td>{{ producto.nombre }}</td>
            <td>Bs. {{ producto.precio.toFixed(2) }}</td>
            <td>{{ producto.categoria }}</td>
            <td class="acciones-td">
              <button @click="prepararEditar(producto)" class="btn-accion btn-editar" title="Editar">✏️</button>
              <button @click="confirmarEliminar(producto.id, producto.nombre)" class="btn-accion btn-borrar" title="Eliminar">🗑️</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<style scoped>
.admin-container { max-width: 900px; margin: 0 auto; background-color: var(--card-bg); padding: 30px; border-radius: 12px; box-shadow: 0 4px 6px rgba(0, 0, 0, 0.05); }
h2 { text-align: center; margin-bottom: 5px; }
.subtitle { text-align: center; color: #6B7280; font-size: 14px; margin-bottom: 25px; }

.alert-success { background-color: #D1FAE5; color: var(--success-color); border: 1px solid #34D399; padding: 10px; border-radius: 6px; margin-bottom: 15px; font-size: 14px; text-align: center; font-weight: 500; }

.form-section { background-color: var(--bg-color); padding: 20px; border-radius: 8px; margin-bottom: 30px; }
.form-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 15px; }
.form-header h3 { font-size: 16px; }
.btn-cancelar { background: none; border: none; color: var(--danger-color); cursor: pointer; font-size: 14px; font-weight: 500; }

.form-row { display: flex; gap: 15px; margin-bottom: 15px; }
.form-row .form-group { flex: 1; margin-bottom: 0; }
.form-select { padding: 10px; border: 1px solid #ccc; border-radius: 6px; font-size: 16px; width: 100%; }
.w-100 { width: 100%; }

.inventario-section h3 { font-size: 16px; margin-bottom: 15px; }
.tabla-inventario { width: 100%; border-collapse: collapse; }
.tabla-inventario th, .tabla-inventario td { padding: 12px 10px; text-align: left; border-bottom: 1px solid #E5E7EB; font-size: 14px; }
.tabla-inventario th { background-color: var(--bg-color); font-weight: 600; color: #4B5563; }
.acciones-td { display: flex; gap: 5px; }

.btn-accion { background: none; border: none; font-size: 18px; cursor: pointer; padding: 5px; border-radius: 4px; transition: background-color 0.2s; }
.btn-accion:hover { background-color: #E5E7EB; }
.btn-editar { color: #3B82F6; }
.btn-borrar { color: var(--danger-color); }
</style>