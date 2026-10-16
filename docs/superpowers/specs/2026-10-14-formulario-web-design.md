# Diseño: Formulario Web EDI/EDID — front React + back Spring Boot

- **Fecha:** 2026-10-14
- **Estado:** borrador para revisión
- **Responsable:** Oscar Dueñas (oscar.duenas@uniminuto.edu)
- **Sistema de origen:** `D:\PERSONALES\PROYECTOS\FORMULARIO_WEB\FORMULARIO_WEB` (PHP + ADOdb + Oracle), se
  conserva intacto como referencia.

## 1. Propósito y alcance

Reemplazar el aplicativo PHP de la Encuesta sobre Ambiente y Desempeño Institucional (EDI/EDID, DANE)
por dos aplicaciones separadas —front en React y back en Spring Boot— con una experiencia de usuario
moderna y accesible, y agregar un módulo de administración del cuestionario que permita, **sin tocar
código**:

1. Modificar el texto de las preguntas.
2. Agregar o eliminar (desactivar) preguntas.
3. Cambiar los saltos entre preguntas.
4. Cambiar valores mínimos y máximos.

El alcance incluye también la migración del `admin/` actual: reportes de cobertura (EDI/EDID), estado
operativo, histórico, novedades, rendimientos, exportaciones a Excel, gestión de novedades por
funcionario y generación de claves de la muestra.

### Criterios de éxito

- Un encuestado puede diligenciar los capítulos A–H, Observaciones y Terminar, con saltos y rangos
  equivalentes a los de la versión PHP 2024, verificados contra la matriz de pruebas de
  `DOCUMENTOS/MATRIZ DE PRUEBAS`.
- Un administrador puede cambiar texto, saltos y rangos y ver el efecto en la encuesta sin despliegue.
- Los reportes producen las mismas cifras que los reportes PHP actuales sobre los mismos datos.
- Cumple WCAG 2.1 AA y los Lineamientos de Desarrollo UNIMINUTO v1.2 (cobertura, seguridad, Docker,
  README, OpenAPI).

## 2. Decisiones tomadas (con el responsable)

| # | Decisión | Consecuencia |
|---|----------|--------------|
| D1 | Reportes y novedades se migran en esta fase | Fase 4 del plan |
| D2 | Se usa la **misma Oracle** (`DANE_DIRPEN`, servicio `TRANS21`) y sus tablas actuales | Sin migración de datos; la carga DMC (`TC=2`) sigue funcionando |
| D3 | Nueva pregunta → columna mapeada; si no existe, se genera `ALTER TABLE` para el DBA y la pregunta queda "pendiente de columna". Eliminar = desactivar | El usuario de aplicación no necesita privilegios DDL |
| D4 | **No se crean tablas nuevas** en Oracle | Saltos complejos y rangos van en JSON (D5) |
| D5 | Saltos y validaciones se guardan como **archivo JSON administrado por el back**, versionado con historial | Ver §5.3 |
| D6 | El back corre en **una sola instancia**; el JSON vive en un volumen local montado | Ruta configurable por `RULES_DIR` |
| D7 | Las contraseñas **siguen en texto plano** (columna `PSW VARCHAR2(8)`); sin DDL | Deuda técnica de seguridad documentada (§7.2) |
| D8 | Encuestado: **una página por capítulo** con navegación lateral, progreso y autoguardado | §6.1 |
| D9 | Identidad **DANE**, reutilizando los activos actuales (`Cabezote_EDI_EDID_2.jpeg`, logo DANE, magenta institucional) | Se excluye `logo_dane-presidencia.png` (sello de gobierno desactualizado) |
| D10 | Back con **OpenAPI 3 + Swagger UI** (springdoc) | Abierto en dev/QA; solo rol administrador en prod |
| D11 | **Monorepo** con dos aplicaciones independientes, repositorio nuevo en `D:\PERSONALES\PROYECTOS\FORMULARIO_WEB\formulario-web` | §3 |
| D12 | Administradores ingresan con COD_USUA + CLAVE (el PHP usaba solo la clave) | Evita confundir administradores con la misma clave |
| D13 | Rol ADMIN_OPERATIVO para ROL 3 y 4 heredados (grupo operativo) | Acceso a reportes en la Fase 4 |
| D14 | Códigos de encuesta heredados EDIN / EDID; ambas comparten las tablas EDIN_20_CAP_* | Las tablas de muestra son EDIN_MUESTRA / EDID_MUESTRA |
| D15 | Preguntas deshabilitadas por salto se guardan como `'0'` (igual que `Buscar_Valores` del PHP) | Compatibilidad con reportes, cargas DMC y series históricas; reemplaza "se guarda NULL" de §5.1 |
| D16 | Navegación **secuencial** y guardado **por página, una sola vez** (como el PHP): una página se habilita cuando la anterior está guardada; guardada queda en solo lectura | Reemplaza el autoguardado y la navegación libre de §6.1; no hay borradores en el servidor |
| D17 | El `C_C` es dato personal: no viaja legible en el JWT (se cifra con AES-GCM) ni se muestra; el comprobante al terminar es un **radicado derivado** no reversible (HMAC con `RECEIPT_KEY`) | Ley 1581 |
| D18 | "Terminar" funciona como el PHP: requiere todas las páginas guardadas y guarda las observaciones (opcionales, una sola vez) en `OBS` de la tabla H; no se escribe `COMPLETA` | La encuesta está terminada cuando todas las páginas están guardadas |
| D19 | Las reglas JSON se validan estructuralmente al cargarlas; el JSON Schema formal llega con el editor de reglas (Fase 3) | YAGNI: en la Fase 2 las reglas solo vienen de la semilla |

## 3. Arquitectura

```
[Navegador] ──HTTPS──> [frontend: nginx sirviendo React]
     │                         │
     └──── /api/v1 (cookie JWT + CSRF) ──> [backend: Spring Boot]
                                              ├── Oracle DANE_DIRPEN (JDBC, usuario DANE_EDI_WEB)
                                              └── Volumen rules-data (JSON de saltos/validaciones)
```

### 3.1 Estructura del repositorio

```
formulario-web/
├─ frontend/                 React 18 + TypeScript + Vite
│  └─ src/
│     ├─ components/         UI reutilizable (QuestionCard, ChapterNav, DataTable…)
│     ├─ features/
│     │   ├─ survey/         flujo del encuestado
│     │   ├─ admin-questions/
│     │   ├─ admin-rules/
│     │   ├─ admin-reports/
│     │   └─ admin-novelties/
│     ├─ services/           cliente API (tipos generados desde OpenAPI) + TanStack Query
│     ├─ rules/              motor de saltos/validaciones del lado del cliente
│     └─ styles/             tokens DANE, tema claro/oscuro
├─ backend/                  Spring Boot 4.1 (la línea 3.5 terminó su soporte OSS en junio de 2026), Java 21, Maven
│  └─ src/main/java/co/gov/dane/edi/
│     ├─ controller/         REST + anotaciones OpenAPI
│     ├─ service/            lógica de negocio
│     ├─ repository/         acceso a Oracle (patrón Repository, JDBC)
│     ├─ model/              DTO y registros de dominio
│     ├─ rules/              motor de reglas + almacén JSON
│     ├─ security/           login, JWT, roles, límite de intentos, cabeceras
│     └─ util/
├─ shared/rules-fixtures/    casos de prueba del motor de reglas (usados por back y front)
├─ db/                       scripts SQL de referencia (lectura); sin DDL nuevo
├─ docs/                     specs, planes, OpenAPI exportado, diagramas
└─ docker-compose.yml        front + back + Oracle XE (solo desarrollo)
```

### 3.2 Acceso a datos

- `NamedParameterJdbcTemplate` (no JPA). Las tablas de respuestas tienen **una columna por pregunta** y las
  preguntas cambian desde el administrador; un mapeo JPA obligaría a recompilar con cada pregunta nueva.
- Las sentencias `INSERT`/`UPDATE` sobre tablas de respuestas se construyen con nombres de columna tomados
  **solo** de una lista blanca obtenida de `ALL_TAB_COLUMNS` (cacheada, invalidada al crear preguntas) y
  validados con `^[A-Z][0-9A-Z_]{1,29}$`. Los nombres de tabla salen de un mapa fijo capítulo→tabla. Todos
  los valores van como parámetros enlazados.

### 3.3 Configuración

Variables de entorno (documentadas en `.env.example`, nunca en código):
`DB_URL`, `DB_USER`, `DB_PASSWORD`, `SURVEY_CODE` (EDI/EDID), `SURVEY_YEAR`, `RULES_DIR`, `JWT_SECRET`,
`CORS_ALLOWED_ORIGIN`, `SWAGGER_ENABLED`. Perfiles Spring `dev`, `qa`, `prod`.

## 4. Modelo de datos (Oracle existente, sin DDL)

### 4.1 Tablas usadas

| Uso | Tablas | Acceso |
|-----|--------|--------|
| Estructura del cuestionario | `FRM_CAP_PROY`, `FRM_CAP_PERIODO`, `FRM_PRE_GRUPO`, `FRM_PREGUNTA`, `FRM_ENCABEZADO` | L/E |
| Opciones de respuesta | `FRM_OBJ_PRE`, `FRM_OBJETO`, `FRM_RTA_GRUPO`, `FRM_RESPUESTA` | L/E |
| Ayudas | `FRM_PRE_AYUDA`, `FRM_ENC_AYUDA` | L/E |
| Salto simple heredado | `FRM_SALTO` | L (se convierte a JSON en la migración inicial) |
| Respuestas | `EDIN_20_CAP_B_UBICA` … `EDIN_20_CAP_H_PREV_PRACT` (y equivalentes EDID) | L/E |
| Muestra y acceso | `EDIN_MUESTRA`, `EDID_MUESTRA`, tabla de usuarios administradores | L/E |
| Catálogos | `GRAL_ENTI`, `GRAL_NOVEDAD`, `GRAL_TERRITORIAL_ACCESO`, `GRAL_DIVIPOLA` | L |

### 4.2 Columnas conocidas (deducidas del código PHP)

- `FRM_CAP_PROY`: `COD_CAP_PROY`, `COD_PRE_GRUPO`, `CAPITULO`
- `FRM_PRE_GRUPO`: `COD_PRE_GRUPO`, `COD_PRE`, `GRUPO`, `ORDEN`
- `FRM_PREGUNTA`: `COD_PRE`, `PREGUNTA`, `TIPO` (`P` = enunciado, otro = literal con respuesta)
- `FRM_CAP_PERIODO`: `COD_CAP_PROY`, `PROY`, `ANIO`, `NUMERAL`
- `FRM_SALTO`: `COD_CAP_PROY`, `SALTO`, `VALOR_SALTO`
- `FRM_OBJ_PRE`: `COD_CAP_PROY`, `COD_OBJ`, `GRUPO`; `FRM_OBJETO`: `COD_OBJ` (1 combo, 2 check, 5 texto)
- `FRM_RTA_GRUPO`: `GRUPO`, `COD_RTA`, `ORDEN`; `FRM_RESPUESTA`: `COD_RTA`, `VALOR`, `RESPUESTA`
- `FRM_ENCABEZADO`: `COD_ENC`, `COD_CAP_PROY`, `ANIO`, `TIPO` (`E` encabezado / subtítulo), `ENCABEZADO`
- `FRM_PRE_AYUDA`: `COD_AYU`, `COD_CAP_PROY`, `TEXTO`; `FRM_ENC_AYUDA`: `COD_AYU`, `COD_ENC`, `TEXTO`
- `EDIN_MUESTRA`: `C_C`, `LGN VARCHAR2(8)`, `PSW VARCHAR2(8)`, `ANIO`, `TC1`, `ATV`, `COD_ENT`, `COD_DEPTO`,
  `NOM_APE`, `AREA_TRA`, `IDFNOV`, `NOV_OTRA_CAUSA`…

**Prerrequisito de la Fase 1:** obtener el DDL real de las tablas `FRM_*` y de la tabla de usuarios
administradores (consulta a `ALL_TAB_COLUMNS` como `DANE_DIRPEN`). Ninguna implementación sobre esas tablas
comienza sin él. Si el DDL revela columnas adicionales útiles, se actualiza este spec.

### 4.3 Operaciones del administrador sobre el cuestionario

- **Editar texto:** `UPDATE FRM_PREGUNTA` / `FRM_ENCABEZADO` / ayudas. El texto se guarda como texto plano
  (sin entidades HTML); al migrar se decodifican las entidades existentes (`&oacute;` → `ó`) solo en la
  lectura, sin reescribir datos históricos salvo que el administrador edite la pregunta.
- **Agregar pregunta:** inserta en `FRM_PREGUNTA`, `FRM_PRE_GRUPO`, `FRM_CAP_PROY`, `FRM_CAP_PERIODO` y
  `FRM_OBJ_PRE` en una transacción, con la columna destino indicada. Si la columna no existe en la tabla del
  capítulo, la pregunta queda en estado **pendiente de columna** (no se muestra al encuestado) y el
  administrador descarga el script `ALTER TABLE … ADD (<col> VARCHAR2(n))` para el DBA. El estado se
  recalcula consultando `ALL_TAB_COLUMNS`.
- **Eliminar pregunta:** se desactiva retirando su registro de `FRM_CAP_PERIODO` para el año activo. Las
  columnas y respuestas históricas no se tocan. Si alguna regla JSON referencia la pregunta, el back rechaza
  la desactivación con la lista de reglas afectadas.
- **Reordenar:** `UPDATE FRM_PRE_GRUPO.ORDEN`.

## 5. Motor de reglas (saltos y validaciones)

### 5.1 Formato

Un archivo por encuesta y año: `${RULES_DIR}/<ENCUESTA>-<ANIO>.json`, validado con JSON Schema
(`backend/src/main/resources/rules.schema.json`).

```json
{
  "encuesta": "EDI",
  "anio": 2024,
  "version": 7,
  "saltos": [
    {
      "id": "c07a-no",
      "descripcion": "Si no participó en C07A, no aplica C08–C10",
      "si": { "pregunta": "C07A", "op": "eq", "valor": "2" },
      "entonces": { "accion": "deshabilitar", "preguntas": ["C08", "C09A", "C09B", "C10"] }
    },
    {
      "id": "c09-todas-no",
      "si": { "todas": ["C09A", "C09B", "C09C"], "op": "eq", "valor": "2" },
      "entonces": { "accion": "deshabilitar", "preguntas": ["C10"] }
    }
  ],
  "validaciones": {
    "B06": { "obligatoria": true, "tipo": "entero", "min": 15, "max": 99 }
  }
}
```

- Condiciones: `{pregunta, op, valor}` con `op ∈ {eq, ne, in, notIn, gt, lt, empty, notEmpty}`;
  cuantificadores `todas` / `alguna` sobre una lista de preguntas; combinadores `y` / `o` anidables.
- Acción: `deshabilitar` (se limpia el valor y se guarda `NULL`, como el JS actual).
- Validaciones por pregunta: `obligatoria`, `tipo` (`entero`, `decimal`, `texto`, `opcion`), `min`, `max`,
  `longitudMax`. Una pregunta deshabilitada por salto nunca es obligatoria.
- Las preguntas se referencian por **código de columna**, nunca por posición.

### 5.2 Evaluación

- **Front:** evalúa en vivo para colapsar preguntas y mostrar errores inline.
- **Back:** reevalúa al guardar y al finalizar; es la validación autoritativa. Respuestas a preguntas
  deshabilitadas se descartan (se guarda `NULL`); valores fuera de rango → 422 con errores por pregunta.
- Ambos motores se prueban contra los mismos casos en `shared/rules-fixtures/*.json`
  (`respuestas` → `deshabilitadas` + `errores` esperados).

### 5.3 Almacenamiento y edición

- Lectura: cacheado en memoria; se recarga al guardar.
- Guardado: validación de esquema → verificación de que toda pregunta referenciada exista y esté activa →
  detección de ciclos (una pregunta no puede deshabilitar, directa o indirectamente, a la que la controla)
  → control de concurrencia por `version` (`If-Match`; desfasado → 409) → copia previa en
  `${RULES_DIR}/historial/<ENCUESTA>-<ANIO>.<timestamp>.json` → escritura atómica (archivo temporal +
  `ATOMIC_MOVE`) → registro de auditoría (usuario, fecha, resumen del diff).
- Restaurar versión, exportar e importar desde el administrador (para promover de QA a producción).
- Semilla: el repo trae `backend/src/main/resources/rules-seed/EDI-2024.json` y `EDID-2024.json` con los
  saltos hoy escritos a mano en `General/JavaScript/FrmValida.js` y los de `FRM_SALTO`. Si `RULES_DIR` no
  tiene archivo para la encuesta activa, se copia la semilla al arrancar.

## 6. Front

Stack: React 18, TypeScript, Vite, React Router, TanStack Query, React Hook Form, Tailwind CSS con tokens,
Radix UI (primitivas accesibles), `openapi-typescript`. Cada dependencia se verifica en npm y se fija la
versión exacta.

### 6.1 Encuestado

- **Login:** usuario y clave; error genérico ("Usuario o clave incorrectos") sin revelar existencia.
- **Bienvenida:** instrucciones del capítulo A resumidas; "Comenzar" o "Continuar donde quedé".
- **Capítulo:** navegación lateral A–H con estado (completo / en curso / pendiente), cajón inferior en
  móvil; barra de progreso global.
- **Pregunta:** tarjeta con numeral y texto; opciones como radios grandes (área táctil ≥ 44 px); combo con
  búsqueda si hay más de 7 opciones; numéricas con min/max y mensaje inline.
- **Ayudas:** ícono (i) con popover accesible en lugar de la ventana emergente.
- **Saltos:** la pregunta deshabilitada se colapsa con "No aplica por su respuesta en C07A", anunciado con
  `aria-live="polite"`.
- **Guardado (D16):** sin autoguardado. "Guardar y continuar" valida la página, pide confirmación
  ("después no podrá modificar esta página") y guarda; aviso al salir con cambios sin guardar.
- **Terminar (D18):** con todas las páginas guardadas, campo de observaciones opcional (máx. 1100 caracteres)
  y pantalla final con el radicado derivado (D17).

### 6.2 Administración

- Layout con menú lateral: Cuestionario, Reglas, Reportes, Novedades, Claves (visibles según rol).
- **Cuestionario:** árbol capítulo → grupo → pregunta; edición inline con vista previa; reordenar por
  arrastre y por teclado; indicador de estado de columna (activa / pendiente de DBA) con descarga del
  script.
- **Reglas:** constructor visual "Si [pregunta] [operador] [valor] → deshabilitar [preguntas]"; pestaña
  JSON de solo lectura; **Simular** (responder una encuesta de prueba y ver el efecto); publicar con
  confirmación y resumen del diff; historial con restaurar.
- **Rangos:** tabla editable por pregunta (mín., máx., obligatoria, tipo) con validación mín ≤ máx.
- **Reportes:** tablas con filtros por encuesta y departamento, orden, paginación y "Exportar a Excel".

### 6.3 Transversal

WCAG 2.1 AA (contraste ≥ 4,5:1, foco visible, teclado completo, `lang="es"`, etiquetas asociadas),
mobile-first, modo oscuro, skeletons de carga, estados vacíos y de error con reintento. Encabezado con
`Cabezote_EDI_EDID_2.jpeg`; color primario magenta DANE ajustado para contraste AA.

## 7. Back

### 7.1 API (`/api/v1`, OpenAPI 3, errores RFC 7807)

**Autenticación:** `GET /auth/csrf`, `POST /auth/login`, `POST /auth/logout`, `GET /auth/me`.

**Encuestado** (rol `ENCUESTADO`; `C_C` siempre desde el token):
`GET /survey`, `GET /survey/chapters/{cap}`, `GET /survey/rules`, `PUT /survey/chapters/{cap}/answers`,
`POST /survey/observations`, `POST /survey/finish`.

**Cuestionario** (rol `ADMIN_CUESTIONARIO`):
`GET|POST /admin/questionnaire/{encuesta}/{anio}/questions`,
`PUT|DELETE /admin/questionnaire/{encuesta}/{anio}/questions/{codigo}`,
`GET …/questions/{codigo}/column-script`, `PATCH …/questions/order`.

**Reglas** (rol `ADMIN_CUESTIONARIO`):
`GET|PUT /admin/rules/{encuesta}/{anio}`, `GET …/history`, `POST …/history/{version}/restore`,
`GET …/export`, `POST …/import`, `POST …/simulate`.

**Reportes y novedades** (roles `ADMIN_NACIONAL`, `ADMIN_TERRITORIAL` filtrado por `COD_DPTO`):
`GET /admin/reports/{coverage|operational-status|history|novelties|performance}` con `format=json|xlsx`;
`GET|PUT /admin/novelties/entities/{codEnt}/users[/{cc}]`; `POST /admin/sample/passwords`.

**Operación:** `GET /actuator/health`; Swagger UI en `/swagger-ui.html` según `SWAGGER_ENABLED`.

### 7.2 Seguridad

- Credenciales por variables de entorno. Las credenciales presentes en `Conexion/Conexion_oracle.php` del
  sistema PHP **no se copian** al repositorio nuevo; se recomienda rotarlas (estuvieron en el código y en
  SVN).
- **Deuda técnica (D7):** contraseñas en texto plano en `PSW VARCHAR2(8)`. Mitigaciones: comparación en
  tiempo constante, límite de 5 intentos y bloqueo de 15 min por usuario+IP, nunca en logs ni respuestas.
  Plan de salida: ampliar `PSW` a `VARCHAR2(60)` y migrar a BCrypt cuando se autorice DDL.
- Sesión: JWT de 30 min con renovación deslizante, en cookie `HttpOnly; Secure; SameSite=Strict`; CSRF por
  double-submit cookie; CORS limitado a `CORS_ALLOWED_ORIGIN`.
- Autorización por rol en cada endpoint; territorial filtrado en el servidor; encuesta finalizada en solo
  lectura.
- Cabeceras: CSP estricta, HSTS, `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`,
  `Referrer-Policy: same-origin`.
- Bean Validation en todos los DTO; textos administrables escapados al mostrarse (nunca HTML).
- Logs JSON con correlation-id, sin contraseñas, tokens, `C_C` ni nombres. Auditoría de cambios de
  cuestionario y reglas.
- Generación de claves: se muestran/exportan una sola vez al generarlas; la exportación no se guarda en el
  servidor.

### 7.3 Manejo de errores

| Situación | Respuesta | Front |
|-----------|-----------|-------|
| Validación de respuestas | 422 con `errores[{pregunta, mensaje}]` | Marca cada pregunta |
| Conflicto de versión de reglas | 409 | Diálogo "otro usuario modificó las reglas, recargar" |
| Sesión expirada | 401 | Aviso, conserva lo escrito, vuelve al login |
| Sin permiso | 403 | Página de acceso denegado |
| Oracle no disponible | 503 | Banner con reintento; autoguardado en cola |

## 8. Pruebas

| Nivel | Back | Front |
|-------|------|-------|
| Unitarias | JUnit 5 + Mockito; motor de reglas con `shared/rules-fixtures` | Vitest + Testing Library; mismo motor con los mismos fixtures |
| Integración | Testcontainers con Oracle XE (DDL de `DOCUMENTOS/` + datos ficticios) | MSW |
| E2E | — | Playwright: login → A–H con saltos → finalizar; editar salto → verlo aplicado |
| Accesibilidad | — | axe-core en E2E por vista |

Cobertura mínima: **80 %** en motor de reglas, autenticación y autorización; **60 %** en el resto (JaCoCo /
Vitest coverage); el pipeline bloquea el merge si no se cumple. Los casos de la matriz de pruebas de
`DOCUMENTOS/MATRIZ DE PRUEBAS` se convierten en pruebas de regresión. Nunca se usan datos reales de
funcionarios en pruebas.

## 9. Despliegue y CI/CD

- `backend/Dockerfile`: multi-stage, build con `eclipse-temurin:21-jdk` (versión fija), runtime
  `eclipse-temurin:21-jre` (versión fija), usuario no-root, `HEALTHCHECK` contra `/actuator/health`,
  volumen `rules-data`.
- `frontend/Dockerfile`: build con `node` (versión LTS fija), runtime `nginxinc/nginx-unprivileged` (versión
  fija) con CSP y cabeceras de seguridad.
- Labels de responsable y documentación; `.dockerignore`; escaneo Trivy sin vulnerabilidades críticas.
- Pipeline: lint → build → pruebas unitarias e integración → SAST (Semgrep) / SCA (OWASP
  Dependency-Check) → imagen + Trivy → staging → aprobación manual → producción → smoke tests.
- gitleaks como hook pre-commit; Dependabot.
- Paso a producción con FR-FT-GST-02, RFC y aprobación del CAB.

## 10. Documentación

README con las once secciones institucionales; OpenAPI exportado en `docs/api/openapi.yaml`; diagramas de
arquitectura, secuencia (guardado con reglas) y MER de las tablas usadas en `docs/diagramas/`;
`CHANGELOG.md` con SemVer. Excepciones de nombres por convención del lenguaje (paquetes Java en minúscula,
componentes React en PascalCase con archivos `PascalCase.tsx`) documentadas en el README.

## 11. Git

Repositorio nuevo con ramas `main`, `QA`, `develop`; trabajo en `feature/*` con PR hacia `develop`;
Conventional Commits; `Co-Authored-By` en commits asistidos. Ninguna fusión, subida o despliegue sin
autorización explícita del responsable.

## 12. Fases de entrega

Cada fase tiene su propio plan de implementación.

1. **Base:** monorepo, CI, Docker, configuración, seguridad (login, JWT, roles, límite de intentos),
   lectura del cuestionario desde Oracle, OpenAPI.
2. **Encuestado:** flujo A–H, Observaciones, Terminar; autoguardado; motor de reglas en back y front;
   semilla de reglas 2024 migrada desde `FrmValida.js` y `FRM_SALTO`.
3. **Administración del cuestionario:** textos, agregar/desactivar/reordenar preguntas, scripts para DBA,
   editor de saltos y rangos, simulador, historial, exportar/importar.
4. **Reportes, novedades y claves:** reportes con exportación Excel, gestión de novedades, generación de
   claves.

## 13. Riesgos y prerrequisitos

| Riesgo / prerrequisito | Mitigación |
|------------------------|------------|
| DDL de `FRM_*` desconocido | Obtenerlo antes de la Fase 1 (§4.2) |
| Saltos 2024 dispersos en JS con índices fijos | Migración caso por caso validada con la matriz de pruebas |
| Contraseñas en texto plano (D7) | Mitigaciones de §7.2; plan de salida documentado |
| Credenciales expuestas en el código PHP | No copiarlas; recomendar rotación |
| JSON de reglas fuera de Oracle | Historial, exportar/importar, volumen respaldado |
| Oracle XE para pruebas difiere de producción | Pruebas de humo en QA contra la base real de pruebas |

## Desviaciones de implementación (Fase 1)

- Spring Boot 4.1.1 en lugar de 3.x; Tomcat fijado en 11.0.26 (`tomcat.version`) por 3 CVE críticos en
  11.0.24, a retirar cuando Boot incluya 11.0.26 o superior.
- Se agregó `GET /api/v1/auth/csrf`; el valor que debe enviarse en `X-XSRF-TOKEN` es el de la cookie
  `XSRF-TOKEN`, no el campo `token` del JSON (enmascarado).
- El backend no usa `.oauth2ResourceServer()`: registra `BearerTokenAuthenticationFilter` a mano porque
  el configurador exime de CSRF a las peticiones con token en cookie. `ApiExceptionHandler` relanza
  `AccessDeniedException` y `AuthenticationException` para conservar los 403/401 de Spring Security.
- IP del cliente: nginx es el borde (`X-Forwarded-For $remote_addr`) y el backend usa
  `server.forward-headers-strategy: native`; con un balanceador delante hay que configurar
  `server.tomcat.remoteip.*`. nginx sobrescribe `X-Correlation-Id` con `$request_id`.
- Imagen del frontend `nginxinc/nginx-unprivileged:1.30.3-alpine3.23` (1.28.2 tenía 3 CVE críticos sin
  parche); cabeceras de seguridad en `frontend/nginx/security-headers.conf`.
- Proxy de Vite con `changeOrigin: false` (con `true` el backend aplica CORS).
- `@testing-library/jest-dom` 6.9.1 (la 6.10.0 figura como release incorrecta). ESLint se mantiene en
  9.39.5 porque `eslint-plugin-jsx-a11y` 6.10.2 no soporta ESLint 10 (deuda técnica).
- `overrides` de `playwright-core` 1.63.0 en el frontend para alinear `@axe-core/playwright`.
- El bloqueo por intentos es en memoria (por instancia); la mitigación real contra inundación es rate
  limiting en el proxy o balanceador.
