<!-- src/components/ModalResenas.vue -->
<script setup>
import { ref, computed } from 'vue'
import { obtenerResenas, agregarResena, eliminarResena, modificarResena } from '../data/resenas.js'

const props = defineProps({
  producto: Object
})

const emit = defineEmits(['cerrar'])

// Simulamos el usuario logueado actualmente (debe coincidir con el nombre que usas al reseñar)
const usuarioActual = ref('Alejandro López') 
// Simulamos si el usuario es admin (para permitir eliminar cualquier reseña)
const esAdmin = ref(true) 

const resenasDelProducto = computed(() => obtenerResenas(props.producto.id))
const nuevaResena = ref({ autor: '', comentario: '', estrellas: 5 })
const mensajeFeedback = ref('')

// Estados para la edición
const resenaEnEdicion = ref(null)
const comentarioEditado = ref('')
const estrellasEditadas = ref(5)

const enviarResena = () => {
  if (nuevaResena.value.comentario.trim() === '') return

  const yaExiste = resenasDelProducto.value.some(r => r.autor.toLowerCase() === nuevaResena.value.autor.toLowerCase())
  
  if (yaExiste) {
    mensajeFeedback.value = 'Ya has dejado una reseña para este producto.'
    return
  }

  agregarResena(props.producto.id, nuevaResena.value.autor, nuevaResena.value.comentario, nuevaResena.value.estrellas)
  mensajeFeedback.value = '¡Reseña publicada con éxito!'
  nuevaResena.value = { autor: '', comentario: '', estrellas: 5 }
  setTimeout(() => { mensajeFeedback.value = '' }, 3000)
}

// Preparar edición
const prepararEdicion = (resena) => {
  resenaEnEdicion.value = resena.id
  comentarioEditado.value = resena.comentario
  estrellasEditadas.value = resena.estrellas
}

const guardarEdicion = (idResena) => {
  modificarResena(props.producto.id, idResena, comentarioEditado.value, estrellasEditadas.value)
  resenaEnEdicion.value = null
  mensajeFeedback.value = 'Reseña actualizada correctamente.'
  setTimeout(() => { mensajeFeedback.value = '' }, 3000)
}

const borrarResena = (idResena) => {
  if (confirm('¿Estás seguro de eliminar esta reseña?')) {
    eliminarResena(props.producto.id, idResena)
    mensajeFeedback.value = 'Reseña eliminada.'
    setTimeout(() => { mensajeFeedback.value = '' }, 3000)
  }
}
</script>

<template>
  <div class="modal-overlay" @click.self="emit('cerrar')">
    <div class="modal-content">
      <button class="btn-cerrar" @click="emit('cerrar')">✕</button>
      
      <h2>Reseñas de {{ producto.nombre }}</h2>

      <div class="lista-resenas">
        <p v-if="resenasDelProducto.length === 0" class="sin-resenas">Aún no hay reseñas para este producto.</p>
        
        <div v-for="resena in resenasDelProducto" :key="resena.id" class="resena-item">
          
          <!-- Modo Edición -->
          <div v-if="resenaEnEdicion === resena.id" class="edicion-form">
            <div class="form-group">
              <label>Calificación</label>
              <div class="estrellas-input">
                <span v-for="n in 5" :key="n" @click="estrellasEditadas = n" :class="['estrella-btn', { activa: n <= estrellasEditadas }]">★</span>
              </div>
            </div>
            <div class="form-group">
              <textarea v-model="comentarioEditado" rows="2"></textarea>
            </div>
            <div class="edicion-acciones">
              <button @click="guardarEdicion(resena.id)" class="btn btn-primary btn-sm">Guardar</button>
              <button @click="resenaEnEdicion = null" class="btn btn-secondary btn-sm">Cancelar</button>
            </div>
          </div>

          <!-- Modo Visualización -->
          <div v-else>
            <div class="resena-header">
              <strong>{{ resena.autor }}</strong>
              <div class="header-derecha">
                <span class="estrellas">{{ '★'.repeat(resena.estrellas) }}{{ '☆'.repeat(5 - resena.estrellas) }}</span>
                
                <!-- Botones de acción (Solo si es el autor o es Admin) -->
                <div v-if="resena.autor === usuarioActual || esAdmin" class="acciones-resena">
                  <button @click="prepararEdicion(resena)" class="btn-icono" title="Editar">✏️</button>
                  <button @click="borrarResena(resena.id)" class="btn-icono" title="Eliminar">🗑️</button>
                </div>
              </div>
            </div>
            <p>{{ resena.comentario }}</p>
          </div>

        </div>
      </div>

      <hr class="divisor">

      <div class="formulario-resena">
        <h3>Escribir una reseña</h3>
        
        <div v-if="mensajeFeedback" class="feedback-msg">{{ mensajeFeedback }}</div>

        <form @submit.prevent="enviarResena">
          <div class="form-group">
            <label>Tu Nombre</label>
            <input type="text" v-model="nuevaResena.autor" placeholder="Ej. Alejandro López" required>
          </div>
          <div class="form-group">
            <label>Calificación</label>
            <div class="estrellas-input">
              <span v-for="n in 5" :key="n" @click="nuevaResena.estrellas = n" :class="['estrella-btn', { activa: n <= nuevaResena.estrellas }]">★</span>
            </div>
          </div>
          <div class="form-group">
            <label>Comentario</label>
            <textarea v-model="nuevaResena.comentario" rows="3" placeholder="¿Qué te pareció el producto?" required></textarea>
          </div>
          <button type="submit" class="btn btn-primary w-100">Publicar Reseña</button>
        </form>
      </div>
    </div>
  </div>
</template>

<style scoped>
.modal-overlay { position: fixed; top: 0; left: 0; width: 100%; height: 100%; background-color: rgba(0,0,0,0.5); display: flex; justify-content: center; align-items: center; z-index: 2000; padding: 20px; }
.modal-content { background-color: var(--card-bg); padding: 30px; border-radius: 12px; width: 100%; max-width: 500px; max-height: 90vh; overflow-y: auto; position: relative; }
.btn-cerrar { position: absolute; top: 15px; right: 15px; background: none; border: none; font-size: 20px; cursor: pointer; color: #6B7280; }
h2 { text-align: center; font-size: 20px; margin-bottom: 20px; }

.lista-resenas { margin-bottom: 20px; max-height: 250px; overflow-y: auto; }
.sin-resenas { color: #6B7280; font-size: 14px; text-align: center; font-style: italic; }

.resena-item { background-color: var(--bg-color); padding: 10px 15px; border-radius: 8px; margin-bottom: 10px; }
.resena-header { display: flex; justify-content: space-between; margin-bottom: 5px; }
.header-derecha { display: flex; align-items: center; gap: 10px; }
.estrellas { color: #F59E0B; font-size: 14px; }
.resena-item p { font-size: 14px; color: #4B5563; }

.acciones-resena { display: flex; gap: 2px; }
.btn-icono { background: none; border: none; cursor: pointer; font-size: 12px; opacity: 0.7; padding: 2px; }
.btn-icono:hover { opacity: 1; }

.edicion-form { margin-top: 5px; }
.edicion-form textarea { width: 100%; padding: 8px; border: 1px solid #ccc; border-radius: 6px; font-family: inherit; }
.edicion-acciones { display: flex; gap: 10px; margin-top: 10px; }
.btn-sm { padding: 5px 10px; font-size: 12px; }

.divisor { border: 0; height: 1px; background-color: #E5E7EB; margin: 20px 0; }
.formulario-resena h3 { margin-bottom: 15px; font-size: 16px; }
.feedback-msg { background-color: #D1FAE5; color: var(--success-color); padding: 10px; border-radius: 6px; margin-bottom: 15px; text-align: center; font-size: 14px; }
.estrellas-input { display: flex; gap: 5px; cursor: pointer; font-size: 24px; color: #D1D5DB; }
.estrella-btn { transition: color 0.2s; }
.estrella-btn.activa { color: #F59E0B; }
textarea { padding: 10px; border: 1px solid #ccc; border-radius: 6px; font-family: inherit; resize: vertical; width: 100%; }
</style>