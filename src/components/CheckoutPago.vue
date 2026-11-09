<!-- src/components/CheckoutPago.vue -->
<script setup>
import { ref } from 'vue'
import { estadoCarrito, calcularTotal, generarCompra } from '../data/carrito.js'

const metodoPago = ref('tarjeta')
const mensajeExito = ref('')
const mostrarFormulario = ref(true)

// Datos simulados de la tarjeta
const tarjeta = ref({
  numero: '',
  titular: '',
  vencimiento: '',
  cvv: ''
})

const procesarPago = () => {
  // Validación básica para simular
  if (metodoPago.value === 'tarjeta' && tarjeta.value.numero.length < 16) {
    alert('Por favor, ingresa un número de tarjeta válido (16 dígitos).')
    return
  }

  const compraRealizada = generarCompra(metodoPago.value)
  
  if (compraRealizada) {
    mostrarFormulario.value = false
    mensajeExito.value = `¡Pago exitoso! Tu número de orden es #${compraRealizada.id}.`
  }
}
</script>

<template>
  <div class="checkout-container">
    
    <!-- Pantalla de Éxito (UX) -->
    <div v-if="!mostrarFormulario" class="exito-card">
      <div class="icono-exito">✓</div>
      <h2>¡Compra Realizada con Éxito!</h2>
      <p>{{ mensajeExito }}</p>
      <p class="subtexto">Puedes revisar el estado de tu compra en tu perfil.</p>
      <!-- Este botón debería llevar al perfil, pero por ahora recarga la página -->
      <button class="btn btn-primary" @click="window.location.reload()">Volver a la Tienda</button>
    </div>

    <!-- Formulario de Pago -->
    <div v-else class="pago-card">
      <h2>Finalizar Compra</h2>
      
      <div class="resumen-compra">
        <h3>Resumen de tu pedido</h3>
        <div v-for="item in estadoCarrito.items" :key="item.id" class="resumen-item">
          <span>{{ item.cantidad }}x {{ item.nombre }}</span>
          <span>Bs. {{ (item.precio * item.cantidad).toFixed(2) }}</span>
        </div>
        <div class="resumen-total">
          <strong>Total a Pagar:</strong>
          <strong class="monto">Bs. {{ calcularTotal().toFixed(2) }}</strong>
        </div>
      </div>

      <form @submit.prevent="procesarPago">
        <div class="form-group">
          <label>Método de Pago</label>
          <select v-model="metodoPago" class="form-select">
            <option value="tarjeta">Tarjeta de Crédito / Débito</option>
            <option value="pago-movil">Pago Móvil (Simulado)</option>
            <option value="efectivo">Efectivo en Tienda</option>
          </select>
        </div>

        <!-- Campos solo si es tarjeta -->
        <div v-if="metodoPago === 'tarjeta'" class="tarjeta-form">
          <div class="form-group">
            <label>Número de Tarjeta</label>
            <input type="text" v-model="tarjeta.numero" placeholder="0000 0000 0000 0000" maxlength="16" required>
          </div>
          <div class="form-group">
            <label>Titular de la Tarjeta</label>
            <input type="text" v-model="tarjeta.titular" placeholder="Nombre como aparece en la tarjeta" required>
          </div>
          <div class="row">
            <div class="form-group">
              <label>Vencimiento (MM/AA)</label>
              <input type="text" v-model="tarjeta.vencimiento" placeholder="MM/AA" required>
            </div>
            <div class="form-group">
              <label>CVV</label>
              <input type="text" v-model="tarjeta.cvv" placeholder="123" maxlength="4" required>
            </div>
          </div>
        </div>

        <div v-if="metodoPago === 'pago-movil'" class="info-alternativa">
          <p>Simulación: Se generará un código de pago móvil para que lo confirmes.</p>
        </div>

        <button type="submit" class="btn btn-primary w-100" style="margin-top: 20px;">
          Pagar Bs. {{ calcularTotal().toFixed(2) }}
        </button>
      </form>
    </div>
  </div>
</template>

<style scoped>
.checkout-container {
  max-width: 600px;
  margin: 0 auto;
}

.pago-card, .exito-card {
  background-color: var(--card-bg);
  padding: 30px;
  border-radius: 12px;
  box-shadow: 0 4px 6px rgba(0, 0, 0, 0.05);
}

h2 { text-align: center; margin-bottom: 20px; }

/* Estilos de Resumen */
.resumen-compra {
  background-color: var(--bg-color);
  padding: 15px;
  border-radius: 8px;
  margin-bottom: 20px;
}
.resumen-compra h3 { font-size: 16px; margin-bottom: 10px; border-bottom: 1px solid #ddd; padding-bottom: 5px;}
.resumen-item { display: flex; justify-content: space-between; font-size: 14px; margin-bottom: 5px; color: #4B5563;}
.resumen-total { display: flex; justify-content: space-between; margin-top: 15px; padding-top: 10px; border-top: 1px solid #ddd; }
.monto { color: var(--success-color); font-size: 18px; }

/* Formularios */
.form-select {
  padding: 10px;
  border: 1px solid #ccc;
  border-radius: 6px;
  font-size: 16px;
}
.row { display: flex; gap: 15px; }
.row .form-group { flex: 1; }

.info-alternativa {
  background-color: #FEF3C7;
  color: #92400E;
  padding: 15px;
  border-radius: 8px;
  text-align: center;
  font-size: 14px;
  margin-top: 15px;
}

/* Pantalla de Éxito */
.exito-card {
  text-align: center;
  padding: 50px 30px;
}
.icono-exito {
  width: 80px;
  height: 80px;
  background-color: var(--success-color);
  color: white;
  font-size: 40px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 20px auto;
}
.subtexto { color: #6B7280; margin-bottom: 30px; font-size: 14px; }
</style>