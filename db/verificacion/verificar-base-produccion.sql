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

PROMPT == 1. Juego de caracteres (se espera AL32UTF8; frm.sql aborta con ORA-20002 si difiere)
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
