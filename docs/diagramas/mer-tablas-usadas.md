# MER — tablas usadas

> Columnas de las `FRM_*` confirmadas con el respaldo real de DANE_DIRPEN (16-oct-2026; salen de
> los INSERT y las claves de los índices únicos, porque el respaldo no trae sus CREATE TABLE).
> `GRAL_AYUDAS` es el único DDL completo del respaldo. Siguen **sin confirmar**: los tipos y
> longitudes de las `FRM_*`, la nulabilidad y cómo se generan los ids (secuencias o disparadores).
> Aquí solo se listan las columnas que usa la aplicación.

```mermaid
erDiagram
  FRM_CAP_PROY ||--|| FRM_PRE_GRUPO : "COD_PRE_GRUPO"
  FRM_PRE_GRUPO }o--|| FRM_PREGUNTA : "COD_PRE"
  FRM_CAP_PROY ||--o{ FRM_CAP_PERIODO : "COD_CAP_PROY"
  FRM_CAP_PROY ||--o| FRM_SALTO : "COD_CAP_PROY"
  FRM_CAP_PROY ||--o| FRM_OBJ_PRE : "COD_CAP_PROY"
  FRM_CAP_PROY ||--o{ FRM_ENCABEZADO : "COD_CAP_PROY"
  FRM_OBJ_PRE }o--o{ FRM_RTA_GRUPO : "GRUPO"
  FRM_RTA_GRUPO }o--|| FRM_RESPUESTA : "COD_RTA"
  FRM_CAP_PROY ||--o{ FRM_PRE_AYUDA : "COD_CAP_PROY"
  FRM_ENCABEZADO ||--o{ FRM_ENC_AYUDA : "COD_ENC"
  FRM_PRE_AYUDA }o--o| GRAL_AYUDAS : "COD_AYU = COD"
  FRM_ENC_AYUDA }o--o| GRAL_AYUDAS : "COD_AYU = COD"
  EDIN_MUESTRA ||--o| EDIN_20_CAP_B_UBICA : "C_C"
  FRM_CAP_PROY {
    number COD_CAP_PROY
    number COD_PRE_GRUPO
    varchar CAPITULO
  }
  FRM_PRE_GRUPO {
    number COD_PRE_GRUPO
    number COD_PRE
    number GRUPO
    number ORDEN
  }
  FRM_PREGUNTA {
    number COD_PRE
    varchar PREGUNTA
    varchar TIPO
  }
  FRM_CAP_PERIODO {
    number COD_CAP_PROY
    varchar PROY
    varchar ANIO
    varchar NUMERAL
  }
  FRM_SALTO {
    number COD_CAP_PROY
    number SALTO
    number VALOR_SALTO
  }
  FRM_OBJ_PRE {
    number COD_CAP_PROY
    number COD_OBJ
    number GRUPO
  }
  FRM_RTA_GRUPO {
    number COD_GRUPO
    number GRUPO
    varchar ORDEN
    varchar VALOR "valor de la opción"
    number COD_RTA
  }
  FRM_RESPUESTA {
    number COD_RTA
    varchar RESPUESTA "sin VALOR en la base real"
  }
  FRM_PRE_AYUDA {
    number COD_PRE_AYU "clave natural"
    number COD_CAP_PROY
    number COD_AYU
    varchar TEXTO "palabra enlazada"
  }
  FRM_ENC_AYUDA {
    number COD_ENC_AYU "PK"
    number COD_ENC
    number COD_AYU
    varchar TEXTO "palabra enlazada"
  }
  GRAL_AYUDAS {
    varchar PROY
    number COD "único en la tabla"
    varchar TITULO
    varchar DESCRIPCION "HTML con entidades"
    number ANIO
  }
  FRM_ENCABEZADO {
    number COD_ENC
    number COD_CAP_PROY
    varchar TIPO
    varchar ENCABEZADO
    varchar ANIO
  }
  EDIN_MUESTRA {
    varchar C_C
    varchar LGN
    varchar PSW
    varchar ANIO
    number TC1
    number ATV
    varchar COD_ENT
  }
  EDIN_20_CAP_B_UBICA {
    varchar C_C
    number ESTADO
    varchar B02
    varchar B06
    varchar COMPLETA
  }
```
