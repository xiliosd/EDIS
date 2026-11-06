# Secuencia — promover un año o una corrección de QA a producción

La aplicación **nunca** escribe `FRM_*` ni ejecuta DDL en producción: su usuario solo tiene `SELECT`
sobre `FRM_*`. La persona técnica responsable (no hay DBA) verifica la base con
`db/verificacion/verificar-base-produccion.sql`, ejecuta `columnas.sql` y `frm.sql` con un usuario con
permisos de escritura, y después se importa el paquete. La importación no autentica el ZIP con una firma: lo verifica contra
la base real de producción.

```mermaid
sequenceDiagram
    actor A as Administrador (QA)
    participant QA as Aplicación en QA
    actor D as Persona técnica
    participant DB as Oracle de producción
    actor P as Administrador (producción)
    participant PR as Aplicación en producción
    A->>QA: Paquetes → "Descargar paquete del año" (o "de corrección")
    QA->>QA: Bajo el candado: misma lista de publicación (422 si no pasa)
    QA->>QA: Fotografía de FRM_* del año + NLS_CHARACTERSET de origen
    QA-->>A: paquete-EDIN-AAAA-anio.zip (manifiesto.json, reglas.json, frm.sql, columnas.sql)
    A->>D: Entrega el ZIP por el canal acordado
    D->>DB: Confirma NLS_CHARACTERSET (igual al de QA; producción es WE8ISO8859P15) y ejecuta columnas.sql
    D->>DB: Ejecuta frm.sql en sesión nueva (idempotente; ORA-20001 si un id es de otra fila, ORA-20002 si cambia el juego de caracteres)
    D->>P: Avisa que los scripts corrieron
    P->>PR: Paquetes → elegir el ZIP → "Verificar" (confirmar=false)
    PR->>PR: 1. cuatro entradas exactas, tope 5 MB / 20 MB descomprimido
    PR->>PR: 2. SHA-256 de cada archivo contra el manifiesto
    PR->>PR: 3. reglas.json válido, publicado, de la encuesta, año y versión del paquete
    PR->>DB: 4. filas y sumas de FRM_* (solo SELECT) contra el manifiesto
    PR->>DB: 5. tablas y columnas de respuesta (derivadas de la base y de reglas.json)
    PR->>PR: 6. misma lista de publicación; versión posterior o igual con igual contenido
    alt Alguna revisión falla
        PR-->>P: 422 "Paquete rechazado" con la lista de diferencias
    else Todas pasan
        PR-->>P: Vista previa: impacto por página (solo cambios de reglas), aviso si pisa un cambio local
        P->>PR: "Importar" → "Confirmar importación" (confirmar=true)
        PR->>PR: Bajo el candado: reemplaza el archivo conservando la versión del paquete
        PR->>PR: Auditoría importar-paquete (código m + 12 hex del SHA-256 del manifiesto) y evict de cachés
        PR-->>P: "El paquete se importó." (sin reiniciar)
    end
```
