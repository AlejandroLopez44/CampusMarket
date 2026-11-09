<!-- src/components/CarritoCompras.vue -->
<script setup>
import { estadoCarrito, calcularTotal, modificarCantidad, eliminarProducto } from '../data/carrito.js'

const emit = defineEmits(['ir-a-pagar'])
</script>

<template>
  <div class="carrito-container">
    <h2>Tu Carrito de Compras</h2>
    
    <div v-if="estadoCarrito.items.length === 0" class="carrito-vacio">
      <p>Tu carrito está vacío. ¡Ve a la sección de productos para agregar insumos!</p>
    </div>

    <div v-else class="carrito-lista">
      <div v-for="item in estadoCarrito.items" :key="item.id" class="carrito-item">
        <img :src="item.imagen" :alt="item.nombre" class="item-img">
        
        <div class="item-detalles">
          <h3>{{ item.nombre }}</h3>
          <p>Precio unitario: Bs. {{ item.precio.toFixed(2) }}</p>
        </div>

        <!-- Controles de cantidad (Sprint 2) -->
        <div class="item-cantidad-controles">
          <button class="btn-cantidad" @click="modificarCantidad(item.id, item.cantidad - 1)">-</button>
          <span class="cantidad-numero">{{ item.cantidad }}</span>
          <button class="btn-cantidad" @click="modificarCantidad(item.id, item.cantidad + 1)">+</button>
        </div>

        <div class="item-subtotal">
          <p>Bs. {{ (item.precio * item.cantidad).toFixed(2) }}</p>
          <!-- Botón de eliminar (Sprint 2) -->
          <button class="btn-eliminar" @click="eliminarProducto(item.id)" title="Eliminar producto">
            🗑️
          </button>
        </div>
      </div>

      <div class="carrito-total">
        <h3>Total a Pagar:</h3>
        <h3 class="total-monto">Bs. {{ calcularTotal().toFixed(2) }}</h3>
      </div>

      <div class="carrito-acciones">
        <button @click="emit('ir-a-pagar')" class="btn btn-primary" style="width: 100%; padding: 15px; font-size: 16px;">
          Proceder al Pago
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.carrito-container { 
  max-width: 800px; 
  margin: 0 auto; 
  background-color: var(--card-bg); 
  padding: 30px; 
  border-radius: 12px; 
  box-shadow: 0 4px 6px rgba(0, 0, 0, 0.05); 
}

h2 { text-align: center; color: var(--text-color); margin-bottom: 20px; }

.carrito-vacio { 
  text-align: center; 
  padding: 40px; 
  color: #6B7280; 
  background-color: var(--bg-color); 
  border-radius: 8px; 
}

.carrito-item { 
  display: flex; 
  align-items: center; 
  gap: 15px; 
  padding: 15px 0; 
  border-bottom: 1px solid #E5E7EB; 
}

.item-img { 
  width: 60px; 
  height: 60px; 
  object-fit: cover; 
  border-radius: 8px; 
}

.item-detalles { flex-grow: 1; }
.item-detalles h3 { font-size: 16px; margin-bottom: 5px; }
.item-detalles p { font-size: 14px; color: #6B7280; }

/* Estilos de los controles de cantidad */
.item-cantidad-controles {
  display: flex;
  align-items: center;
  gap: 10px;
  background-color: var(--bg-color);
  padding: 5px;
  border-radius: 8px;
}

.btn-cantidad {
  background-color: white;
  border: 1px solid #D1D5DB;
  border-radius: 4px;
  width: 30px;
  height: 30px;
  font-size: 18px;
  font-weight: bold;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background-color 0.2s;
}

.btn-cantidad:hover { background-color: #F3F4F6; }

.cantidad-numero { font-weight: bold; min-width: 20px; text-align: center; }

.item-subtotal { 
  font-weight: bold; 
  color: var(--text-color); 
  min-width: 120px; 
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
}

/* Estilos del botón eliminar */
.btn-eliminar {
  background: none;
  border: none;
  font-size: 18px;
  cursor: pointer;
  opacity: 0.6;
  transition: opacity 0.2s, transform 0.2s;
}

.btn-eliminar:hover { 
  opacity: 1; 
  transform: scale(1.1);
}

.carrito-total { 
  display: flex; 
  justify-content: space-between; 
  align-items: center; 
  padding: 20px 0; 
  margin-top: 10px; 
  border-top: 2px solid #E5E7EB; 
}

.total-monto { color: var(--success-color); font-size: 24px; }
.carrito-acciones { margin-top: 20px; }
</style>