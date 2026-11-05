# Secuencia — publicar un año

Publicar pasa por `PublicationAdminController` y corre dentro del candado global de administración
(`SaveLocks.withAdminLock`): la lista se recalcula ahí, no se confía en la que el navegador vio.
No toca Oracle para escribir: solo cambia el `estado` en el archivo de reglas del año
(`RULES_DIR/<ENCUESTA>-<AÑO>.json`). Las filas `FRM_*` se leen para comprobar columnas y tablas.

```mermaid
sequenceDiagram
    actor A as Administrador
    participant F as Frontend (PublishPage)
    participant S as PublicationService
    participant Y as YearCatalogService
    participant N as RuleSetAnalyzer
    participant C as CaseRunner
    participant Q as Columnas y tablas (ALL_TAB_COLUMNS)
    participant R as RulesFileRepository
    A->>F: Publicar → "Confirmar publicación"
    F->>S: POST .../publication con la version leída
    S->>S: SaveLocks.withAdminLock
    S->>Y: requireYear(encuesta, año) — archivo vigente
    alt El año ya está publicado
        S-->>F: 409 "El cuestionario de AAAA está publicado: solo se permiten correcciones…"
    end
    alt La version no coincide
        S-->>F: 409 "Otro usuario modificó el cuestionario. Recargue la página."
    end
    S->>N: analyze(reglas) — cero errores
    S->>C: runAll(reglas efectivas, hechos) — todo caso en pasa (falla y por_revisar bloquean)
    S->>Q: cada pregunta activa con su columna (ACTIVE) y cada tabla mapeada válida
    alt Alguna de las cuatro condiciones falla
        S-->>F: 422 "Lista de publicación incompleta" con la lista (checklist)
        F->>A: Resumen enfocable con enlaces a cada caso, regla, columna o tabla
    else Lista completa (los avisos nunca bloquean)
        S->>R: save(estado publicado + publicacion{usuario, fecha, version, avisos}, version, usuario, "publicar")
        R-->>S: archivo guardado (version + 1) y una línea de auditoría
        S->>S: EffectiveRulesService.evict()
        S-->>F: 200 con la lista recalculada, dentro del candado
        F->>A: "El año AAAA quedó publicado."
    end
```

## Después de publicar

- La edición estructural (agregar, desactivar, reordenar, tablas) responde 409.
- Las correcciones (textos, encabezados, ayudas, saltos, validaciones, casos) piden un `motivo` de
  10 a 500 caracteres, que queda solo en `ultimoCambio.motivo` del archivo, y se auditan como
  `corregir-<operación>`.
- Una corrección se rechaza (422) si deja algún caso en rojo que antes no lo estaba.
- Si `SURVEY_YEAR` apunta a este año, publicarlo levanta por sí mismo el 503 de la compuerta de la 3a.
