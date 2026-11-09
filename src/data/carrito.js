// src/data/carrito.js
import { reactive } from 'vue'

export const estadoCarrito = reactive({
  items: [],
  historialCompras: []
})

export const agregarProducto = (producto) => {
  const itemExistente = estadoCarrito.items.find(item => item.id === producto.id)
  if (itemExistente) {
    itemExistente.cantidad++
  } else {
    estadoCarrito.items.push({ ...producto, cantidad: 1 })
  }
}

// NUEVA: Modificar cantidad (Sprint 2)
export const modificarCantidad = (idProducto, nuevaCantidad) => {
  const item = estadoCarrito.items.find(item => item.id === idProducto)
  if (item) {
    // Si la cantidad baja a 0, eliminamos el producto automáticamente
    if (nuevaCantidad <= 0) {
      eliminarProducto(idProducto)
    } else {
      item.cantidad = nuevaCantidad
    }
  }
}

// NUEVA: Eliminar producto del carrito (Sprint 2)
export const eliminarProducto = (idProducto) => {
  estadoCarrito.items = estadoCarrito.items.filter(item => item.id !== idProducto)
}

export const calcularTotal = () => {
  return estadoCarrito.items.reduce((total, item) => total + (item.precio * item.cantidad), 0)
}

export const vaciarCarrito = () => {
  estadoCarrito.items = []
}

export const generarCompra = (metodoPago) => {
  if (estadoCarrito.items.length === 0) return null

  const nuevaCompra = {
    id: Date.now(),
    fecha: new Date().toLocaleDateString(),
    items: [...estadoCarrito.items],
    total: calcularTotal(),
    metodoPago: metodoPago
  }

  estadoCarrito.historialCompras.push(nuevaCompra)
  vaciarCarrito()
  return nuevaCompra
}