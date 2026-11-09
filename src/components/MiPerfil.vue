<!-- src/components/MiPerfil.vue -->
<script setup>
import { ref } from 'vue'
import { estadoCarrito } from '../data/carrito.js'

const usuario = ref({
  nombre: 'Alejandro López',
  correo: 'alejandro@correo.com',
  rol: 'Estudiante'
})

const usuarioEditado = ref({ ...usuario.value })
const modoEdicion = ref(false)
const mensajeExito = ref('')
const historial = ref(estadoCarrito.historialCompras)

const guardarCambios = () => {
  if (usuarioEditado.value.nombre.trim() === '' || usuarioEditado.value.correo.trim() === '') {
    alert('Los campos no pueden estar vacíos.')
    return
  }
  usuario.value = { ...usuarioEditado.value }
  modoEdicion.value = false
  mensajeExito.value = 'Perfil actualizado correctamente.'
  setTimeout(() => { mensajeExito.value = '' }, 3000)
}

const cancelarEdicion = () => {
  usuarioEditado.value = { ...usuario.value }
  modoEdicion.value = false
}

const eliminarPerfil = () => {
  const confirmacion = confirm('¿Estás seguro de que deseas eliminar tu cuenta? Esta acción no se puede deshacer.')
  if (confirmacion) {
    alert('Cuenta eliminada. Serás redirigido a la página de inicio.')
    window.location.reload()
  }
}

// NUEVA: Función para Descargar Biblioteca Virtual / Comprobante (Sprint 2)
const descargarComprobante = (compra) => {
  // Creamos el contenido del archivo de texto
  let contenido = `=========================================\n`
  contenido += `      CAMPUSMARKET - COMPROBANTE DE PAGO\n`
  contenido += `=========================================\n\n`
  contenido += `Orden N°: ${compra.id}\n`
  contenido += `Fecha: ${compra.fecha}\n`
  contenido += `Cliente: ${usuario.value.nombre}\n`
  contenido += `Método de Pago: ${compra.metodoPago}\n\n`
  contenido += `--- DETALLE DE PRODUCTOS ---\n`
  
  compra.items.forEach(item => {
    contenido += `${item.cantidad}x ${item.nombre} - Bs. ${(item.precio * item.cantidad).toFixed(2)}\n`
  })
  
  contenido += `\n-----------------------------------------\n`
  contenido += `TOTAL PAGADO: Bs. ${compra.total.toFixed(2)}\n`
  contenido += `=========================================\n`
  contenido += `¡Gracias por tu compra en CampusMarket!\n`

  // Creamos un Blob (archivo en memoria) y forzamos la descarga
  const blob = new Blob([contenido], { type: 'text/plain;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  
  const link = document.createElement('a')
  link.href = url
  link.download = `Comprobante_CampusMarket_${compra.id}.txt`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}
</script>

<template>
  <div class="perfil-container">
    <div v-if="mensajeExito" class="alert-success">
      {{ mensajeExito }}
    </div>

    <div class="perfil-header">
      <div class="avatar">{{ usuario.nombre.charAt(0) }}</div>
      
      <div class="info">
        <div v-if="!modoEdicion">
          <h2>{{ usuario.nombre }}</h2>
          <p>{{ usuario.correo }}</p>
          <span class="rol-badge">{{ usuario.rol }}</span>
          <button @click="modoEdicion = true" class="btn btn-secondary btn-sm" style="margin-top: 10px;">
            ✏️ Editar Perfil
          </button>
        </div>

        <div v-else class="form-edicion">
          <div class="form-group">
            <label>Nombre</label>
            <input type="text" v-model="usuarioEditado.nombre" required>
          </div>
          <div class="form-group">
            <label>Correo</label>
            <input type="email" v-model="usuarioEditado.correo" required>
          </div>
          <div class="acciones-edicion">
            <button @click="guardarCambios" class="btn btn-primary btn-sm">Guardar</button>
            <button @click="cancelarEdicion" class="btn btn-secondary btn-sm">Cancelar</button>
          </div>
        </div>
      </div>
    </div>

    <div class="historial-section">
      <h3>Historial de Compras</h3>
      
      <div v-if="historial.length === 0" class="sin-compras">
        <p>No tienes compras registradas aún.</p>
      </div>

      <div v-else class="lista-compras">
        <div v-for="compra in historial" :key="compra.id" class="compra-card">
          <div class="compra-header">
            <strong>Orden #{{ compra.id }}</strong>
            <span>{{ compra.fecha }}</span>
          </div>
          <div class="compra-detalles">
            <p><strong>Método de pago:</strong> {{ compra.metodoPago }}</p>
            <p><strong>Total:</strong> Bs. {{ compra.total.toFixed(2) }}</p>
            <p><strong>Productos:</strong> {{ compra.items.length }} artículos</p>
            
            <!-- Botón de Descarga (Sprint 2) -->
            <button @click="descargarComprobante(compra)" class="btn btn-primary btn-sm" style="margin-top: 10px;">
              📥 Descargar Comprobante
            </button>
          </div>
        </div>
      </div>
    </div>

    <div class="zona-peligro">
      <h3>Zona de Peligro</h3>
      <p>Una vez que elimines tu cuenta, no hay vuelta atrás. Por favor, asegúrate de que es lo que deseas.</p>
      <button @click="eliminarPerfil" class="btn btn-danger">Eliminar mi cuenta permanentemente</button>
    </div>
  </div>
</template>

<style scoped>
/* Los mismos estilos que ya tenías */
.perfil-container { max-width: 600px; margin: 0 auto; background-color: var(--card-bg); padding: 30px; border-radius: 12px; box-shadow: 0 4px 6px rgba(0, 0, 0, 0.05); }
.alert-success { background-color: #D1FAE5; color: var(--success-color); border: 1px solid #34D399; padding: 10px; border-radius: 6px; margin-bottom: 15px; font-size: 14px; text-align: center; font-weight: 500; }
.perfil-header { display: flex; align-items: flex-start; gap: 20px; border-bottom: 1px solid #E5E7EB; padding-bottom: 20px; margin-bottom: 20px; }
.avatar { width: 70px; height: 70px; background-color: var(--primary-color); color: white; font-size: 30px; font-weight: bold; border-radius: 50%; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.info { flex-grow: 1; }
.info h2 { margin-bottom: 5px; }
.info p { color: #6B7280; font-size: 14px; }
.rol-badge { display: inline-block; background-color: #EEF2FF; color: var(--primary-color); padding: 2px 8px; border-radius: 4px; font-size: 12px; font-weight: bold; margin-top: 5px; }
.form-edicion { display: flex; flex-direction: column; gap: 10px; }
.acciones-edicion { display: flex; gap: 10px; margin-top: 5px; }
.btn-sm { padding: 6px 12px; font-size: 13px; }
.historial-section h3 { margin-bottom: 15px; }
.sin-compras { text-align: center; color: #6B7280; padding: 20px; background-color: var(--bg-color); border-radius: 8px; }
.compra-card { border: 1px solid #E5E7EB; border-radius: 8px; margin-bottom: 15px; overflow: hidden; }
.compra-header { background-color: var(--bg-color); padding: 10px 15px; display: flex; justify-content: space-between; font-size: 14px; }
.compra-detalles { padding: 15px; font-size: 14px; color: #4B5563; }
.compra-detalles p { margin-bottom: 5px; }
.zona-peligro { margin-top: 40px; padding-top: 20px; border-top: 2px dashed #FCA5A5; }
.zona-peligro h3 { color: var(--danger-color); font-size: 16px; margin-bottom: 10px; }
.zona-peligro p { font-size: 13px; color: #6B7280; margin-bottom: 15px; }
</style>