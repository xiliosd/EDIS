# Secuencia — asignar una novedad (Fase 4)

Solo el territorial asigna novedades, y solo en sus departamentos. La cédula (`C_C`) viaja en el cuerpo
del POST, nunca en la URL. La fila se bloquea (`FOR UPDATE`) y se compara con lo que la pantalla leyó:
si cambió entretanto, 409. La auditoría guarda el id de muestra y los códigos, sin cédula, nombre ni
texto libre. La operación funciona aunque el cuestionario esté en solo lectura
(`@AllowedInReadOnlyQuestionnaire`).

```mermaid
sequenceDiagram
    actor T as Territorial
    participant F as Frontend (NoveltiesPage)
    participant C as NoveltyController
    participant A as OperationalAccessService
    participant S as NoveltyService
    participant R as NoveltyRepository
    participant D as AdminAudit
    T->>F: Asignar novedad (código y, si es "otra causa", texto)
    F->>C: POST /admin/novelties/assign (C_C, año, código, texto, valores esperados)
    C->>A: current(jwt) — departamentos del usuario (GRAL_TERRITORIAL_ACCESO)
    C->>S: assign(acceso, usuario, solicitud)
    alt No gestiona novedades o ve todo el país
        S-->>F: 403
    end
    S->>R: findCatalog(año) — la novedad existe en GRAL_NOVEDAD
    alt Fuera del catálogo, o "otra causa" sin texto o de más de 500 bytes Latin-9
        S-->>F: 422 con el campo en errors
    end
    S->>R: findCurrentForUpdate(año, C_C, departamentos)
    alt El funcionario no está en sus departamentos
        S-->>F: 404 (mismo mensaje que si no existe)
    end
    alt La novedad actual no es la que la pantalla leyó
        S-->>F: 409 "Recargue la lista"
    end
    S->>R: updateNovelty — UPDATE EDIN_MUESTRA SET IDFNOV, NOV_OTRA_CAUSA
    S->>D: record(usuario, operación, año, [IDMUES, código anterior, código nuevo])
    S-->>F: 204 (Cache-Control: no-store)
    F->>F: recarga la lista y devuelve el foco a la fila del funcionario
```
