<!-- src/App.vue -->
<script setup>
import { ref } from 'vue'
import RegistroForm from './components/RegistroForm.vue'
import LoginForm from './components/LoginForm.vue'
import CatalogoProductos from './components/CatalogoProductos.vue'
import CarritoCompras from './components/CarritoCompras.vue'
import CheckoutPago from './components/CheckoutPago.vue'
import { estadoCarrito } from './data/carrito.js'

const vistaActual = ref('registro')
</script>

<template>
  <div class="app-layout">
    <header class="main-header">
      <div class="container header-content">
        <h1>CampusMarket</h1>
        
        <nav>
          <button :class="['nav-btn', { active: vistaActual === 'productos' }]" @click="vistaActual = 'productos'">Productos</button>
          
          <button :class="['nav-btn', { active: vistaActual === 'carrito' || vistaActual === 'checkout' }]" @click="vistaActual = 'carrito'">
            Carrito 
            <span v-if="estadoCarrito.items.length > 0" class="badge">
              {{ estadoCarrito.items.reduce((acc, item) => acc + item.cantidad, 0) }}
            </span>
          </button>

          <button :class="['nav-btn', { active: vistaActual === 'registro' || vistaActual === 'login' }]" @click="vistaActual = 'registro'">Mi Cuenta</button>
        </nav>
      </div>
    </header>

    <main class="container">
      <!-- Cuenta -->
      <div v-if="vistaActual === 'registro' || vistaActual === 'login'">
        <div class="tabs-container">
          <button :class="['tab-btn', { active: vistaActual === 'registro' }]" @click="vistaActual = 'registro'">Registrarse</button>
          <button :class="['tab-btn', { active: vistaActual === 'login' }]" @click="vistaActual = 'login'">Iniciar Sesión</button>
        </div>
        <RegistroForm v-if="vistaActual === 'registro'" />
        <LoginForm v-if="vistaActual === 'login'" />
      </div>

      <!-- Productos -->
      <div v-if="vistaActual === 'productos'">
        <CatalogoProductos />
      </div>

      <!-- Carrito -->
      <div v-if="vistaActual === 'carrito'">
        <!-- Escuchamos el evento 'ir-a-pagar' para cambiar a la vista de checkout -->
        <CarritoCompras @ir-a-pagar="vistaActual = 'checkout'" />
      </div>

      <!-- Checkout / Pago -->
      <div v-if="vistaActual === 'checkout'">
        <CheckoutPago />
      </div>
    </main>
  </div>
</template>

<style scoped>
/* Los mismos estilos de antes */
.app-layout { min-height: 100vh; display: flex; flex-direction: column; }
.main-header { background-color: var(--card-bg); box-shadow: 0 2px 4px rgba(0,0,0,0.1); padding: 15px 0; margin-bottom: 30px; }
.header-content { display: flex; justify-content: space-between; align-items: center; padding: 0 20px; }
header h1 { color: var(--primary-color); font-size: 24px; }
nav { display: flex; gap: 15px; align-items: center; }
.nav-btn { background: none; border: none; font-size: 16px; font-weight: 500; color: var(--text-color); cursor: pointer; padding: 8px 12px; border-radius: 6px; transition: background-color 0.2s; position: relative; display: flex; align-items: center; gap: 5px; }
.nav-btn:hover { background-color: var(--bg-color); }
.nav-btn.active { color: var(--primary-color); background-color: #EEF2FF; }
.badge { background-color: var(--danger-color); color: white; font-size: 12px; font-weight: bold; padding: 2px 6px; border-radius: 50px; }
.tabs-container { display: flex; justify-content: center; gap: 10px; margin-bottom: 20px; }
.tab-btn { padding: 10px 20px; border: none; background: transparent; font-size: 16px; font-weight: bold; color: #6B7280; cursor: pointer; border-bottom: 3px solid transparent; transition: all 0.3s ease; }
.tab-btn:hover { color: var(--primary-color); }
.tab-btn.active { color: var(--primary-color); border-bottom: 3px solid var(--primary-color); }
</style>