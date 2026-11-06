# MER — tablas usadas

> Tipos, longitudes y claves de las `FRM_*` confirmados con el diccionario de la base real
> (`DANE_DIRPEN`, 2026-11-06; juego de caracteres `WE8ISO8859P15`, longitudes en bytes). Sin
> secuencias ni disparadores: los ids se generan con MAX+1. `FRM_RTA_GRUPO` tiene clave compuesta
> `(COD_GRUPO, GRUPO)`. El DDL completo está en `db/dev/schema.sql`. Aquí solo se listan las
> columnas que usa la aplicación.

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
    number COD_CAP_PROY "PK"
    number COD_PRE_GRUPO
    varchar CAPITULO "VARCHAR2(2)"
    varchar NUMERAL "VARCHAR2(5)"
    varchar ANIO "VARCHAR2(4)"
  }
  FRM_PRE_GRUPO {
    number COD_PRE_GRUPO
    number COD_PRE
    number GRUPO
    number ORDEN
  }
  FRM_PREGUNTA {
    number COD_PRE "PK"
    varchar PREGUNTA "VARCHAR2(1000)"
    varchar TIPO "VARCHAR2(1)"
  }
  FRM_CAP_PERIODO {
    number COD_CAP_PROY
    varchar PROY "VARCHAR2(10)"
    number ANIO "NUMBER(4)"
    varchar NUMERAL "VARCHAR2(10)"
  }
  FRM_SALTO {
    number COD_CAP_PROY
    number SALTO
    number VALOR_SALTO "NUMBER(1)"
  }
  FRM_OBJ_PRE {
    number COD_CAP_PROY
    number COD_OBJ
    number GRUPO
  }
  FRM_RTA_GRUPO {
    number COD_GRUPO "PK compuesta"
    number GRUPO "PK compuesta"
    varchar ORDEN "VARCHAR2(2)"
    varchar VALOR "valor de la opción, VARCHAR2(5)"
    number COD_RTA
  }
  FRM_RESPUESTA {
    number COD_RTA
    varchar RESPUESTA "VARCHAR2(500), sin VALOR"
  }
  FRM_PRE_AYUDA {
    number COD_PRE_AYU "clave natural"
    number COD_CAP_PROY
    number COD_AYU
    varchar TEXTO "palabra enlazada, VARCHAR2(100)"
  }
  FRM_ENC_AYUDA {
    number COD_ENC_AYU "PK"
    number COD_ENC
    number COD_AYU
    varchar TEXTO "palabra enlazada, VARCHAR2(100)"
  }
  GRAL_AYUDAS {
    varchar PROY
    number COD "único en la tabla"
    varchar TITULO
    varchar DESCRIPCION "HTML con entidades"
    number ANIO
  }
  FRM_ENCABEZADO {
    number COD_ENC "PK"
    number COD_CAP_PROY
    varchar TIPO "VARCHAR2(1)"
    varchar ENCABEZADO "VARCHAR2(500)"
    number ANIO "NUMBER(4)"
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
