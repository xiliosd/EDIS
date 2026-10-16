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
- "Cerrar sesión" pide confirmación si la página tiene respuestas sin guardar.

### Cambiado
- El inicio del encuestado muestra el avance por página.
- El `C_C` viaja cifrado (AES-GCM) en la sesión.
- La encuesta queda terminada al guardar las nueve páginas (D18): el inicio y "Terminar la encuesta"
  muestran el radicado de inmediato y las observaciones quedan opcionales ("Enviar observaciones").
- El resumen de errores de una página nombra la pregunta por su etiqueta, no por la columna.
- Las preguntas por página y las reglas efectivas se guardan en memoria y el avance se lee con una
  consulta por capítulo; un cambio en las tablas `FRM_*` exige reiniciar hasta la Fase 3.
- El backend registra un WARN si la semilla de reglas trae una versión mayor que el archivo vigente.

### Corregido
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
- El radicado no revela el `C_C`.
- Las reglas ficticias de desarrollo no viajan en la imagen.
- Fuera de los perfiles `dev` y `test`, el backend no arranca con los valores de ejemplo de
  `JWT_SECRET` o `RECEIPT_KEY`.
- `PUT .../answers` acepta como máximo 200 respuestas (400) y 4000 caracteres por valor (422); las
  claves desconocidas se devuelven recortadas a 30 caracteres.
