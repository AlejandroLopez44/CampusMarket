// src/data/resenas.js
import { reactive } from 'vue'

export const estadoResenas = reactive({
  resenas: {
    1: [
      { id: 101, autor: "María G.", comentario: "Muy buen cuaderno, las hojas son gruesas.", estrellas: 5 },
      { id: 102, autor: "Carlos P.", comentario: "Cumple su función, aunque la portada es sencilla.", estrellas: 4 }
    ],
    2: [
      { id: 103, autor: "Ana R.", comentario: "La calculadora es excelente para cálculo y física.", estrellas: 5 }
    ]
  }
})

export const obtenerResenas = (idProducto) => {
  return estadoResenas.resenas[idProducto] || []
}

export const agregarResena = (idProducto, autor, comentario, estrellas) => {
  if (!estadoResenas.resenas[idProducto]) {
    estadoResenas.resenas[idProducto] = []
  }
  
  estadoResenas.resenas[idProducto].push({
    id: Date.now(),
    autor: autor,
    comentario: comentario,
    estrellas: estrellas
  })
}

// NUEVA: Eliminar reseña (Sprint 2)
export const eliminarResena = (idProducto, idResena) => {
  if (estadoResenas.resenas[idProducto]) {
    estadoResenas.resenas[idProducto] = estadoResenas.resenas[idProducto].filter(r => r.id !== idResena)
  }
}

// NUEVA: Modificar reseña (Sprint 2)
export const modificarResena = (idProducto, idResena, nuevoComentario, nuevasEstrellas) => {
  if (estadoResenas.resenas[idProducto]) {
    const resena = estadoResenas.resenas[idProducto].find(r => r.id === idResena)
    if (resena) {
      resena.comentario = nuevoComentario
      resena.estrellas = nuevasEstrellas
    }
  }
}