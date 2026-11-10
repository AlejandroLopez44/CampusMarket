// Configuración de Vite (no es código de negocio).
// - root = carpeta frontend/ (index.html aquí; el código vive en view/ y control/).
// - proxy /api → backend Spring Boot: mismo origen para el navegador, así la
//   cookie de sesión (JSESSIONID) viaja sin configurar CORS.
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [vue(), tailwindcss()],
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
})
