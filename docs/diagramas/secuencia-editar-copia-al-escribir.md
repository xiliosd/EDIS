# Secuencia — editar una pregunta con copia al escribir

Editar el texto de una pregunta de un año en borrador nunca modifica una fila usada por otro
año: si la pregunta se comparte, se copia para el año que se edita y solo ese año se repunta.

```mermaid
sequenceDiagram
    actor A as Administrador
    participant F as Frontend
    participant B as Backend
    participant R as Archivo de reglas
    participant O as Oracle
    A->>F: Editar texto, escribir y pulsar Guardar
    F->>B: PUT /api/v1/admin/questionnaire/{encuesta}/{año}/entries/{capProyId}/text (texto, versión)
    B->>B: Rol ADMIN_CUESTIONARIO y candado de administración
    B->>B: El año está en borrador
    alt La versión enviada no es la vigente
        B-->>F: 409 "Otro usuario modificó el cuestionario"
        F->>A: Aviso con botón Recargar
    else Texto inválido
        B-->>F: 422 con el campo y el motivo
    else Otro año o encuesta usa la fila (SharedRowException)
        B->>O: rollback
        B-->>F: 409 "Esta fila la usa otro año o encuesta. Cree primero el año a partir del anterior."
    else Versión vigente
        B->>O: Inicia la transacción
        B->>O: ¿Otra pareja (encuesta, año) usa esta pregunta?
        alt La pregunta es compartida
            B->>O: Copia FRM_PREGUNTA y FRM_PRE_GRUPO
            B->>O: Repunta solo este FRM_CAP_PROY a la copia
        end
        B->>O: UPDATE del texto en la pregunta privada del año
        B->>R: Guarda el archivo del año (versión + 1, historial)
        alt Falla el guardado del archivo
            B->>O: rollback
            B-->>F: 5xx (si falla el archivo, la base y la versión no divergen)
        else Todo guardado
            B->>O: commit
            B->>B: Descarta cachés y registra la auditoría (usuario, año, operación, códigos)
            B-->>F: 200 {versión, página releída}
            F->>A: Muestra el texto nuevo y devuelve el foco al botón Editar
        end
    end
```

Notas:
- El texto se guarda como texto plano (sin etiquetas) con las entidades que lee el PHP heredado y
  siempre se muestra escapado; el comodín `200X` se conserva al editar.
- Caso borde: el archivo se guarda dentro de la transacción y antes del commit. Si el commit falla
  después de escribir el archivo, la base se revierte pero el archivo ya quedó con versión + 1 (la
  versión anterior queda en el historial). El administrador recibe un 5xx; con la versión que
  tenía, su siguiente intento responde 409 hasta que recargue, y la base conserva el estado anterior.
- La línea de auditoría lleva usuario, encuesta, año, operación y códigos afectados; nunca el
  texto de la pregunta.
- Reordenar y desactivar siguen el mismo patrón (candado, versión, transacción y archivo); un
  `FRM_CAP_PROY` usado por otro año responde 409 en vez de modificarse.
