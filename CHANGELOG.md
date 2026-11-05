# Changelog

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/); versiones con SemVer.

## [Sin publicar]

### Agregado
- Backend Spring Boot 4.1.1 con inicio y cierre de sesión (encuestados y administradores), sesión JWT
  en cookie HttpOnly, CSRF, bloqueo tras 5 intentos fallidos y cabeceras de seguridad.
- Lectura del cuestionario (capítulos B–H) desde las tablas FRM_* de Oracle, con lista blanca de
  columnas de respuesta y textos saneados.
- OpenAPI con Swagger UI (restringido en producción) y contrato exportado en docs/api/openapi.yaml.
- Frontend React con identidad DANE accesible (WCAG 2.1 AA), ingreso, control de acceso por rol y
  lista de capítulos.
- Imágenes Docker sin root, entorno local con docker compose y pipeline de CI con SAST, SCA, Trivy
  y pruebas E2E.
- Flujo completo del encuestado: progreso por página, páginas con saltos en vivo, guardado único
  confirmado, observaciones y radicado.
- Motor de reglas compartido (backend y frontend) y reglas reales de EDIN 2026.
- Prueba E2E del flujo del encuestado con verificación de accesibilidad; el usuario de cada
  proyecto de Playwright se configura con `E2E_FLOW_USER_<PROYECTO>` y
  `E2E_FLOW_PASSWORD_<PROYECTO>`.
- Aviso de sesión vencida (401) al guardar una página o enviar las observaciones, con enlace para
  ingresar de nuevo y borrador local de la página; aviso de recarga ante un 403 de CSRF.
- Prueba de carga reproducible del flujo del encuestado (`loadtest/`, k6 2.2.0 en Docker) con 200
  usuarios ficticios `CARGA001..200` (`db/dev/loadtest-users.sql`) y script de reinicio.
- "Cerrar sesión" pide confirmación si la página tiene respuestas sin guardar.
- Salir de una página con respuestas sin guardar (botón Atrás del navegador o navegación interna)
  pide confirmar en un diálogo accesible ("¿Salir sin guardar?"); el frontend usa un enrutador de
  datos (`createBrowserRouter`) para poder bloquear la navegación.
- Administración del cuestionario (Fase 3a) para el rol `ADMIN_CUESTIONARIO`: años (crear un año en
  borrador a partir de otro), árbol del año, edición de textos, encabezados y ayudas, orden con
  teclado o arrastre, preguntas nuevas con columna pendiente (script descargable, sin DDL desde la
  aplicación), desactivación por año y tablas de respuestas por capítulo. Las ediciones usan copia
  al escribir, control de versión (409 ante edición simultánea) y una línea de auditoría por cambio.
- Prueba E2E del administrador (`frontend/e2e/admin-cuestionario.spec.ts`) con verificación de
  accesibilidad; el administrador se configura con `E2E_ADMIN_USER` y `E2E_ADMIN_PASSWORD`.
- Diagramas de secuencia de crear un año y de editar con copia al escribir.
- Reglas, simulador y casos de prueba (Fase 3b) para `ADMIN_CUESTIONARIO`: editor de saltos con
  constructor accesible (una condición o un nivel de Y/O), edición de validaciones, vista, descarga e
  importación del JSON de reglas (hasta 1 MB, 1000 reglas y 1000 casos; más responde 422
  `ESTRUCTURA`) y análisis de lógica en cada guardado (ciclos, preguntas inexistentes, valores que
  no son opciones, rangos, reglas que nunca se cumplen o solapadas), acotado en tiempo y memoria:
  lo que no alcanza a analizarse sale en un solo aviso del archivo.
  Solo bloquean (422) los errores que el archivo vigente no tenía; las advertencias nunca; un JSON
  con `null` donde va una condición, un valor o una pregunta responde 422 `ESTRUCTURA`. Desactivar
  una pregunta nombra las reglas y los casos que lo impiden, con enlace al simulador. Simulador con
  los mismos campos de la encuesta que muestra qué regla oculta cada pregunta, y casos de prueba
  guardados en el archivo de reglas que pasan, fallan o quedan por revisar. Los 12
  casos reales de 2026 viajan en la semilla; el contrato `/api/v1/admin/questionnaire/**` suma
  `rules` y `simulator`.
- Prueba E2E del editor de reglas (`frontend/e2e/admin-reglas.spec.ts`) con axe (WCAG 2.1 AA) sobre
  Reglas, el constructor, la alerta de un 422, el simulador y la lista de casos.
- Diagrama de secuencia de guardar una regla y sección «Desviaciones de implementación (3b)» en el
  diseño de la Fase 3.
- Publicar, corregir y promover (Fase 3c) para `ADMIN_CUESTIONARIO`:
  - **Publicar** un año con una lista de comprobación (análisis sin errores, todos los casos en
    «Pasa», columnas creadas y tablas válidas; los avisos no bloquean) que enlaza a cada elemento
    que falla; un año publicado ya no admite edición estructural (409).
  - **Correcciones** sobre un año publicado (textos, encabezados, ayudas, saltos, validaciones y
    casos) con un motivo de 10 a 500 caracteres que queda solo en el archivo de reglas; una
    corrección no puede dejar ningún caso en rojo.
  - **Historial** de versiones con comparación entre dos versiones y restauración en borrador
    (saltos, validaciones, casos y tablas; no las filas `FRM_*`).
  - **Paquetes de promoción** (ZIP con `manifiesto.json`, `reglas.json`, `frm.sql` y
    `columnas.sql`) de un año o de una corrección, y su importación verificada contra la base real
    (sin firma), con el impacto por página y sin escribir `FRM_*`.
  - Auditoría `publicar`, `importar-paquete`, `restaurar-version` y `corregir-<operación>`.
  - Prueba E2E `frontend/e2e/admin-publicar.spec.ts` con axe (WCAG 2.1 AA) sobre Publicar, Historial
    y Paquetes; diagramas de secuencia de publicar y de promover; procedimiento de paso a producción
    en el README y sección «Desviaciones de implementación (3c)» en el diseño de la Fase 3.

### Cambiado
- El motivo de una corrección en los DELETE de saltos, validaciones y casos viaja en el cuerpo JSON
  y no en la query string (para que no llegue a los logs de acceso); nginx registra `/api/` sin
  query string.
- La página Paquetes muestra una nota neutra (no un error) para un año que el ambiente aún no
  tiene, y deja importar su paquete; la descarga usa el nombre del servidor.
- nginx tiene una `location` propia para `.../package/import` con tope de 6 MB (el resto de `/api/`
  conserva 1 MB); `BACKEND_URL` debe ser `esquema://host:puerto` sin ruta.
- `GET /api/v1/survey/**` deja de responder 503 en cuanto el año activo se publica.
- Las pruebas E2E `admin-cuestionario` y `admin-reglas` se adaptaron: un año publicado muestra el
  recuadro de corrección (ya no «solo lectura») y `admin-reglas` usa el siguiente año libre si 2027
  quedó publicado por `admin-publicar`.
- El inicio del encuestado muestra el avance por página.
- El `C_C` viaja cifrado (AES-GCM) en la sesión.
- La encuesta queda terminada al guardar las nueve páginas (D18): el inicio y "Terminar la encuesta"
  muestran el radicado de inmediato y las observaciones quedan opcionales ("Enviar observaciones").
- El resumen de errores de una página nombra la pregunta por su etiqueta, no por la columna.
- Las preguntas por página y las reglas efectivas se guardan en memoria y el avance se lee con una
  consulta por capítulo; un cambio en las tablas `FRM_*` exige reiniciar hasta la Fase 3.
- El backend registra un WARN si la semilla de reglas trae una versión mayor que el archivo vigente.
- El archivo de reglas de cada año guarda su `estado` (`borrador`; sin estado equivale a publicado) y
  las tablas de respuestas por capítulo; la lectura del cuestionario y las respuestas usan las tablas
  del año.
- `db/dev/schema.sql` incorpora la estructura de `FRM_*` usada por la administración: recree el
  contenedor de Oracle de desarrollo.
- `db/dev/schema.sql` sigue las columnas del respaldo real de `DANE_DIRPEN`: `FRM_RESPUESTA` sin
  `VALOR`, `FRM_PRE_AYUDA` y `FRM_ENC_AYUDA` con `COD_PRE_AYU` / `COD_ENC_AYU` y `COD_AYU`, y la
  tabla `GRAL_AYUDAS` (DDL real). Recree el contenedor de Oracle de desarrollo.

### Corregido
- Las opciones de respuesta toman su valor de `FRM_RTA_GRUPO.VALOR`: la base real no tiene
  `FRM_RESPUESTA.VALOR` y la consulta anterior fallaba con ORA-00904 (el encuestado no vería
  opciones).
- Ayudas ajustadas al modelo real: `FRM_PRE_AYUDA` y `FRM_ENC_AYUDA` se identifican por
  `COD_PRE_AYU` / `COD_ENC_AYU`; `TEXTO` es la palabra enlazada y `COD_AYU` la referencia a la
  definición en `GRAL_AYUDAS`, que la copia de año conserva y nunca genera. El editor llama
  "palabra enlazada" a lo que edita y muestra la definición en solo lectura; `PUT .../help/{codPreAyu}`
  reemplaza a `.../help/{codAyu}`. `frm.sql` aborta con ORA-20003 y la importación responde 422 si
  falta una definición en `GRAL_AYUDAS` del destino. Los paquetes de promoción generados antes de
  este cambio no son válidos (cambió la forma de las tablas de ayudas): hay que generarlos de nuevo.
- `ColumnCodeResolver` completa el cero de los numerales ("C7a." pasa a `C07A`).
- El bloqueo de inicio de sesión se comprueba con la IP real enviada por el proxy.
- Cerrar sesión ya no pierde el aviso de cierre fallido por la carrera con la redirección de la
  ruta protegida.
- Los valores que Oracle no aceptaría (longitud en bytes, precisión y escala de `NUMBER`) y las
  observaciones que no caben en `OBS` responden 422 en vez de 503.
- Un sujeto de sesión que no se puede descifrar responde 401 en vez de 500.
- La primera siembra concurrente del archivo de reglas ya no falla si otra petición lo creó antes.
- Terminar registra un WARN (sin texto ni `C_C`) si las observaciones no actualizaron ninguna fila.

### Seguridad
- Los borradores de página ya no pasan de un usuario a otro entre pestañas: cada ingreso y cada
  cierre de sesión cambian una generación aleatoria en `localStorage` y las demás pestañas
  descartan sus borradores (Ley 1581 de 2012).
- El radicado no revela el `C_C`.
- Las reglas ficticias de desarrollo no viajan en la imagen.
- Fuera de los perfiles `dev` y `test`, el backend no arranca con los valores de ejemplo de
  `JWT_SECRET` o `RECEIPT_KEY`.
- `PUT .../answers` acepta como máximo 200 respuestas (400) y 4000 caracteres por valor (422); las
  claves desconocidas se devuelven recortadas a 30 caracteres.
