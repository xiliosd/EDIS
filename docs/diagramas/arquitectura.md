# Arquitectura — Formulario Web EDI/EDID

```mermaid
flowchart LR
  navegador[Navegador] -- HTTPS --> nginx[frontend: nginx + React]
  nginx -- "/api (cookie EDI_SESSION + X-XSRF-TOKEN)" --> api[backend: Spring Boot 4]
  api -- "JDBC (DANE_EDI_WEB)" --> oracle[(Oracle DANE_DIRPEN)]
  api -. "Fase 2+: lectura/escritura" .-> reglas[("Volumen rules-data: JSON de reglas (sin uso en Fase 1)")]
  api -. "Fase 4: cortes de cobertura (JSON agregado)" .-> cortes[("Volumen reports-data: REPORTS_DIR")]
```

Fase 4: los reportes y las claves se arman al vuelo como `.xlsx` (Apache POI) y no se guardan en disco;
solo los cortes de cobertura se guardan, como JSON con cifras agregadas, en el volumen `reports-data`.
