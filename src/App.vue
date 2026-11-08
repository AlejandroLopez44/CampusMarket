<!-- src/App.vue -->
<script setup>
import { ref } from 'vue'
import RegistroForm from './components/RegistroForm.vue'
import LoginForm from './components/LoginForm.vue'

// Estado para saber qué formulario mostrar
const vistaActual = ref('registro') // Puede ser 'registro' o 'login'
</script>

<template>
  <div class="app-layout">
    <header class="main-header">
      <div class="container header-content">
        <h1>CampusMarket</h1>
        <nav>
          <a href="#">Productos</a>
          <a href="#">Carrito</a>
          <a href="#">Mi Perfil</a>
        </nav>
      </div>
    </header>

    <main class="container">
      <!-- Botones para alternar entre Login y Registro -->
      <div class="tabs-container">
        <button 
          :class="['tab-btn', { active: vistaActual === 'registro' }]" 
          @click="vistaActual = 'registro'"
        >
          Registrarse
        </button>
        <button 
          :class="['tab-btn', { active: vistaActual === 'login' }]" 
          @click="vistaActual = 'login'"
        >
          Iniciar Sesión
        </button>
      </div>

      <!-- Renderizado condicional de los formularios -->
      <RegistroForm v-if="vistaActual === 'registro'" />
      <LoginForm v-if="vistaActual === 'login'" />
    </main>
  </div>
</template>

<style scoped>
.app-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.main-header {
  background-color: var(--card-bg);
  box-shadow: 0 2px 4px rgba(0,0,0,0.1);
  padding: 15px 0;
  margin-bottom: 30px;
}

.header-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 20px;
}

header h1 {
  color: var(--primary-color);
  font-size: 24px;
}

nav {
  display: flex;
  gap: 20px;
}

nav a {
  text-decoration: none;
  color: var(--text-color);
  font-weight: 500;
}

nav a:hover {
  color: var(--primary-color);
}

/* Estilos de las pestañas */
.tabs-container {
  display: flex;
  justify-content: center;
  gap: 10px;
  margin-bottom: 20px;
}

.tab-btn {
  padding: 10px 20px;
  border: none;
  background: transparent;
  font-size: 16px;
  font-weight: bold;
  color: #6B7280;
  cursor: pointer;
  border-bottom: 3px solid transparent;
  transition: all 0.3s ease;
}

.tab-btn:hover {
  color: var(--primary-color);
}

.tab-btn.active {
  color: var(--primary-color);
  border-bottom: 3px solid var(--primary-color);
}
</style>