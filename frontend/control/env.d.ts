/// <reference types="vite/client" />
// Declaración para que TypeScript reconozca los archivos .vue como módulos.
declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const componente: DefineComponent<object, object, unknown>
  export default componente
}
