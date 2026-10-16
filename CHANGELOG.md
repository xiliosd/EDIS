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
- Prueba E2E del flujo del encuestado con verificación de accesibilidad.

### Cambiado
- El inicio del encuestado muestra el avance por página.
- El `C_C` viaja cifrado (AES-GCM) en la sesión.

### Corregido
- `ColumnCodeResolver` completa el cero de los numerales ("C7a." pasa a `C07A`).
- El bloqueo de inicio de sesión se comprueba con la IP real enviada por el proxy.

### Seguridad
- El radicado no revela el `C_C`.
- Las reglas ficticias de desarrollo no viajan en la imagen.
