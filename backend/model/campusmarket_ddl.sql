-- =====================================================================
-- CampusMarket (UCAB) - Script DDL PostgreSQL - Sprint 1
-- ---------------------------------------------------------------------
-- Capa: MODEL (persistencia). Ejecutar en DBeaver sobre una base vacía:
--     CREATE DATABASE campusmarket;   -- (una sola vez, conectado a "postgres")
--     luego abrir este archivo conectado a "campusmarket" y ejecutar (Alt+X).
--
-- Decisiones de diseño:
--   * Las reglas de negocio críticas se repiten como CONSTRAINTS en la BD
--     (defensa en profundidad): aunque el backend valide, la base de datos
--     nunca acepta datos que violen las reglas del dominio.
--   * Todos los montos son NUMERIC(12,2) y se expresan SOLO en Bolívares (Bs.).
--   * Spring Boot está configurado con ddl-auto=none: este script es la
--     única fuente de verdad del esquema.
-- =====================================================================

-- Extensión para búsquedas rápidas por texto (RNF: búsqueda < 2 s).
-- pg_trgm permite índices GIN que aceleran "ILIKE '%texto%'".
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Limpieza (orden inverso a las dependencias de claves foráneas).
DROP TABLE IF EXISTS resena          CASCADE;
DROP TABLE IF EXISTS compra_detalle  CASCADE;
DROP TABLE IF EXISTS compra          CASCADE;
DROP TABLE IF EXISTS carrito_item    CASCADE;
DROP TABLE IF EXISTS carrito         CASCADE;
DROP TABLE IF EXISTS producto        CASCADE;
DROP TABLE IF EXISTS usuario         CASCADE;

-- ---------------------------------------------------------------------
-- USUARIO  (HU-07 Registro de Cuenta, HU-02 Consultar Perfil)
-- ---------------------------------------------------------------------
CREATE TABLE usuario (
    id              BIGSERIAL     PRIMARY KEY,                 -- PK sustituta autoincremental
    nombre          VARCHAR(100)  NOT NULL,
    correo          VARCHAR(120)  NOT NULL,
    password_hash   VARCHAR(100)  NOT NULL,                    -- Hash BCrypt, NUNCA la clave en texto plano
    telefono        VARCHAR(20),
    rol             VARCHAR(20)   NOT NULL DEFAULT 'ESTUDIANTE',
    fecha_registro  TIMESTAMP     NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_usuario_correo  UNIQUE (correo),             -- Un correo = una cuenta
    -- Ámbito UCAB: solo correos institucionales estudiantiles (en minúsculas).
    CONSTRAINT ck_usuario_correo_ucab CHECK (correo ~ '^[a-z0-9._%+-]+@est\.ucab\.edu\.ve$'),
    CONSTRAINT ck_usuario_rol     CHECK (rol IN ('ESTUDIANTE', 'ADMINISTRADOR')),
    -- Teléfono venezolano normalizado "0414-1234567": móviles 0412/0414/0416/0424/0426 y fijo 0212 (Gran Caracas).
    CONSTRAINT ck_usuario_telefono CHECK (telefono IS NULL OR telefono ~ '^0(412|414|416|424|426|212)-[0-9]{7}$')
);
COMMENT ON TABLE usuario IS 'Cuentas de la comunidad UCAB (estudiantes y administradores).';

-- ---------------------------------------------------------------------
-- PRODUCTO  (HU-03 Agregar Producto, HU-06 Consultar Producto)
-- ---------------------------------------------------------------------
CREATE TABLE producto (
    id              BIGSERIAL     PRIMARY KEY,
    codigo          VARCHAR(30)   NOT NULL,                    -- Código de negocio (SKU) visible al admin
    nombre          VARCHAR(120)  NOT NULL,
    descripcion     VARCHAR(500),
    categoria       VARCHAR(50)   NOT NULL,
    precio          NUMERIC(12,2) NOT NULL,                    -- Precio unitario en Bs.
    stock           INTEGER       NOT NULL,
    imagen_url      VARCHAR(500)  NOT NULL,                    -- Imagen obligatoria
    fecha_creacion  TIMESTAMP     NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_producto_codigo UNIQUE (codigo),             -- Código único, no duplicado
    CONSTRAINT ck_producto_precio CHECK (precio > 0),          -- precio > 0
    -- stock >= 0 en BD porque las compras lo descuentan hasta 0;
    -- la regla "stock > 0 al crear" la aplica el backend (HU-03).
    CONSTRAINT ck_producto_stock  CHECK (stock >= 0)
);
COMMENT ON TABLE producto IS 'Catálogo de insumos estudiantiles. Precios en Bolívares.';

CREATE INDEX ix_producto_categoria   ON producto (categoria);
CREATE INDEX ix_producto_nombre_trgm ON producto USING GIN (nombre gin_trgm_ops);

-- ---------------------------------------------------------------------
-- CARRITO  (HU-04 Agregar a Carrito, HU-08 Consultar Carrito)
-- Un carrito persistente por estudiante (relación 1:1 con usuario).
-- ---------------------------------------------------------------------
CREATE TABLE carrito (
    id                  BIGSERIAL  PRIMARY KEY,
    usuario_id          BIGINT     NOT NULL,
    fecha_actualizacion TIMESTAMP  NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_carrito_usuario UNIQUE (usuario_id),         -- 1 carrito por usuario
    CONSTRAINT fk_carrito_usuario FOREIGN KEY (usuario_id)
        REFERENCES usuario (id) ON DELETE CASCADE              -- Si se borra el usuario, su carrito también
);

CREATE TABLE carrito_item (
    id          BIGSERIAL  PRIMARY KEY,
    carrito_id  BIGINT     NOT NULL,
    producto_id BIGINT     NOT NULL,
    cantidad    INTEGER    NOT NULL,

    CONSTRAINT ck_carrito_item_cantidad CHECK (cantidad > 0),
    -- Un producto aparece una sola vez por carrito: si se agrega de nuevo, se suma la cantidad.
    CONSTRAINT uq_carrito_item UNIQUE (carrito_id, producto_id),
    CONSTRAINT fk_item_carrito  FOREIGN KEY (carrito_id)  REFERENCES carrito (id)  ON DELETE CASCADE,
    CONSTRAINT fk_item_producto FOREIGN KEY (producto_id) REFERENCES producto (id) ON DELETE RESTRICT
);

-- ---------------------------------------------------------------------
-- COMPRA / ORDEN  (HU-05 Generar Compra, HU-09 Consultar Compras)
-- ---------------------------------------------------------------------
CREATE TABLE compra (
    id            BIGSERIAL     PRIMARY KEY,
    numero_orden  VARCHAR(30),                                 -- Se asigna tras el INSERT (CM-AAAAMMDD-id)
    usuario_id    BIGINT        NOT NULL,
    fecha         TIMESTAMP     NOT NULL DEFAULT NOW(),
    estado        VARCHAR(20)   NOT NULL DEFAULT 'PENDIENTE_PAGO',
    total         NUMERIC(12,2) NOT NULL,                      -- Total en Bs.

    CONSTRAINT uq_compra_numero  UNIQUE (numero_orden),
    CONSTRAINT ck_compra_estado  CHECK (estado IN ('PENDIENTE_PAGO', 'PAGADA', 'CANCELADA')),
    CONSTRAINT ck_compra_total   CHECK (total > 0),
    CONSTRAINT fk_compra_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE RESTRICT
);
CREATE INDEX ix_compra_usuario ON compra (usuario_id, fecha DESC);   -- Historial ordenado por fecha

CREATE TABLE compra_detalle (
    id               BIGSERIAL     PRIMARY KEY,
    compra_id        BIGINT        NOT NULL,
    producto_id      BIGINT        NOT NULL,
    cantidad         INTEGER       NOT NULL,
    -- "Foto" del precio al momento de comprar: si luego cambia el catálogo,
    -- el historial conserva lo que realmente se cobró.
    precio_unitario  NUMERIC(12,2) NOT NULL,
    subtotal         NUMERIC(12,2) NOT NULL,

    CONSTRAINT ck_detalle_cantidad CHECK (cantidad > 0),
    CONSTRAINT ck_detalle_precio   CHECK (precio_unitario > 0),
    CONSTRAINT fk_detalle_compra   FOREIGN KEY (compra_id)   REFERENCES compra (id)   ON DELETE CASCADE,
    CONSTRAINT fk_detalle_producto FOREIGN KEY (producto_id) REFERENCES producto (id) ON DELETE RESTRICT
);

-- ---------------------------------------------------------------------
-- RESEÑA  (HU-01 Consultar Reseña, HU-10 Insertar Reseña)
-- ---------------------------------------------------------------------
CREATE TABLE resena (
    id           BIGSERIAL    PRIMARY KEY,
    producto_id  BIGINT       NOT NULL,
    usuario_id   BIGINT       NOT NULL,
    texto        VARCHAR(500) NOT NULL,                        -- Límite de caracteres: 500
    calificacion SMALLINT     NOT NULL,
    fecha        TIMESTAMP    NOT NULL DEFAULT NOW(),

    -- Regla de negocio: máximo 1 reseña por estudiante por producto.
    CONSTRAINT uq_resena_usuario_producto UNIQUE (producto_id, usuario_id),
    CONSTRAINT ck_resena_texto        CHECK (char_length(btrim(texto)) BETWEEN 1 AND 500),
    CONSTRAINT ck_resena_calificacion CHECK (calificacion BETWEEN 1 AND 5),
    CONSTRAINT fk_resena_producto FOREIGN KEY (producto_id) REFERENCES producto (id) ON DELETE CASCADE,
    CONSTRAINT fk_resena_usuario  FOREIGN KEY (usuario_id)  REFERENCES usuario (id)  ON DELETE CASCADE
);
CREATE INDEX ix_resena_producto ON resena (producto_id, fecha DESC);

-- =====================================================================
-- DATOS SEMILLA (solo para pruebas locales)
-- Contraseña de ambos usuarios de prueba: "Campus2026"  (hash BCrypt)
-- =====================================================================
INSERT INTO usuario (nombre, correo, password_hash, telefono, rol) VALUES
 ('Administrador CampusMarket', 'admin.campus@est.ucab.edu.ve',
  '$2a$10$5cMgtHCblmbjKZrY3DCR9O2ElQcUPTCuRYv5akJymzoTlqDl6HQeS', '0212-4074000', 'ADMINISTRADOR'),
 ('Estudiante Demo', 'estudiante.demo@est.ucab.edu.ve',
  '$2a$10$5cMgtHCblmbjKZrY3DCR9O2ElQcUPTCuRYv5akJymzoTlqDl6HQeS', '0414-1234567', 'ESTUDIANTE');

INSERT INTO producto (codigo, nombre, descripcion, categoria, precio, stock, imagen_url) VALUES
 ('PAP-001', 'Cuaderno Universitario 100 hojas', 'Cuaderno de espiral, hojas rayadas.', 'Papelería',   150.00, 40,
  'https://images.unsplash.com/photo-1531346878377-a5be20888e57?auto=format&fit=crop&q=80&w=300&h=300'),
 ('TEC-001', 'Calculadora Científica Casio',      'Ideal para Cálculo y Física.',         'Tecnología',  850.00, 10,
  'https://images.unsplash.com/photo-1594980596870-8aa52a78d8cd?auto=format&fit=crop&q=80&w=300&h=300'),
 ('PAP-002', 'Bolígrafo Azul (Pack x3)',          'Tinta de secado rápido.',              'Papelería',    45.00, 100,
  'https://images.unsplash.com/photo-1585336261022-680e295ce3fe?auto=format&fit=crop&q=80&w=300&h=300'),
 ('ACC-001', 'Mochila Antirrobo para Laptop',     'Compartimiento acolchado de 15".',     'Accesorios', 1200.00, 5,
  'https://images.unsplash.com/photo-1553062407-98eeb64c6a62?auto=format&fit=crop&q=80&w=300&h=300');
