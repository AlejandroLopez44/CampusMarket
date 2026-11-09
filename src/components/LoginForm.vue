<!-- src/components/LoginForm.vue -->
<script setup>
import { ref } from 'vue'

const formulario = ref({
  correo: '',
  password: ''
})

const mensajeError = ref('')
const mensajeExito = ref('')

const manejarLogin = () => {
  mensajeError.value = ''
  mensajeExito.value = ''

  // Simulación de validación de credenciales
  if (formulario.value.correo === 'test@est.ucab.edu.ve.com' && formulario.value.password === '123456') {
    mensajeExito.value = '¡Bienvenido de vuelta! Redirigiendo...'
    console.log('Login exitoso')
  } else {
    mensajeError.value = 'Correo o contraseña incorrectos. (Prueba con test@est.ucab.edu.ve.com / 123456)'
  }
}
</script>

<template>
  <div class="form-card">
    <h2>Iniciar Sesión</h2>
    <p class="subtitle">Accede a tu cuenta de CampusMarket</p>

    <div v-if="mensajeError" class="alert alert-error">
      {{ mensajeError }}
    </div>

    <div v-if="mensajeExito" class="alert alert-success">
      {{ mensajeExito }}
    </div>

    <form @submit.prevent="manejarLogin">
      <div class="form-group">
        <label for="correo-login">Correo Institucional</label>
        <input 
          type="email" 
          id="correo-login" 
          v-model="formulario.correo" 
          placeholder="estudiante@est.ucab.edu.ve.com" 
          required
        >
      </div>

      <div class="form-group">
        <label for="password-login">Contraseña</label>
        <input 
          type="password" 
          id="password-login" 
          v-model="formulario.password" 
          placeholder="********" 
          required
        >
      </div>

      <button type="submit" class="btn btn-primary w-100">
        Entrar
      </button>
    </form>
  </div>
</template>

<style scoped>
/* Reutilizamos los mismos estilos base del registro */
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