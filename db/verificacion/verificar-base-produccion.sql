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

-- ---------------------------------------------------------------------------------------------
-- Fase 4: supuestos sobre los DATOS de la base real. Solo de lectura y SIN datos personales:
-- todas las consultas devuelven conteos, tipos o códigos de catálogo. Si alguna no da lo esperado,
-- NO promueva la Fase 4 y avise: las cifras de los reportes dependen de estos supuestos.
-- Ajuste el año de las tablas de capítulo (aquí «20», como EDIN_20_CAP_B_UBICA) si cambia.
-- ---------------------------------------------------------------------------------------------
PROMPT == 13. GRAL_USUARIOS_ADM.COD_USUA y GRAL_TERRITORIAL_ACCESO.COD_USUA: mismo identificador
PROMPT ==     Los tipos se muestran solo como información: pueden ser distintos (en el respaldo la
PROMPT ==     de usuarios es texto y la de acceso NUMBER(10)) porque la aplicación une ambas con
PROMPT ==     TO_CHAR. Lo que bloquea es la segunda consulta: filas_de_acceso_sin_usuario debe ser 0.
PROMPT ==     Si es mayor, esas asignaciones no resuelven el alcance territorial: no promover y
PROMPT ==     revisar con quien administra los usuarios cómo se relacionan.
PROMPT ==     El dueño se toma del esquema actual de la sesión; si las tablas son de otro dueño
PROMPT ==     (consulta 3, p. ej. DANE_DIRPEN), ejecute antes ALTER SESSION SET CURRENT_SCHEMA.
SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, DATA_LENGTH, DATA_PRECISION
  FROM ALL_TAB_COLUMNS
 WHERE OWNER = SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA')
   AND COLUMN_NAME = 'COD_USUA'
   AND TABLE_NAME IN ('GRAL_USUARIOS_ADM', 'GRAL_TERRITORIAL_ACCESO')
 ORDER BY TABLE_NAME;
SELECT (SELECT COUNT(*) FROM GRAL_TERRITORIAL_ACCESO) AS FILAS_DE_ACCESO,
       (SELECT COUNT(*)
          FROM GRAL_TERRITORIAL_ACCESO A
         WHERE NOT EXISTS (SELECT 1
                             FROM GRAL_USUARIOS_ADM U
                            WHERE TO_CHAR(U.COD_USUA) = TO_CHAR(A.COD_USUA)))
         AS FILAS_DE_ACCESO_SIN_USUARIO
  FROM DUAL;

PROMPT == 14. Filas repetidas por C_C en los capítulos B y H
PROMPT ==     Esperado: 0 en ambas. Si hay repetidas, los reportes ya no cuentan dos veces (usan el
PROMPT ==     máximo por C_C), pero el avance del funcionario depende de cuál fila se considere:
PROMPT ==     avise y revise esas cédulas con quien administra los datos antes de publicar cifras.
SELECT COUNT(*) AS CEDULAS_REPETIDAS_EN_B
  FROM (SELECT C_C FROM EDIN_20_CAP_B_UBICA GROUP BY C_C HAVING COUNT(*) > 1);
SELECT COUNT(*) AS CEDULAS_REPETIDAS_EN_H
  FROM (SELECT C_C FROM EDIN_20_CAP_H_PREV_PRACT GROUP BY C_C HAVING COUNT(*) > 1);

PROMPT == 15. Valores distintos de IDFNOV en la muestra (códigos de catálogo, no personales)
PROMPT ==     Esperado: «sin novedad» aparece como NULL y NO como 0, y cada código existe en la
PROMPT ==     consulta 12. Si «sin novedad» se guardó como 0, la aplicación lo mostraría como una
PROMPT ==     novedad inexistente y contaría de más: no promover y avisar.
SELECT IDFNOV, COUNT(*) AS FILAS
  FROM EDIN_MUESTRA
 GROUP BY IDFNOV
 ORDER BY IDFNOV NULLS FIRST;

PROMPT == 16. Filas de la muestra con COD_ENT o COD_DEPTO no numéricos
PROMPT ==     Esperado: 0 en las tres cuentas. Los reportes convierten esos códigos con TO_NUMBER:
PROMPT ==     una sola fila no numérica NO queda fuera, hace fallar el reporte entero (ORA-01722,
PROMPT ==     error 500) para todo el país y todos los departamentos. Si hay, corrija esos códigos
PROMPT ==     en la muestra antes de promover.
SELECT NVL(SUM(CASE WHEN COD_ENT IS NOT NULL
                     AND NOT REGEXP_LIKE(TRIM(TO_CHAR(COD_ENT)), '^[0-9]+$')
                    THEN 1 ELSE 0 END), 0) AS COD_ENT_NO_NUMERICO,
       NVL(SUM(CASE WHEN COD_DEPTO IS NOT NULL
                     AND NOT REGEXP_LIKE(TRIM(TO_CHAR(COD_DEPTO)), '^[0-9]+$')
                    THEN 1 ELSE 0 END), 0) AS COD_DEPTO_NO_NUMERICO,
       NVL(SUM(CASE WHEN COD_ENT IS NULL OR COD_DEPTO IS NULL THEN 1 ELSE 0 END), 0)
         AS CON_CODIGO_NULO
  FROM EDIN_MUESTRA;

PROMPT == 17. Filas con NOV_OTRA_CAUSA que traen referencias numéricas «&#» (solo el conteo)
PROMPT ==     Esperado: cualquier valor. Si es mayor que 0, el texto viene guardado con entidades
PROMPT ==     (tildes y comillas tipográficas como «&#8220;») y la aplicación las convierte al leer
PROMPT ==     y al escribir: compruebe en Reportes > Novedades que se vean con sus tildes. Si se ven
PROMPT ==     los códigos «&#...;», avise. No se muestra ningún texto.
SELECT COUNT(*) AS FILAS_CON_REFERENCIAS_NUMERICAS
  FROM EDIN_MUESTRA
 WHERE NOV_OTRA_CAUSA IS NOT NULL
   AND INSTR(NOV_OTRA_CAUSA, '&#') > 0;

PROMPT == 18. Filas de la muestra con COD_DEPTO numérico que no está en GRAL_DEPARTAMENTOS
PROMPT ==     (solo el conteo). Esperado: 0. Si es mayor, esas filas siguen contando en Cobertura,
PROMPT ==     Novedades, Rendimientos y Claves, pero con el nombre «Departamento N»; el territorial
PROMPT ==     no las ve porque su alcance sale del catálogo. Revise el catálogo o esos códigos.
SELECT COUNT(*) AS FILAS_CON_DEPTO_SIN_CATALOGO
  FROM EDIN_MUESTRA M
 WHERE M.COD_DEPTO IS NOT NULL
   AND REGEXP_LIKE(TRIM(TO_CHAR(M.COD_DEPTO)), '^[0-9]+$')
   AND NOT EXISTS (SELECT 1
                     FROM GRAL_DEPARTAMENTOS D
                    WHERE D.COD_DEPTO = TO_NUMBER(CASE WHEN REGEXP_LIKE(TRIM(TO_CHAR(M.COD_DEPTO)),
                                                           '^[0-9]+$')
                                               THEN TRIM(TO_CHAR(M.COD_DEPTO)) END));
