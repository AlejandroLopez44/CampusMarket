# CampusMarket (UCAB) — Sprint 1

Arquitectura por capas **View / Control / Model**. Solo hay dos carpetas raíz: `frontend` y `backend`. El código ejecutable vive únicamente en sus subcarpetas `view`, `control` y `model`. En las raíces solo quedan archivos de configuración de build: `pom.xml`, `package.json`, `vite.config.ts`, `index.html` y `application.properties`.

```
campusmarket/
├── frontend/   (Vue 3 + Vite + Tailwind CSS v4 + TypeScript)
│   ├── view/     App.vue (Dashboard), ProductoView, UsuarioView, CarritoView,
│   │             CompraView, ResenaView, DialogoView (modal reutilizable), estilos.css
│   └── control/  ApiClient (clase base), *ClientController.ts, NavegacionController.ts, main.ts
└── backend/    (Spring Boot 3.3 · Java 17 · PostgreSQL)
    ├── control/  CampusMarketApplication (main + errores + sesión), *Controller.java
    │             (REST + Service en cada archivo), EmailNotificationController
    └── model/    *Model.java (Entidad + DTOs + Repositorio por módulo), campusmarket_ddl.sql
```

## Cómo ejecutarlo

1. **Base de datos (DBeaver):** crea la BD `campusmarket` y ejecuta `backend/model/campusmarket_ddl.sql`.
   Usuarios de prueba (clave `Campus2026`): `admin.campus@est.ucab.edu.ve` (ADMINISTRADOR) y `estudiante.demo@est.ucab.edu.ve`.
2. **Backend:** requiere Java 17 o superior. **No hace falta instalar Maven:** el proyecto trae el Maven Wrapper (`mvnw.cmd`), que lo descarga solo la primera vez.
   - Crea la variable de entorno de Windows `DB_PASSWORD` con tu clave de PostgreSQL (no la escribas en `application.properties`, porque ese archivo se sube a GitHub). Abre una PowerShell nueva después de crearla.
   - Dentro de `backend/`, ejecuta `.\mvnw.cmd package -DskipTests` y después `java -jar target\campusmarket-backend-1.0.0-SPRINT1.jar`. Queda en http://localhost:8080.
   - No usamos `spring-boot:run`: en Windows falla con "no se ha encontrado o cargado la clase principal" cuando la ruta del proyecto tiene tildes (por ejemplo "Ingeniería").
   - Cada vez que cambies código Java, repite `.\mvnw.cmd package -DskipTests` antes de arrancar.
3. **Frontend:** ejecuta, dentro de `frontend/`, `npm install` y luego `npm run dev`. Queda en http://localhost:5173. El proxy de Vite reenvía `/api` al backend.

## Endpoints REST (10 HU)

| HU | Método y ruta | Notas |
|----|---------------|-------|
| 01 Consultar Reseña | `GET /api/productos/{id}/resenas` | Lista vacía + "No hay reseñas disponibles para este producto" |
| 02 Consultar Perfil | `GET /api/usuarios/perfil` | 503 + "Error al cargar la información del perfil. Intente nuevamente" |
| 03 Agregar Producto | `POST /api/productos/validacion` → `POST /api/productos` | Solo ADMIN; valida sin guardar, confirma y persiste |
| 04 Agregar a Carrito | `GET /api/carrito/disponibilidad` → `POST /api/carrito/items` | Verifica stock, confirma, re-verifica y guarda. Funciona sin sesión (carrito en el navegador) |
| 05 Generar Compra | `GET /api/compras/resumen` → `POST /api/compras` | Exige sesión de estudiante. Bloqueo `FOR UPDATE`, orden "Pendiente de Pago" |
| 05 Factura y pago simulado | `GET /api/compras/{id}/factura`, `POST /api/compras/{id}/pago-simulado` | Solo órdenes propias. El pago no pide datos: cambia el estado a "Pagada" (el pago real es de la 2.ª iteración) |
| 06 Consultar Producto | `GET /api/productos?q=&categoria=`, `GET /api/productos/{id}` | Índice GIN pg_trgm |
| 07 Registro de Cuenta | `POST /api/usuarios/registro` | Solo `@est.ucab.edu.ve`, BCrypt. Teléfono opcional: 0412, 0414, 0416, 0424, 0426 o 0212 + 7 dígitos |
| 08 Consultar Carrito | `GET /api/carrito` (estudiante), `POST /api/carrito/cotizacion` (visitante) | Total en Bs. con precios del servidor |
| 08 Unir carritos | `POST /api/carrito/fusion` | Al iniciar sesión, el carrito del navegador se une al de la cuenta respetando el stock |
| 09 Consultar Compras | `GET /api/compras` | Solo las órdenes de la sesión activa |
| 09 Panel del admin | `GET /api/admin/estadisticas` | Solo ADMIN: ventas, pendientes, inventario, más vendidos, ventas de 7 días, órdenes recientes y reseñas |
| 10 Insertar Reseña | `POST /api/productos/{id}/resenas` | Solo estudiantes. Requiere compra previa; 1 reseña por producto; máx. 500 caracteres |
| (soporte) | `POST /api/usuarios/login` y `/logout` | Sesión HTTP (cookie JSESSIONID) |

**Roles:**
- **Visitante:** ve el catálogo y las reseñas, y llena su carrito.
- **Estudiante:** además compra, ve sus facturas y escribe reseñas.
- **Administrador:** ve su panel de estadísticas, agrega productos y lee reseñas. No tiene carrito ni compras: el backend le responde 403.

El correo **no tiene endpoint**. Lo envía el backend al recibir los eventos `UsuarioRegistradoEvent`, `CompraRegistradaEvent` y `CompraPagadaEvent`, después del commit y de forma asíncrona. Si no hay SMTP configurado, el mensaje solo se escribe en el log.

## Adaptaciones respecto al frontend previo (`../src`)

- **De datos simulados a API REST:** `src/data/*.js` (estado en memoria con `reactive`) se reemplazó por los `*ClientController.ts`. Ahora la fuente de verdad es PostgreSQL.
- **Correspondencia de componentes:**
  - `CatalogoProductos` + `AdminProductos` (solo la parte de agregar) → `ProductoView`
  - `RegistroForm` + `LoginForm` + `MiPerfil` (solo lectura) → `UsuarioView`
  - `CarritoCompras` → `CarritoView`
  - `CheckoutPago` + historial de `MiPerfil` → `CompraView`
  - `ModalResenas` → `ResenaView`
- **Estilos:** el CSS propio (`style.css` y `<style scoped>`) pasó a utilidades de Tailwind. Se conservó el color índigo de la marca como `marca-600`.
- **Correcciones de reglas:**
  - El registro valida `@est.ucab.edu.ve`; antes aceptaba cualquier correo y contraseñas de 6 caracteres.
  - Las reseñas usan el usuario de la sesión; antes el autor se escribía a mano. Además se exige compra previa y se limita el texto.
  - "Agregar" verifica stock y pide confirmación; antes agregaba directo y sin stock.
  - Productos con `codigo` único y `stock`; antes no tenían ninguno de los dos campos.
- **Se retiró lo que queda fuera del Sprint 1:**
  - Modificar o eliminar productos, ítems del carrito y reseñas.
  - Editar o eliminar el perfil.
  - Formulario de tarjeta y comprobante descargable. El pago se simula en un sprint posterior: la orden queda "Pendiente de Pago".
  - `HelloWorld.vue`.
- **Navegación:** se agregó un Dashboard (menú principal) con retorno limpio desde Perfil y Compras, y un menú hamburguesa para móvil.
