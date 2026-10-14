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
