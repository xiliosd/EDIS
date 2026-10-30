# Secuencia — crear un año del cuestionario

El administrador crea el año nuevo (borrador) a partir de un año publicado. Todo ocurre bajo el
candado global de administración; si algo falla, no queda ninguna fila ni archivo a medias.

```mermaid
sequenceDiagram
    actor A as Administrador
    participant F as Frontend
    participant B as Backend
    participant R as Archivo de reglas
    participant O as Oracle
    A->>F: Crear año (año nuevo, año de origen)
    F->>B: POST /api/v1/admin/years/{encuesta}
    B->>B: Rol ADMIN_CUESTIONARIO y candado de administración
    B->>B: Año nuevo no existe y no hay otro borrador abierto
    alt El año existe o hay un borrador abierto
        B-->>F: 409 (sin filas nuevas)
    else Año de origen sin preguntas o con PROY inexacto
        B-->>F: 422 con el motivo
    else Se puede crear
        B->>B: Reglas efectivas del origen como borrador (estado, versión 0)
        B->>O: Transacción: copia FRM_CAP_PERIODO, FRM_CAP_PROY y encabezados del año
        alt La copia falla a mitad
            O-->>B: rollback (conteos de filas sin cambio)
            B-->>F: 5xx sin filas parciales
        else La copia termina
            O-->>B: commit
            B->>R: Guarda EDIN-{año}.json con estado "borrador"
            alt El archivo no se puede escribir
                B->>O: Borra las filas recién copiadas (compensación)
                B-->>F: 409 o 5xx
            else Archivo escrito
                B->>B: Descarta cachés (tablas, preguntas, reglas)
                B-->>F: 201 {año, estado borrador, versión}
                F->>A: Abre el árbol del año nuevo
            end
        end
    end
```

Notas:
- Las filas de preguntas y grupos de FRM_* se comparten con el año de origen hasta que se edita
  una pregunta (ver la [secuencia de edición con copia al escribir](secuencia-editar-copia-al-escribir.md)).
- Un año con archivo sin `estado` es publicado y de solo lectura; solo existe un borrador por
  encuesta.
- El año nuevo no copia `columnasNuevas` del origen: esas columnas ya existen en la tabla o siguen
  pendientes en el año de origen.
