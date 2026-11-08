-- Verificación de la base de producción (o QA) antes de la primera promoción.
-- Solo lectura: consulta el diccionario de Oracle, no lee ni modifica datos de encuestados.
-- Se ejecuta con el usuario disponible (no hace falta DBA). ALL_* muestra solo lo que ese usuario
-- puede ver: si una consulta no devuelve filas, puede ser falta de permiso y no ausencia del objeto.
--
-- Uso en SQL*Plus o SQL Developer:
--   @verificar-base-produccion.sql
-- Guarde la salida y compárela con db/dev/schema.sql y con «Ajuste al DDL real (2026-11-05)» de la
-- spec de la Fase 3.

SET PAGESIZE 200
SET LINESIZE 200
COLUMN OWNER FORMAT A14
COLUMN TABLE_NAME FORMAT A28
COLUMN COLUMN_NAME FORMAT A22
COLUMN DATA_TYPE FORMAT A12
COLUMN PARAMETER FORMAT A26
COLUMN VALUE FORMAT A30

PROMPT == 1. Juego de caracteres (debe coincidir con el de QA; produccion es WE8ISO8859P15)
SELECT PARAMETER, VALUE
  FROM NLS_DATABASE_PARAMETERS
 WHERE PARAMETER IN ('NLS_CHARACTERSET', 'NLS_NCHAR_CHARACTERSET', 'NLS_LENGTH_SEMANTICS');

PROMPT == 2. Usuario de la sesión y esquema por defecto
SELECT USER AS USUARIO, SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA') AS ESQUEMA FROM DUAL;

PROMPT == 3. Dueño de las tablas que usa la aplicación (GRAL_AYUDAS debe estar con las FRM_*)
SELECT OWNER, TABLE_NAME
  FROM ALL_TABLES
 WHERE TABLE_NAME LIKE 'FRM\_%' ESCAPE '\'
    OR TABLE_NAME IN ('GRAL_AYUDAS', 'EDIN_MUESTRA', 'GRAL_USUARIOS_ADM')
 ORDER BY TABLE_NAME, OWNER;

PROMPT == 4. Sinónimos hacia esas tablas (si la aplicación no califica el esquema)
SELECT OWNER, SYNONYM_NAME, TABLE_OWNER, TABLE_NAME
  FROM ALL_SYNONYMS
 WHERE TABLE_NAME LIKE 'FRM\_%' ESCAPE '\'
    OR TABLE_NAME = 'GRAL_AYUDAS'
 ORDER BY TABLE_NAME, OWNER;

PROMPT == 5. Columnas con tipo, longitud y nulabilidad (confirma db/dev/schema.sql)
SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, DATA_LENGTH, CHAR_LENGTH, CHAR_USED,
       DATA_PRECISION, DATA_SCALE, NULLABLE
  FROM ALL_TAB_COLUMNS
 WHERE OWNER = 'DANE_DIRPEN'
   AND (TABLE_NAME LIKE 'FRM\_%' ESCAPE '\' OR TABLE_NAME = 'GRAL_AYUDAS')
 ORDER BY TABLE_NAME, COLUMN_ID;

PROMPT == 6. Claves primarias y únicas
SELECT C.TABLE_NAME, C.CONSTRAINT_NAME, C.CONSTRAINT_TYPE, CC.COLUMN_NAME
  FROM ALL_CONSTRAINTS C
  JOIN ALL_CONS_COLUMNS CC
    ON CC.OWNER = C.OWNER AND CC.CONSTRAINT_NAME = C.CONSTRAINT_NAME
 WHERE C.OWNER = 'DANE_DIRPEN'
   AND C.CONSTRAINT_TYPE IN ('P', 'U')
   AND (C.TABLE_NAME LIKE 'FRM\_%' ESCAPE '\' OR C.TABLE_NAME = 'GRAL_AYUDAS')
 ORDER BY C.TABLE_NAME, C.CONSTRAINT_NAME, CC.POSITION;

PROMPT == 7. Secuencias y disparadores (la aplicación genera ids con MAX+1; si hay disparadores
PROMPT ==    que asignan ids, avise antes de usar la administración)
SELECT SEQUENCE_OWNER, SEQUENCE_NAME, LAST_NUMBER
  FROM ALL_SEQUENCES
 WHERE SEQUENCE_OWNER = 'DANE_DIRPEN';
SELECT OWNER, TRIGGER_NAME, TABLE_NAME, TRIGGERING_EVENT, STATUS
  FROM ALL_TRIGGERS
 WHERE TABLE_OWNER = 'DANE_DIRPEN'
   AND (TABLE_NAME LIKE 'FRM\_%' ESCAPE '\' OR TABLE_NAME = 'GRAL_AYUDAS');

PROMPT == 8. Permisos del usuario actual sobre esas tablas (quién puede correr frm.sql)
SELECT TABLE_NAME, PRIVILEGE
  FROM USER_TAB_PRIVS
 WHERE TABLE_NAME LIKE 'FRM\_%' ESCAPE '\'
    OR TABLE_NAME = 'GRAL_AYUDAS'
 ORDER BY TABLE_NAME, PRIVILEGE;
SELECT PRIVILEGE FROM SESSION_PRIVS
 WHERE PRIVILEGE LIKE '%ANY TABLE%' OR PRIVILEGE = 'CREATE TABLE';

-- ---------------------------------------------------------------------------------------------
-- Fase 4 (reportes, novedades y claves). Solo estructura y privilegios: sin datos personales.
-- ---------------------------------------------------------------------------------------------
PROMPT == 9. Estructura de los catálogos de la Fase 4 (confirma db/dev/schema.sql)
SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, DATA_LENGTH, CHAR_USED,
       DATA_PRECISION, DATA_SCALE, NULLABLE
  FROM ALL_TAB_COLUMNS
 WHERE OWNER = 'DANE_DIRPEN'
   AND TABLE_NAME IN ('GRAL_NOVEDAD', 'GRAL_ENTI', 'GRAL_DEPARTAMENTOS', 'GRAL_TERRITORIAL_ACCESO')
 ORDER BY TABLE_NAME, COLUMN_ID;

PROMPT == 10. Columnas de EDIN_MUESTRA que usa la Fase 4 (y H06 del capítulo H)
SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, DATA_LENGTH, CHAR_USED,
       DATA_PRECISION, DATA_SCALE, NULLABLE
  FROM ALL_TAB_COLUMNS
 WHERE OWNER = 'DANE_DIRPEN'
   AND ((TABLE_NAME = 'EDIN_MUESTRA'
         AND COLUMN_NAME IN ('ANIO', 'ENC', 'COD_ENT', 'COD_DEPTO', 'C_C', 'NOM_APE', 'AREA_TRA',
                             'ATV', 'TC1', 'TC2', 'LGN', 'PSW', 'IDFNOV', 'NOV_OTRA_CAUSA',
                             'IDMUES'))
     OR (TABLE_NAME = 'EDIN_20_CAP_H_PREV_PRACT' AND COLUMN_NAME = 'H06'))
 ORDER BY TABLE_NAME, COLUMN_ID;

PROMPT == 11. Privilegios para UPDATE sobre EDIN_MUESTRA (novedades y claves escriben ahí)
SELECT TABLE_NAME, PRIVILEGE
  FROM USER_TAB_PRIVS
 WHERE TABLE_NAME = 'EDIN_MUESTRA'
 ORDER BY PRIVILEGE;
SELECT PRIVILEGE FROM SESSION_PRIVS
 WHERE PRIVILEGE IN ('UPDATE ANY TABLE', 'SELECT ANY TABLE');

PROMPT == 12. Catálogo de novedades (sin datos personales): confirma el código de "otra causa"
PROMPT ==     (app.operation.novelty-other-cause-code, por defecto 8; el PHP mezcla 8 y 17)
SELECT IDFNOV, NOVEDAD FROM GRAL_NOVEDAD ORDER BY IDFNOV;
