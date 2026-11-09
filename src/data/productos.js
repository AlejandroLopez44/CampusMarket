// src/data/productos.js
import { reactive } from 'vue'

export const estadoProductos = reactive({
  lista: [
    {
      id: 1,
      nombre: "Cuaderno Universitario 100 hojas",
      precio: 150.00,
      categoria: "Papelería",
      imagen: "https://images.unsplash.com/photo-1531346878377-a5be20888e57?auto=format&fit=crop&q=80&w=300&h=300"
    },
    {
      id: 2,
      nombre: "Calculadora Científica Casio",
      precio: 850.00,
      categoria: "Tecnología",
      imagen: "https://images.unsplash.com/photo-1594980596870-8aa52a78d8cd?auto=format&fit=crop&q=80&w=300&h=300"
    },
    {
      id: 3,
      nombre: "Bolígrafo Azul (Pack x3)",
      precio: 45.00,
      categoria: "Papelería",
      imagen: "https://images.unsplash.com/photo-1585336261022-680e295ce3fe?auto=format&fit=crop&q=80&w=300&h=300"
    },
    {
      id: 4,
      nombre: "Mochila Antirrobo para Laptop",
      precio: 1200.00,
      categoria: "Accesorios",
      imagen: "https://images.unsplash.com/photo-1553062407-98eeb64c6a62?auto=format&fit=crop&q=80&w=300&h=300"
    }
  ]
})

export const agregarNuevoProducto = (producto) => {
  estadoProductos.lista.push({
    id: Date.now(),
    ...producto
  })
}

// NUEVA: Eliminar producto (Sprint 2)
export const eliminarProducto = (id) => {
  estadoProductos.lista = estadoProductos.lista.filter(p => p.id !== id)
}

// NUEVA: Actualizar producto (Sprint 2)
export const actualizarProducto = (productoActualizado) => {
  const index = estadoProductos.lista.findIndex(p => p.id === productoActualizado.id)
  if (index !== -1) {
    // Reemplazamos el producto en la lista
    estadoProductos.lista[index] = { ...productoActualizado }
  }
}