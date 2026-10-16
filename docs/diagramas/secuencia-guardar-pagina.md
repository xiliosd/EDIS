# Secuencia — guardar una página

```mermaid
sequenceDiagram
    actor E as Encuestado
    participant F as Frontend
    participant B as Backend
    participant O as Oracle
    E->>F: Responde la página
    F->>F: evaluate(reglas) — saltos y errores en vivo
    E->>F: Guardar página
    F->>E: ¿Guardar esta página? (escritura única)
    E->>F: Guardar y continuar
    F->>B: PUT /api/v1/survey/pages/{pagina}/answers
    B->>B: Candado por encuestado, estado AVAILABLE
    B->>B: RuleEvaluator, opciones, tipo y longitud
    alt Respuestas inválidas
        B-->>F: 422 errors columna → mensaje
    else Página ya guardada
        B-->>F: 409
    else Válidas
        B->>O: INSERT o UPDATE ... WHERE primera IS NULL
        B-->>F: 200 nextPageCode
        F->>E: Página siguiente (foco en el contenido)
    end
```

Notas:
- La validación del servidor respeta los límites reales de Oracle (bytes o caracteres según
  `CHAR_USED`, precisión y escala de `NUMBER`): lo que no cabe responde 422, nunca 503.
- Una sesión con firma válida cuyo sujeto de encuestado no se puede descifrar responde 401.
- El repositorio de respuestas rechaza las columnas del sistema (`C_C`, `ESTADO`, `TC`, `ANIO`,
  `FECHA`, `COMPLETA`, `OBS`) y exige un valor de guarda no vacío.
