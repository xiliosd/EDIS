# Secuencia — guardar una regla (o una validación, un caso o una importación)

Todas las ediciones de reglas pasan por `RulesAdminService.apply`, que corre dentro del candado
global de administración (`SaveLocks.withAdminLock`). No toca Oracle: solo el archivo de reglas del
año (`RULES_DIR/<ENCUESTA>-<AÑO>.json`).

```mermaid
sequenceDiagram
    actor A as Administrador
    participant F as Frontend (RulesPage)
    participant S as RulesAdminService
    participant Y as YearCatalogService
    participant N as RuleSetAnalyzer
    participant R as RulesFileRepository
    participant C as CaseRunner
    A->>F: Guardar regla (constructor)
    F->>S: PUT .../rules/skips/{ruleId} con la regla y la version leida
    S->>S: SaveLocks.withAdminLock
    S->>Y: requireDraft(encuesta, año)
    alt El año está publicado
        S-->>F: 409 "El cuestionario de AAAA está publicado y no se puede editar."
    end
    S->>Y: requireYear(encuesta, año) — archivo vigente
    alt La version no coincide
        S-->>F: 409 "Otro usuario modificó el cuestionario. Recargue la página."
    end
    S->>S: candidato = archivo vigente con la regla (más los saltos heredados)
    S->>N: analyze(vigente) — informe antes
    S->>N: analyze(candidato) — informe después
    alt Problemas de estructura del candidato
        S-->>F: 422 ESTRUCTURA con el informe
    else El informe después tiene errores que el de antes no tenía
        Note over S,N: igualdad por code, element y questions
        S-->>F: 422 con los errores nuevos y las advertencias
        F->>A: Alerta (el diálogo sigue abierto con el borrador)
    else Sin errores nuevos (las advertencias nunca bloquean)
        S->>R: save(candidato, version, usuario, operación, códigos)
        R-->>S: archivo guardado (version + 1, historial y auditoría)
        S->>S: EffectiveRulesService.evict()
        S->>C: runAll(reglas efectivas, hechos) — resultado de los casos
        S-->>F: 200 version, informe y resultado de cada caso
        F->>A: Regla guardada (foco en la fila)
    end
```

Notas:
- La respuesta (versión, informe y casos) se arma sin soltar el candado, para que otro guardado no
  se interponga entre el archivo y lo que se devuelve.
- `RulesFileRepository.save` vuelve a comprobar la versión; la operación (`editar-salto`,
  `borrar-salto`, `editar-validacion`, `borrar-validacion`, `editar-caso`, `borrar-caso`,
  `importar-reglas`) y los códigos (ids de regla o de caso, columnas) son lo único que va al log de
  auditoría: nunca texto libre.
- Un error que ya existía en el archivo vigente (por ejemplo, una columna del 2026 real que no está
  en el cuestionario ficticio) se muestra pero no bloquea un guardado que no lo agrava. El plan 3c
  exigirá cero errores para publicar.
- La importación (`PUT .../rules/import`) sigue el mismo camino; antes del candado rechaza con 413
  un cuerpo de más de 1 MB y con 422 los ids que la auditoría no admitiría.
