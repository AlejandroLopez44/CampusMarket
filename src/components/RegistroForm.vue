<!-- src/components/RegistroForm.vue -->
<script setup>
import { ref } from 'vue'

// Datos del formulario
const formulario = ref({
  nombre: '',
  correo: '',
  password: ''
})

// Estados para controlar la interfaz
const mensajeExito = ref('')
const mensajeError = ref('')

const manejarRegistro = () => {
  // Limpiamos mensajes previos
  mensajeExito.value = ''
  mensajeError.value = ''

  // Validación básica (UX: evitar contraseñas muy cortas)
  if (formulario.value.password.length < 6) {
    mensajeError.value = 'La contraseña debe tener al menos 6 caracteres.'
    return
  }

  // Simulación de éxito
  console.log('Usuario registrado:', formulario.value)
  mensajeExito.value = `¡Bienvenido, ${formulario.value.nombre}! Tu cuenta ha sido creada.`
  
  // Limpiamos el formulario
  formulario.value = {
    nombre: '',
    correo: '',
    password: ''
  }
}
</script>

<template>
  <div class="form-card">
    <h2>Crear Cuenta</h2>
    <p class="subtitle">Únete a CampusMarket y compra tus insumos</p>

    <!-- Mensaje de Error -->
    <div v-if="mensajeError" class="alert alert-error">
      {{ mensajeError }}
    </div>

    <!-- Mensaje de Éxito -->
    <div v-if="mensajeExito" class="alert alert-success">
      {{ mensajeExito }}
    </div>

    <form @submit.prevent="manejarRegistro">
      <div class="form-group">
        <label for="nombre">Nombre Completo</label>
        <input 
          type="text" 
          id="nombre" 
          v-model="formulario.nombre" 
          placeholder="Ej. Juan Pérez" 
          required
        >
      </div>

      <div class="form-group">
        <label for="correo">Correo Institucional</label>
        <input 
          type="email" 
          id="correo" 
          v-model="formulario.correo" 
          placeholder="estudiante@est.ucab.edu.ve" 
          required
        >
      </div>

      <div class="form-group">
        <label for="password">Contraseña (mín. 6 caracteres)</label>
        <input 
          type="password" 
          id="password" 
          v-model="formulario.password" 
          placeholder="********" 
          required
        >
      </div>

      <button type="submit" class="btn btn-primary w-100">
        Registrarse
      </button>
    </form>
  </div>
</template>

<style scoped>
.form-card {
  background-color: var(--card-bg);
  padding: 30px;
  border-radius: 12px;
  box-shadow: 0 4px 6px rgba(0, 0, 0, 0.05);
  max-width: 400px;
  margin: 0 auto;
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
  margin-bottom: 25px;
}

.w-100 {
  width: 100%;
  margin-top: 10px;
}

/* Estilos de las alertas */
.alert {
  padding: 10px;
  border-radius: 6px;
  margin-bottom: 15px;
  font-size: 14px;
  text-align: center;
  font-weight: 500;
}

.alert-error {
  background-color: #FEE2E2;
  color: var(--danger-color);
  border: 1px solid #F87171;
}

.alert-success {
  background-color: #D1FAE5;
  color: var(--success-color);
  border: 1px solid #34D399;
}
</style>