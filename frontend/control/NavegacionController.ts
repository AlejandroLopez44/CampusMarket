/**
 * NavegacionController — Tipos compartidos del estado de interfaz (capa CONTROL).
 * Responsabilidad única: definir los destinos de navegación y la forma de los
 * botones de diálogo, para que las vistas compartan un contrato tipado
 * sin depender unas de otras.
 */

/** Vistas a las que se puede navegar desde cualquier pantalla. */
export type Destino = 'inicio' | 'productos' | 'carrito' | 'compra' | 'compras' | 'cuenta' | 'perfil'

/** Botón de un diálogo modal (DialogoView). */
export interface BotonDialogo {
  texto: string
  valor: string
  estilo?: 'primario' | 'secundario' | 'peligro'
}
