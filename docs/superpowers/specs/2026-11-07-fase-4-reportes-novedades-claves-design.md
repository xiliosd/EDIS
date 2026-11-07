# Fase 4 — Reportes, novedades, claves y roles operativos (diseño)

Fecha: 2026-11-07. Base: spec general `2026-10-14-formulario-web-design.md` (D1, D7, D13, §6.2, §7.1,
§7.2) e inventario del admin PHP heredado (`FORMULARIO_WEB/admin`, revisado el 2026-11-07). Este
documento reemplaza lo que la spec general decía de la Fase 4 donde haya diferencia; las diferencias se
listan en §10.

## 1. Para qué

Durante el operativo, las personas que hacen seguimiento revisan **cada semana** el avance de la
encuesta contra la muestra. La herramienta es de **control**: cuando una entidad o un departamento va
atrasado, la acción ocurre por fuera (se llama a la territorial). Por eso los reportes deben mostrar
rápido y con cifras correctas dónde está el atraso. Las territoriales, además, registran las
**novedades** de los funcionarios de su departamento (retiros y otras causas), y el ingeniero encargado
de la encuesta genera las **claves** de la muestra y las entrega a los coordinadores territoriales,
quienes notifican a cada funcionario por carta/correo (fuera de la herramienta).

Hoy todo esto vive en el admin PHP con inyección SQL, sin autorización en el servidor, sin auditoría y
con errores de conteo. La Fase 4 lo migra a la app nueva (Spring Boot + React) y retira esa parte del
PHP.

## 2. Objetivos, no objetivos y antiobjetivos

**Objetivos**
- Los siete reportes del PHP, todos usados semanalmente: cobertura general, cobertura EDI, cobertura
  EDID, generar reporte (corte) por departamento, histórico de cortes, estado de mi operativo,
  novedades y rendimientos. Cada uno en pantalla y exportable a `.xlsx`.
- Gestión de novedades por funcionario: registrar, modificar y eliminar.
- Generación de claves de la muestra y descarga por departamento.
- Autorización por rol validada en el servidor en cada endpoint.

**No objetivos**
- Enviar correos o cartas de notificación (siguen fuera de la herramienta).
- Cambiar el almacenamiento de contraseñas (D7 sigue: texto plano en `EDIN_MUESTRA.PSW`; plan de salida
  sin cambios).
- Gestionar usuarios administrativos (`GRAL_USUARIOS_ADM`) o la muestra (carga de `EDIN_MUESTRA`).
- Usar `GRAL_PERIODO_OPERATIVO` ni filtros por rango de fechas (el PHP nunca los usó).
- Acciones de seguimiento dentro de la herramienta (llamadas, tareas, alertas).

**Antiobjetivos** (fracaso aunque "funcione")
- Que un territorial vea o modifique datos de otro departamento, o que un operativo cambie algo.
- Que nombres, cédulas o claves terminen en logs, nombres de archivo, URLs o archivos guardados en el
  servidor.
- Cifras que no se puedan explicar frente al PHP: cada diferencia debe estar documentada (§5.4).
- Que el modo de solo lectura del cuestionario en producción bloquee novedades o claves (§8.3).

## 3. Roles (decidido con el usuario)

Los roles ya se derivan de `GRAL_USUARIOS_ADM.ROL` (`AuthService.rolesForLegacyAdminRole`); no se crean
ni migran usuarios.

| ROL PHP | Rol nuevo | Reportes | Novedades | Claves |
|---|---|---|---|---|
| 1 (ingeniero encargado) | `ADMIN_NACIONAL` (+ `ADMIN_CUESTIONARIO`) | Todos, todos los departamentos | No (solo el reporte) | Generar y descargar |
| 2 (territorial) | `ADMIN_TERRITORIAL` | Todos, solo su `COD_DPTO` | Registrar, modificar, eliminar en su departamento | No |
| 3 y 4 (operativo central) | `ADMIN_OPERATIVO` | Todos, todos los departamentos | No (solo el reporte) | No |

- Todo se valida en el servidor; un departamento ajeno o una acción no permitida responde 403.
- **Departamentos del territorial** (verificado en el respaldo con conteos agregados): un usuario puede
  tener varios departamentos en `GRAL_TERRITORIAL_ACCESO` (hasta 14), con `PROY` como lista
  (`'EDIN, EDID'`, `'EDIN'`, `'EDID'`, `'ECDE'`…). Decisión mía, igual que el PHP: los departamentos
  permitidos para una encuesta son las filas de `GRAL_TERRITORIAL_ACCESO` del usuario cuyo `PROY` incluye
  esa encuesta; si no tiene filas, `GRAL_USUARIOS_ADM.COD_DPTO`. Se calculan en el servidor al iniciar
  sesión (el principal pasa de un departamento a una lista por encuesta), nunca desde un parámetro; la
  interfaz muestra un selector con solo esos departamentos.
- `ADMIN_NACIONAL` no registra, modifica ni elimina novedades (decidido con el usuario): solo la
  territorial lo hace.

## 4. Datos (estructura real, respaldo del 2026-11-05)

- `EDIN_MUESTRA`: PK `C_C`, **única entre años**: la tabla guarda todos los años (2008–2026, sin 2021,
  con 20–41 mil filas por año), así que unir los capítulos por `C_C` no mezcla años y el selector de año
  sirve para consultar operativos anteriores. Columnas usadas: `ANIO VARCHAR2(4)`, `ENC` ('1' EDI, '2' EDID),
  `COD_ENT`, `COD_DEPTO NUMBER(2)`, `NOM_APE`, `AREA_TRA` (nivel 1–3), `ATV` (1 = activo), `TC1`
  (1 = web, 2 = DMC), `TC2`, `LGN VARCHAR2(8)`, `PSW VARCHAR2(8)`, `IDFNOV`,
  `NOV_OTRA_CAUSA VARCHAR2(500)`, `IDMUES`.
- `GRAL_NOVEDAD` (catálogo: `PROY`, `IDFNOV` PK, `NOVEDAD`, `ANIO`), `GRAL_ENTI` (`COD_ENT` PK,
  `ENTIDAD`, `PROY`), `GRAL_DEPARTAMENTOS` (`COD_DEPTO` PK, `NOM_DEPTO`, `TERRIT`), `GRAL_DIVIPOLA`.
- Capítulos `EDIN_20_CAP_B_UBICA` … `EDIN_20_CAP_H_PREV_PRACT` (o los del año, vía el mapeo de tablas
  de respuesta de la Fase 3), con `FECHA` por capítulo.
- **No existen en el respaldo** `GRAL_CLAVES` ni `GRAL_BCK_REP_DIARIO` (el PHP las usa). La Fase 4 no
  las necesita (§6, §5.3).
- `SurveyProperties` mapea EDID → `EDID_MUESTRA`, tabla que tampoco está en el respaldo; los reportes
  del PHP filtran EDI/EDID con `EDIN_MUESTRA.ENC`. El builder sigue el criterio del PHP (`ENC`) y
  corrige o documenta el mapeo de `SurveyProperties` si no corresponde.
- Año: el operativo vigente de la configuración (`SurveyProperties`), con selector de año en los
  reportes para consultar años anteriores. Nada de años fijos en el código.

## 5. Reportes

### 5.1 Criterio de estado (único para todos los reportes)

Por funcionario de la muestra del año (`ATV = 1`):
1. **Completo:** tiene B07 y H06 no nulos (columnas fijas, como el PHP; decisión mía: si el
   cuestionario de un año no las tiene, el reporte de ese año responde un error claro en vez de cifras). **Prevalece sobre la novedad** (decidido con el usuario: si
   diligenció de B07 a H06 cuenta como completa aunque tenga novedad).
2. **Novedad (SD):** tiene `IDFNOV` y no está completo. En cobertura se resta de la muestra efectiva y
   se muestra en la columna de novedades; en los listados aparece como "Novedad (SD)".
3. **Incompleto:** B07 no nulo, H06 nulo, sin novedad.
4. **Sin diligenciar:** el resto.

Muestra efectiva = muestra − novedades (no completas). Porcentajes sobre la muestra efectiva, con dos
decimales; si la muestra efectiva es 0, los porcentajes son 0.

### 5.2 Los reportes

Todos: filtro de encuesta (general / EDI / EDID donde aplique) y año; departamento (fijo para el
territorial); en pantalla con orden y paginación; "Exportar a Excel" (`.xlsx` real, generado al vuelo,
nunca guardado en disco). Nombre de archivo con reporte, encuesta, departamento (código) y fecha, sin
datos personales.

1. **Cobertura general / EDI / EDID**: por departamento y entidad (decisión mía: **una fila por
   entidad** con web y DMC sumados, como `3.2`; detalle por nivel solo web), muestra, novedades, muestra efectiva,
   completas, incompletas, sin diligenciar (total y %), detalle por nivel 1–3 para web y consolidado
   nacional (número de departamentos, número de entidades reales, totales y niveles).
2. **Generar reporte (corte) por departamento**: el mismo cálculo de cobertura de un departamento,
   guardado como corte con fecha y hora (§5.3) y descargable.
3. **Histórico**: lista de cortes guardados (filtrable por departamento y año) con descarga `.xlsx`.
4. **Estado de mi operativo**: listado nominal por departamento y entidad con filtro de estado (todos,
   completos, incompletos, sin diligenciar, novedad), con código, nombre, nivel, estado, fecha de
   diligenciamiento, novedad/otra causa y capítulos faltantes.
5. **Novedades**: listado de funcionarios con novedad (entidad, funcionario, nivel, código y texto de la
   novedad, otra causa) y si además está completo.
6. **Rendimientos**: por funcionario que inició (B07 no nulo), fecha y hora de guardado de cada capítulo
   B–H.

Los reportes 4, 5 y 6 contienen datos personales (Ley 1581): solo para roles autorizados, sin caché
compartida, con `Cache-Control: no-store`.

### 5.3 Cortes (histórico) — decisión mía

El PHP guardaba archivos `.xls` en una carpeta pública y su ruta en `GRAL_BCK_REP_DIARIO` (tabla que no
está en el respaldo). La app nueva guarda cada corte como **JSON con solo cifras agregadas** (sin
nombres ni cédulas) en un directorio persistente configurable (`REPORTS_DIR`, volumen como el de reglas),
con usuario que lo generó, fecha, hora, encuesta, año y departamento. El `.xlsx` se arma al descargar.
Generar de nuevo el mismo día agrega otro corte (no sobrescribe). No se escribe en la base.

### 5.4 Correcciones frente al PHP (decidido: corregir y documentar)

Se entrega `docs/fase-4-diferencias-reportes.md` con cada diferencia, su causa en el PHP y el efecto en
las cifras:
1. Entidades con funcionarios web y DMC se contaban dos veces en `2.*` (protección por entidad
   comentada): ahora una vez.
2. `3.1` sumaba las incompletas por nivel del recordset equivocado: ahora correcto.
3. EDI/EDID filtraban `ENC` solo en la consulta externa: ahora cada encuesta cuenta solo lo suyo.
4. El Excel de estado operativo ignoraba el departamento en "sin diligenciar": ahora lo respeta.
5. Completo prevalece sobre novedad (en el PHP la novedad lo sacaba de la muestra efectiva y aun así se
   contaba como completa).
6. El listado "Todas" usaba otro criterio (7 capítulos presentes): ahora el de §5.1.
7. Consultas emparejadas por posición (MN/EC/EI) que se podían desalinear: ahora una sola consulta
   agregada por entidad.
8. Columnas `HORA_H`/`FECHA_H` invertidas en rendimientos.
9. "Número de entidades" contaba filas (entidad, tipo de carga): ahora entidades.

Pruebas: datos ficticios que reproducen cada caso y verifican la cifra corregida.

## 6. Novedades (decidido con el usuario)

- **Quién:** `ADMIN_TERRITORIAL`, solo funcionarios de su departamento.
- **Flujo:** elegir departamento (entre los suyos) y entidad → lista de funcionarios del año con su
  estado y novedad actual → asignar novedad del catálogo `GRAL_NOVEDAD` (encuesta y año), modificarla o
  eliminarla. Eliminar es **manual**: la territorial quita una novedad transitoria cuando el funcionario
  diligencia la encuesta; la app nunca cambia `IDFNOV` desde el flujo del encuestado.
- La causa "otra causa" exige texto (máx. 500 bytes Latin-9, mismo tratamiento `Latin9Text` de la
  Fase 3); el código de "otra causa" se identifica en el catálogo real (el PHP mezcla 8 y 17) y se
  configura, no se escribe en el código.
- **Identificación:** por `C_C` (PK) y año, nunca por `TC2`. Escritura: `UPDATE EDIN_MUESTRA SET
  IDFNOV, NOV_OTRA_CAUSA WHERE C_C = ? AND ANIO = ? AND COD_DEPTO IN (departamentos del usuario)`
  parametrizado, verificando que
  afectó exactamente una fila. Eliminar limpia ambas columnas.
- La novedad **no bloquea** el ingreso del funcionario a la encuesta.
- Validación en el servidor: código existente en el catálogo, funcionario del departamento del usuario,
  texto obligatorio para "otra causa". CSRF como el resto de la app.
- **Auditoría:** línea estructurada (`AdminAudit`) con usuario, operación, año, `IDMUES` (o código interno
  no personal), código anterior y nuevo; sin cédula, nombre ni texto libre.
- Concurrencia: si la novedad cambió desde que se cargó la pantalla, 409 y recarga.

## 7. Claves (decidido con el usuario)

- **Quién:** `ADMIN_NACIONAL`.
- **Generar:** para la encuesta y el año elegidos, asigna `LGN` y `PSW` directamente en `EDIN_MUESTRA` a
  los funcionarios que **no tienen** clave (`LGN` nulo). Nunca sobrescribe; si luego se agrega un
  funcionario a la muestra, una nueva generación solo crea la suya. Decisión mía: solo a quienes pueden
  ingresar por web (`TC1 = 1`, `ATV = 1`), igual que el login.
- **Formato:** 8 letras A–Z mayúsculas (compatibilidad con el login heredado y las cartas), con
  `SecureRandom`; `LGN` único por año.
- **Descargar:** `.xlsx` por departamento con entidad, código del funcionario, nombre, usuario y clave,
  para enviarlo al coordinador. Generado al vuelo, nunca guardado. Decisión mía (cambia §7.2 de la spec
  general, ver §10): se puede volver a descargar después, porque las claves ya están en la base.
- **Auditoría:** quién generó (cuántas, año, encuesta) y quién descargó qué departamento; nunca las claves.
- Confirmación antes de generar con el número de funcionarios que recibirán clave.

## 8. Restricciones

### 8.1 Técnicas
- Lineamientos del proyecto (CLAUDE.md): capas, pruebas (80 % en módulos con datos personales:
  novedades, claves, listados nominales), OpenAPI + `npm run api:types`, WCAG 2.1 AA con axe, Latin-9,
  consultas parametrizadas, sin datos reales en pruebas ni en el repo.
- Librería de Excel: nueva dependencia verificada en Maven Central con versión fijada (el builder elige;
  Apache POI o una alternativa en streaming).
- Rendimiento: un reporte nacional no puede hacer tres consultas correlacionadas por departamento como
  el PHP; consultas agregadas en lote. Objetivo: cobertura nacional en menos de 5 s con la muestra real.

### 8.2 Base de datos
- Sin DDL. Escribe solo en `EDIN_MUESTRA` (`IDFNOV`, `NOV_OTRA_CAUSA`, `LGN`, `PSW`). El builder agrega
  al script de verificación (`db/verificacion/`) la comprobación de privilegios de UPDATE sobre
  `EDIN_MUESTRA` y de la estructura de `GRAL_NOVEDAD`/`GRAL_ENTI`/`GRAL_DEPARTAMENTOS`.
- `db/dev/schema.sql` y `data.sql`: completar `EDIN_MUESTRA` y los catálogos con la estructura real y
  datos ficticios.

### 8.3 Modo de solo lectura
`QuestionnaireWriteGuard` bloquea escrituras en `/api/v1/admin/**` cuando
`QUESTIONNAIRE_WRITES_ENABLED=false` (producción). Novedades y claves **deben funcionar en producción**:
quedan fuera del guardia (por ruta o con `@AllowedInReadOnlyQuestionnaire`), con prueba que lo verifique.

## 9. Interfaz

- Menú lateral de administración: Reportes (submenú con los siete), Novedades, Claves, visibles según rol;
  el cuestionario sigue como está.
- Tablas accesibles (encabezados, `caption`), filtros con etiquetas, estados de carga, vacío y error,
  móvil primero. Cifras alineadas a la derecha con separador de miles colombiano.
- Novedades: formulario por funcionario con confirmación al eliminar.
- Claves: resumen (cuántos sin clave por departamento), botón Generar con confirmación y descarga por
  departamento.

## 10. Diferencias con la spec general

- "Mismas cifras que el PHP" (§1): reemplazado por "cifras corregidas y documentadas" (§5.4).
- Rol `ADMIN_OPERATIVO`: ve todos los reportes de todos los departamentos (la spec general §7.1 solo
  nombraba a `ADMIN_NACIONAL` y `ADMIN_TERRITORIAL`).
- Claves: se asignan en `EDIN_MUESTRA` (no un pool en `GRAL_CLAVES`) y pueden volver a descargarse
  (la spec general decía "se muestran una sola vez").
- Rutas del API: las de §7.1 son orientativas; el builder las ajusta manteniendo `/api/v1/admin/...`.

## 11. Lo que queda al builder

- Diseño de endpoints, DTO, consultas SQL y componentes, dentro de las reglas anteriores.
- Librería de Excel y formato del libro (una hoja por reporte, encabezados congelados).
- Orden de las tareas y tamaño de cada entrega; retiro del admin PHP queda como paso de despliegue
  documentado (no se borra código PHP en este repo).
