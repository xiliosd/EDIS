# Fase 4: diferencias de los reportes frente al PHP

Los reportes nuevos (`/api/v1/admin/reports/...`) **no reproducen todas las cifras del admin PHP**: se
corrigieron nueve defectos del código heredado y cada uno cambia alguna cifra. Este documento explica,
por cada corrección, el reporte PHP afectado, la causa, el efecto esperado en las cifras y cómo
comprobarlo con una consulta de solo lectura. Fuente de las decisiones: diseño de la Fase 4, §5.4.

Convenciones del criterio nuevo (diseño §5.1), válidas para todo el documento:

- Muestra = funcionarios de `EDIN_MUESTRA` del año con `ATV = 1` (y `ENC` de la encuesta, si no es la general).
- Estado de cada funcionario, con una sola definición: **completa** si `B07` (capítulo B) y `H06` (capítulo H)
  no son nulos; **novedad** si `IDFNOV` no es nulo y no está completa; **incompleta** si `B07` no es nulo y
  `H06` es nulo; **sin diligenciar** = muestra efectiva - completas - incompletas.
- Muestra efectiva = muestra - novedades. Porcentajes sobre la muestra efectiva, con dos decimales.
- Los capítulos se unen por subconsultas agregadas por `C_C` (una fila por funcionario).

Las consultas de ejemplo son de solo lectura, usan marcadores (`:anio`, `:depto`) y no contienen datos. Se
ejecutan en un cliente SQL con permiso de consulta; ajuste el nombre de las tablas de capítulo al año si
difiere (`EDIN_20_CAP_B_UBICA` y `EDIN_20_CAP_H_PREV_PRACT` son las del año 2026).

---

## 1. Entidades con carga web y DMC se contaban dos veces

- **Reporte PHP afectado:** `2.Rep_gral_cob-nov.php`, `2.1.Rep_gral_edi.php`, `2.2.Rep_gral_edid.php`
  (cobertura general, EDI y EDID).
- **Causa:** la protección `if ($Last_cod_ent != COD_ENT)` está comentada. El PHP imprime una fila por
  `(entidad, TC1)` pero con los totales de la **entidad completa** (`MUES_TOTAL`, `NOV_TOTAL`, `EC_TOTAL`,
  `EI_TOTAL`) y los acumula. Una entidad con funcionarios de carga web (`TC1 = 1`) y DMC (`TC1 = 2`) suma
  sus totales dos veces al consolidado.
- **Efecto en las cifras:** el consolidado del PHP es **mayor** que el nuevo, exactamente en la muestra,
  novedades, completas e incompletas de cada entidad mixta (una vez de más). Los porcentajes del consolidado
  también cambian. Las entidades con un solo tipo de carga no cambian.
- **Cómo comprobarlo:** listar las entidades con más de un tipo de carga; la diferencia entre ambos
  reportes es la suma de sus totales.

```sql
SELECT COD_ENT, COUNT(DISTINCT TC1) AS TIPOS_DE_CARGA, COUNT(*) AS FUNCIONARIOS
FROM EDIN_MUESTRA
WHERE ANIO = :anio AND ATV = 1
GROUP BY COD_ENT
HAVING COUNT(DISTINCT TC1) > 1
ORDER BY COD_ENT;
```

## 2. `3.1` sumaba las incompletas por nivel del recordset equivocado

- **Reporte PHP afectado:** `3.1.Frm_rep-cob-nov.php` (ver reporte por departamento).
- **Causa:** los acumuladores `GT_Nn_EI_WEB` y `GT_Nn_EI_DMC` suman `$EC->fields('EI_Nn')`, un campo que no
  existe en el recordset de completas (`EC`), en lugar de `$EI->fields(...)`. `3.2` y los reportes `2.*` sí
  usan el recordset correcto.
- **Efecto en las cifras:** en el consolidado por nivel de `3.1`, las incompletas salen en cero (o erradas),
  y con ellas su porcentaje y el "sin diligenciar" derivado (que queda inflado). El resto de columnas no
  cambia. Con el criterio nuevo las incompletas por nivel coinciden con las de `3.2`.
- **Cómo comprobarlo:** contar incompletas por nivel de un departamento y compararlas con la fila del nivel
  en `3.1` (cero) y en `3.2` (correcta).

```sql
SELECT M.AREA_TRA AS NIVEL, COUNT(*) AS INCOMPLETAS
FROM EDIN_MUESTRA M
JOIN (SELECT C_C, MAX(B07) AS B07 FROM EDIN_20_CAP_B_UBICA GROUP BY C_C) B ON B.C_C = M.C_C
LEFT JOIN (SELECT C_C, MAX(H06) AS H06 FROM EDIN_20_CAP_H_PREV_PRACT GROUP BY C_C) H ON H.C_C = M.C_C
WHERE M.ANIO = :anio AND M.COD_DEPTO = :depto AND M.ATV = 1 AND M.TC1 = 1
  AND M.IDFNOV IS NULL AND B.B07 IS NOT NULL AND H.H06 IS NULL
GROUP BY M.AREA_TRA
ORDER BY M.AREA_TRA;
```

## 3. EDI y EDID filtraban `ENC` solo en la consulta externa

- **Reporte PHP afectado:** `2.1.Rep_gral_edi.php` (EDI) y `2.2.Rep_gral_edid.php` (EDID), con
  `SQL_EDI.php` y `SQL_EDID.php`.
- **Causa:** el filtro `AND M1.enc = '1'` (o `'2'`) está únicamente en la consulta externa. Las
  subconsultas correlacionadas de muestra, novedades, completas e incompletas cuentan por entidad sin
  `ENC`. Si una entidad tiene funcionarios de ambas encuestas, las cifras de EDI y EDID incluyen a las dos.
- **Efecto en las cifras:** el PHP **sobrecuenta** en EDI y en EDID para las entidades que están en las dos
  encuestas (cada una muestra la entidad completa). Ahora cada encuesta cuenta solo lo suyo y
  EDI + EDID no excede al general.
- **Cómo comprobarlo:** buscar entidades presentes en ambas encuestas.

```sql
SELECT COD_ENT,
       SUM(CASE WHEN ENC = '1' THEN 1 ELSE 0 END) AS EN_EDI,
       SUM(CASE WHEN ENC = '2' THEN 1 ELSE 0 END) AS EN_EDID
FROM EDIN_MUESTRA
WHERE ANIO = :anio AND ATV = 1
GROUP BY COD_ENT
HAVING SUM(CASE WHEN ENC = '1' THEN 1 ELSE 0 END) > 0
   AND SUM(CASE WHEN ENC = '2' THEN 1 ELSE 0 END) > 0
ORDER BY COD_ENT;
```

Nota: el reporte general no filtra por `ENC`, por lo que incluye también funcionarios con `ENC` nulo;
EDI y EDID, solo los marcados con `'1'` y `'2'`.

## 4. El Excel de estado operativo ignoraba el departamento en "sin diligenciar"

- **Reporte PHP afectado:** `4.2.Est_operativo_excel.php` (Excel de "Estado de mi operativo"), acción 3
  (sin diligenciar).
- **Causa:** para `Accion == 3` usa la variable `$DEPTO`, que no está definida en esa página, así que la
  consulta no lleva `AND M.COD_DEPTO = ...`. Además el texto del filtro termina en el nombre del archivo.
- **Efecto en las cifras:** el Excel de "sin diligenciar" de un departamento trae los funcionarios de
  **todo el país**; la pantalla sí filtra. Ahora el Excel respeta el departamento (y el alcance del rol:
  un territorial solo recibe los suyos). Las filas sobrantes desaparecen.
- **Cómo comprobarlo:** contar los sin diligenciar de un departamento y compararlos con el total nacional.
  El Excel PHP debería coincidir con el segundo, no con el primero.

```sql
SELECT COUNT(*) AS SIN_DILIGENCIAR_DEL_DEPARTAMENTO
FROM EDIN_MUESTRA M
LEFT JOIN (SELECT C_C FROM EDIN_20_CAP_B_UBICA GROUP BY C_C) B ON B.C_C = M.C_C
LEFT JOIN (SELECT C_C FROM EDIN_20_CAP_H_PREV_PRACT GROUP BY C_C) H ON H.C_C = M.C_C
WHERE M.ANIO = :anio AND M.COD_DEPTO = :depto AND M.ATV = 1
  AND M.IDFNOV IS NULL AND B.C_C IS NULL AND H.C_C IS NULL;
-- Sin la condición M.COD_DEPTO = :depto se obtiene el total nacional que arrastraba el Excel.
```

## 5. Completo prevalece sobre novedad

- **Reporte PHP afectado:** los de cobertura (`2.*`, `3.1`, `3.2`) y el listado `4.Est_operativo.php`.
- **Causa:** la muestra efectiva del PHP resta todas las novedades (`MUES - NOV`), pero las consultas de
  completas (`EC`) e incompletas (`EI`) **no excluyen** a quienes tienen `IDFNOV`. Un funcionario con
  novedad que además diligenció la encuesta sale de la muestra efectiva y aun así se cuenta como completo
  (y lo mismo con incompleto), de modo que `completas + incompletas` puede exceder la efectiva y "sin
  diligenciar" puede salir negativo.
- **Efecto en las cifras:** con el criterio nuevo, un funcionario completo se cuenta como completo y **no**
  como novedad (la novedad solo aplica si no completó). Por eso las novedades de la cobertura pueden ser
  **menores** que `COUNT(IDFNOV)` del PHP, la muestra efectiva queda coherente y `sin diligenciar` nunca es
  negativo. Las completas del PHP no cambian en este punto; cambia lo que se resta como novedad.
- **Cómo comprobarlo:** contar los funcionarios con novedad que además están completos.

```sql
SELECT COUNT(*) AS NOVEDAD_Y_COMPLETA
FROM EDIN_MUESTRA M
JOIN (SELECT C_C, MAX(B07) AS B07 FROM EDIN_20_CAP_B_UBICA GROUP BY C_C) B ON B.C_C = M.C_C
JOIN (SELECT C_C, MAX(H06) AS H06 FROM EDIN_20_CAP_H_PREV_PRACT GROUP BY C_C) H ON H.C_C = M.C_C
WHERE M.ANIO = :anio AND M.ATV = 1 AND M.IDFNOV IS NOT NULL
  AND B.B07 IS NOT NULL AND H.H06 IS NOT NULL;
```

## 6. El listado "Todas" usaba otro criterio de estado

- **Reporte PHP afectado:** `4.Est_operativo.php` y `4.2.Est_operativo_excel.php`, listado "Todas"
  (`Accion = 5`). (Los listados nominales de la app nueva llegan en tareas posteriores; esta corrección fija
  el criterio que usarán.)
- **Causa:** en "Todas" el estado se calcula por el número de capítulos con fila (7 = completo, 1 a 6 =
  incompleto, 0 = sin diligenciar), mientras que la cobertura y los listados "Completas" e "Incompletas"
  usan `B07` y `H06`.
- **Efecto en las cifras:** los conteos de "Todas" no coincidían con los de cobertura ni con el resto de
  listados. Quien tenía filas en los siete capítulos pero `H06` nulo salía "completo" en "Todas" e
  "incompleto" en cobertura; y quien tenía `B07` y `H06` pero le faltaba una fila de capítulo intermedio
  salía "incompleto" en "Todas" y "completo" en cobertura. Ahora hay una sola definición.
- **Cómo comprobarlo:** contar funcionarios con filas en los siete capítulos pero sin `H06` (el PHP los
  llamaba completos en "Todas"). Con `C..G` las tablas intermedias, análogas a las de B y H.

```sql
SELECT COUNT(*) AS SIETE_CAPITULOS_SIN_H06
FROM EDIN_MUESTRA M
JOIN EDIN_20_CAP_B_UBICA B ON B.C_C = M.C_C
JOIN EDIN_20_CAP_C_EXP_PERS C ON C.C_C = M.C_C
JOIN EDIN_20_CAP_D_GEST_TAL D ON D.C_C = M.C_C
JOIN EDIN_20_CAP_E_USO_TECN E ON E.C_C = M.C_C
JOIN EDIN_20_CAP_F_GEST_ENT F ON F.C_C = M.C_C
JOIN EDIN_20_CAP_G_REN_CUEN G ON G.C_C = M.C_C
JOIN EDIN_20_CAP_H_PREV_PRACT H ON H.C_C = M.C_C
WHERE M.ANIO = :anio AND M.ATV = 1 AND H.H06 IS NULL;
```

## 7. Consultas emparejadas por posición (muestra, completas, incompletas)

- **Reporte PHP afectado:** todos los de cobertura (`2.*`, `3.1`, `3.2`).
- **Causa:** `MuestraAndNovedad`, `EC` y `EI` son tres consultas separadas que el PHP recorre en paralelo
  con `MoveNext()` por posición, confiando en que devuelvan las mismas filas `(entidad, TC1)` en el mismo
  orden. La primera hace inner join con `GRAL_ENTI` y las otras dos no: una entidad que falte en el catálogo
  o cualquier diferencia entre ellas desalinea las columnas en silencio (cifras de una entidad bajo otra).
- **Efecto en las cifras:** en los casos desalineados, las completas e incompletas del PHP aparecen en la
  fila equivocada y los totales por departamento no son confiables. Ahora una sola consulta agrupada
  entrega todo por entidad; una entidad sin nombre en el catálogo aparece como `Entidad <código>` y se
  cuenta igual. Si no hay desalineación, las cifras no cambian.
- **Cómo comprobarlo:** listar entidades de la muestra que no están en el catálogo (las que provocan el
  desalineamiento).

```sql
SELECT DISTINCT TO_NUMBER(M.COD_ENT) AS COD_ENT
FROM EDIN_MUESTRA M
LEFT JOIN GRAL_ENTI E ON E.COD_ENT = TO_NUMBER(M.COD_ENT)
WHERE M.ANIO = :anio AND M.ATV = 1 AND E.COD_ENT IS NULL
ORDER BY 1;
```

Un resultado vacío indica que en este año la corrección no altera cifras. Nota relacionada: los
funcionarios con departamento fuera de `GRAL_DEPARTAMENTOS` quedan fuera del reporte (como en el PHP, que
lista los departamentos desde el catálogo).

## 8. Columnas `HORA_H` y `FECHA_H` invertidas en rendimientos

- **Reporte PHP afectado:** `17.Reporte_rendimientos.php` (insumo de rendimientos).
- **Causa:** los dos últimos encabezados están rotulados `FECHA_H, HORA_H`, pero las celdas se imprimen en
  el orden `HORA_H, FECHA_H`. Quien lee el Excel ve la hora bajo "FECHA_H" y la fecha bajo "HORA_H".
- **Efecto en las cifras:** no cambia ningún total; cambia la posición de dos columnas del capítulo H. El
  reporte nuevo rotula y ordena la fecha y la hora de H como las demás (`FECHA_B`/`HORA_B` ... `FECHA_H`/
  `HORA_H`). Quien procese el Excel antiguo por posición debe invertir esas dos columnas al comparar.
- **Cómo comprobarlo:** la consulta de rendimientos entrega ambas columnas con su alias correcto.

```sql
SELECT TO_CHAR(H.FECHA, 'DD-MM-YYYY') AS FECHA_H, TO_CHAR(H.FECHA, 'HH24:MI:SS') AS HORA_H
FROM EDIN_20_CAP_H_PREV_PRACT H
WHERE H.H06 IS NOT NULL AND ROWNUM <= 5;
-- Al abrir el Excel del PHP, la columna rotulada FECHA_H trae valores como los de HORA_H, y al revés.
```

## 9. "Número de entidades" contaba filas, no entidades

- **Reporte PHP afectado:** consolidado de `2.Rep_gral_cob-nov.php`, `2.1.Rep_gral_edi.php`,
  `2.2.Rep_gral_edid.php` ("NUMERO ENTIDADES").
- **Causa:** el contador `$A` aumenta por cada fila `(entidad, TC1)`, no por entidad distinta. Una entidad
  con carga web y DMC cuenta dos.
- **Efecto en las cifras:** el número de entidades del PHP es **mayor o igual** que el real; la diferencia
  es el número de entidades con más de un tipo de carga (las mismas de la corrección 1). El número de
  departamentos no cambia.
- **Cómo comprobarlo:** comparar filas contra entidades distintas.

```sql
SELECT COUNT(*) AS FILAS_ENTIDAD_Y_CARGA,
       COUNT(DISTINCT COD_ENT) AS ENTIDADES
FROM (SELECT DISTINCT COD_ENT, TC1
      FROM EDIN_MUESTRA
      WHERE ANIO = :anio AND ATV = 1);
```

---

## Otras diferencias de criterio (sin cifra equivalente en el PHP)

- **Año por parámetro:** el PHP tenía el año fijo (`2026`); la app lo recibe (`anio`, por defecto el de la
  configuración) y lista los años disponibles de la muestra.
- **Encuesta:** `GENERAL`, `EDI` (`ENC = '1'`) y `EDID` (`ENC = '2'`) con un solo servicio.
- **Alcance:** un territorial solo recibe los departamentos que tiene asignados; pedir otro responde 403.
  El PHP no filtraba por departamento en varios reportes.
- **Nivel por carga web:** el detalle por nivel (1, 2, 3) cuenta solo la carga web (`TC1 = 1`), como el PHP,
  pero ahora está rotulado así y siempre aparecen los tres niveles.
- **Salida:** el PHP guardaba tablas HTML con extensión `.xls` en una carpeta pública; la app entrega un
  `.xlsx` real al descargar, sin guardar el archivo en el servidor, con `Cache-Control: no-store`, y los
  textos de la base siempre como texto (nunca como fórmula).
- **Reporte no disponible:** si las tablas de capítulo del año no tienen `B07` o `H06`, el reporte responde
  422 en lugar de cifras erradas.

## Cómo validar el conjunto

1. Elegir un departamento y año con datos y obtener la cobertura en la app (`GET /api/v1/admin/reports/coverage`).
2. Ejecutar, con el mismo departamento, las consultas de las correcciones 1, 3, 5 y 7. Las diferencias frente
   al reporte PHP deben explicarse con esos conteos (entidades mixtas, entidades en ambas encuestas,
   funcionarios con novedad y completos, entidades fuera del catálogo).
3. Si queda una diferencia sin explicar, registrarla aquí antes de aprobar el reporte.
