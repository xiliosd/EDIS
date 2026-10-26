# Diseño: Fase 3 — Administración del cuestionario

- **Fecha:** 2026-10-26
- **Estado:** borrador para revisión
- **Responsable:** Oscar Dueñas (oscar.duenas@uniminuto.edu)
- **Amplía:** `docs/superpowers/specs/2026-10-14-formulario-web-design.md` (§4.3, §5.3, §6.2, §7.1, §12 punto 3).
  Donde este documento y el spec general difieren, manda este documento para la Fase 3.

## 1. Intención

Cada año, cuando cierra la recolección, una persona técnica prepara el cuestionario del año siguiente:
- ajusta textos;
- agrega o quita preguntas;
- cambia saltos y valores mínimos y máximos.

Hoy lo hace a mano en las tablas `FRM_*` y en `FrmValida.js`. El cambio tarda **cerca de 20 días**, la mayor parte en pruebas. Los errores que más aparecen son:
- saltos mal hechos;
- valores (rangos) equivocados;
- preguntas que debían ocultarse por un salto y se muestran, o al revés.

Un ejemplo: si E03 lleva a E05, la E04 no debe mostrarse.

La Fase 3 entrega un **editor sólido**: la edición no deja guardar esos errores, de modo que las pruebas se acortan solas. El simulador y los casos de prueba son el respaldo para confirmar, no la red principal.

**Usuario:** la misma persona técnica de hoy. Conoce las tablas y los numerales y tiene permisos sobre la base de datos de QA y de producción. No es un editor para usuarios no técnicos.

## 2. Objetivos, no objetivos y antiobjetivos

**Objetivos**
1. Preparar el cuestionario de un año nuevo partiendo del año anterior, sin escribir SQL a mano ni tocar código.
2. Editar textos, encabezados y ayudas; agregar, desactivar y reordenar preguntas.
3. Editar saltos y validaciones (mínimo, máximo, obligatoria, tipo, longitud) **por código de pregunta**, nunca por posición.
4. Rechazar al guardar cualquier regla o valor inconsistente, y avisar los problemas de lógica detectables (§5).
5. Simular el formulario y guardar casos de prueba que se vuelven a ejecutar en cada cambio.
6. Publicar el cuestionario con un paso explícito que lo bloquea.
7. Pasar de QA a producción con un paquete verificable.
8. Corregir en plena recolección los errores de textos, saltos y rangos sin afectar lo ya recolectado.

**No objetivos (esta fase)**
- Crear o renombrar opciones de respuesta (`FRM_RESPUESTA`, `FRM_RTA_GRUPO`): una pregunta nueva elige un grupo de respuestas existente. *(Decisión del constructor; revisable.)*
- Crear capítulos o páginas nuevas. Sí se puede elegir otra tabla de respuestas por capítulo y año (§4.1.1).
- Ejecutar DDL desde la aplicación (se genera el script; lo ejecuta la persona técnica).
- Reportes, novedades y claves (Fase 4).
- Varias instancias del backend (D6 sigue vigente).
- Reabrir o recalcular páginas ya guardadas por los encuestados.

**Antiobjetivos** (sería un fracaso aunque funcione):
- Que editar el año nuevo cambie textos, orden o reglas de un año anterior.
- Que un cuestionario publicado cambie sin dejar rastro (quién, cuándo, por qué, qué cambió).
- Que la aplicación necesite privilegios DDL, o escritura sobre `FRM_*` en producción.
- Que se pueda publicar un cuestionario con un salto o rango roto.

## 3. Ciclo de vida de un año

```
Preparar (copia) → Borrador (editar + simular) → Publicado en QA → Paquete → Importado en producción
                                                     ↑                                  │
                                                     └── Corrección (nueva versión) ←───┘
```

1. **Preparar.** "Crear año AAAA a partir de AAAA−1" copia, dentro de una transacción en QA:
   - las filas del año en `FRM_CAP_PROY`, `FRM_CAP_PERIODO`, `FRM_OBJ_PRE` y `FRM_ENCABEZADO`, y las ayudas;
   - el archivo de reglas, como `<ENC>-<AAAA>.json` con `estado: "borrador"` y versión 1.

   Los saltos genéricos heredados de `FRM_SALTO` se **materializan** en el archivo como reglas `legacy-<COL>`, y no se crean filas `FRM_SALTO` para el año nuevo. Desde la Fase 3, todos los saltos viven en el archivo de reglas. *(Decisión del constructor: elimina la doble fuente de saltos. El traductor heredado sigue sirviendo a los años anteriores.)*

   La copia falla sin cambios si el año destino ya existe.
2. **Borrador.** Solo un año en borrador por encuesta. Se puede editar todo lo de §4. El simulador y los casos de prueba están disponibles.
3. **Publicar** (en QA). Exige:
   - cero errores de validación (§5);
   - todos los casos de prueba en verde;
   - todas las preguntas activas con su columna creada.

   Si falta alguna columna, la publicación lo indica y ofrece el script `ALTER TABLE` (D3). Publicar fija `estado: "publicado"`, la fecha y el usuario, y bloquea la edición estructural.
4. **Paquete de promoción** (§7). Se genera en QA desde un año publicado, y la persona técnica lo aplica en producción.
5. **Corrección en caliente** (§6). Se hace en QA sobre el año publicado, sube la versión y viaja a producción con un paquete de corrección.

**Año activo de la encuesta.** Sigue siendo `SURVEY_YEAR`, un cambio de configuración en el despliegue. *(Decisión del constructor.)* Publicar no cambia el año que ven los encuestados.

Si el año activo tiene archivo de reglas con `estado: "borrador"`, el API del encuestado responde 503 con "La encuesta aún no está disponible". Un archivo sin campo `estado` se trata como publicado, por compatibilidad con 2026.

## 4. Edición

### 4.1 Preguntas (tablas `FRM_*`, solo en QA)

- **Árbol** capítulo → página → grupo → pregunta, con numeral, código de columna, tipo (combo, casilla, texto), estado de columna (activa o pendiente) y reglas que la usan.
- **Editar texto.** Texto plano; las entidades HTML se decodifican al cargar (§4.3 del spec general).
- **Copia al escribir.** Si el `COD_PRE` o el `COD_PRE_GRUPO` que se edita lo usa otro año o la otra encuesta, se crea primero una copia para la encuesta y el año en edición, y se repunta su `FRM_CAP_PROY`. **Ningún cambio modifica filas que use otra combinación de encuesta y año.** Aplica a textos, orden y encabezados.
  - En los datos reales, `FRM_CAP_PROY` y `FRM_CAP_PERIODO` traen filas separadas por `PROY` (EDIN o EDID) y por año. La copia de año y la desactivación operan solo sobre la encuesta seleccionada. *(Verificado en los metadatos de 2026; la comprobación de copia al escribir cubre igual cualquier fila compartida.)*
- **Agregar pregunta.** Se dan capítulo y página, posición, numeral, código de columna, tipo de objeto y grupo de respuestas existente (para combos). Validaciones:
  - el código cumple `^[A-Z][0-9A-Z_]{1,29}$` y no está repetido en la página;
  - el numeral no está repetido;
  - si la columna no existe en la tabla del capítulo, la pregunta queda **pendiente de columna** y se ofrece el script con el tipo y el tamaño elegidos.
- **Desactivar pregunta.** Retira su fila de `FRM_CAP_PERIODO` para el año. Se rechaza, con la lista de reglas afectadas, si alguna regla o caso de prueba la usa (§4.3 del spec general).
- **Reordenar** dentro de la página, por arrastre y por teclado. Al guardar se avisa qué saltos tienen una pregunta destino que queda *antes* de su disparador (§5).
- **Encabezados y ayudas:** editar texto.

### 4.1.1 Tablas de respuestas por año

La persona técnica define qué tabla de respuestas usa cada capítulo en cada año (confirmado por el responsable). Por ejemplo, para 2027 podría crear `EDIN_21_CAP_B_UBICA` en lugar de `EDIN_20_CAP_B_UBICA`. Hoy ese mapeo está fijo en `Chapter.tableName()` y pasa a ser **configuración por encuesta y año**:

- **Dónde se guarda:** en el archivo de reglas, sección `tablas` (capítulo → nombre de tabla), sin tablas nuevas (D4). Un archivo sin esa sección usa los nombres actuales, por compatibilidad con 2026.
- **Al crear el año:** se copia el mapeo del año anterior y se puede cambiar cualquier capítulo. *(Decisión del constructor:)* el editor ofrece, para cada tabla nueva, el script `CREATE TABLE … AS SELECT * FROM <tabla del año anterior> WHERE 1 = 0`, más sus restricciones. Lo ejecuta la persona técnica, igual que los `ALTER TABLE` (D3).
- **Seguridad SQL** (sigue §3.2 del spec general): un nombre de tabla solo se acepta si:
  - cumple `^[A-Z][A-Z0-9_]{1,29}$`;
  - existe en `ALL_TABLES` del esquema configurado;
  - tiene las columnas fijas `C_C`, `ESTADO` y `TC`.

  Los nombres salen solo de ese mapeo validado y nunca de la petición. La lista blanca de columnas (`ALL_TAB_COLUMNS`) se calcula por tabla.
- **Publicar** exige que todas las tablas del mapeo existan y cumplan lo anterior.
- **El encuestado** (Fase 2) lee el mapeo del año activo en lugar del valor fijo. Esto toca `AnswerRepository`, `AnswerColumnRepository` y `SurveyProgressService`.
- **Correcciones en caliente:** no cambian el mapeo de un año publicado.

### 4.2 Reglas y validaciones (archivo de reglas)

- **Constructor de saltos:**

  `Si [pregunta] [operador] [valor(es)] → no mostrar [preguntas]`

  con `todas`, `alguna`, `y` y `o`. Las preguntas se eligen de una lista del año (código más texto). Los valores de un combo se eligen de sus opciones reales; no se escriben a mano.
  - Cada regla muestra su lectura en lenguaje claro: "Si E03 = Sí → no se muestra E04".
  - También muestra el **efecto en la página**: qué preguntas quedan ocultas y por qué regla.
- **Validaciones por pregunta de texto:** obligatoria, tipo (entero, decimal o texto), mínimo, máximo y longitud máxima. Los combos se validan por sus opciones, sin rango.
- **Vista JSON** de solo lectura; exportar e importar se mantienen.
- **Guardado:** como en §5.3 del spec general (versión con `If-Match`, historial, escritura atómica y auditoría), más las validaciones de §5.

## 5. Validación al editar (el núcleo)

Se ejecuta en el backend en cada guardado. El frontend repite las comprobaciones baratas para avisar en vivo.

**Errores** (bloquean guardar o publicar):
1. Una regla o validación referencia una pregunta que no existe o no está activa en el año.
2. Un salto oculta a su propio disparador, directa o indirectamente (ciclo).
3. Un valor de condición no existe entre las opciones del combo disparador. Ejemplo: `C07A = 3` cuando C07A solo tiene 1 y 2.
4. Un rango con mínimo mayor que máximo, o fuera de lo que admite la columna real (precisión y escala de `NUMBER`, longitud de `VARCHAR2` en bytes o caracteres), como ya valida la Fase 2 al guardar respuestas.
5. Una validación de rango sobre un combo o una casilla.
6. Ids de regla repetidos, o una regla sin preguntas destino.
7. Una pregunta nueva con código o numeral repetido, o un código inválido.

**Avisos** (no bloquean; se muestran con explicación y quedan registrados al publicar):
1. **Pregunta que nunca se muestra:** para cada respuesta posible de sus disparadores, alguna regla la oculta.
   - Se calcula enumerando las opciones de los combos disparadores cuando las combinaciones son ≤ 4096. Si son más, se informa que no se pudo analizar.
   - Ejemplo real: F04I en 2026 queda oculta con casi cualquier combinación, como ya está documentado en `docs/reglas/edin-2026.md`.
2. **Regla que nunca se cumple:** ninguna combinación de opciones la activa. Mismo cálculo acotado.
3. **Destino antes del disparador:** el salto oculta una pregunta que aparece antes en el orden de la página.
4. **Reglas solapadas:** dos reglas con el mismo destino cuyas condiciones pueden cumplirse a la vez con resultados redundantes. Es solo informativo.
5. **Pregunta pendiente de columna.**

## 6. Simulador y casos de prueba

- **Simulador.** Muestra el formulario del año en edición página por página, con los mismos componentes del encuestado, sobre respuestas en memoria. Nunca escribe en las tablas de respuestas.
  - Muestra junto a cada pregunta oculta qué regla la ocultó.
  - Permite saltar a cualquier página.
- **Casos de prueba.** Desde el simulador se guarda un caso: nombre, respuestas y resultado esperado (preguntas ocultas y errores).
  - Se guardan dentro del archivo de reglas, en una sección `casos`, sin tablas nuevas (D4).
  - Se ejecutan con el motor autoritativo del backend en cada guardado y antes de publicar. Un caso en rojo bloquea la publicación.
  - *(Decisión del constructor: ataca directamente los 20 días de pruebas manuales, porque cada año se reutilizan los casos del anterior.)*
  - Al crear el año nuevo, los casos se copian y se marcan "por revisar" si referencian preguntas que cambiaron.

## 7. Correcciones en plena recolección

Sobre un año publicado:
- **Se permite:** corregir textos, encabezados y ayudas, saltos y validaciones.
- **No se permite:** agregar, desactivar o reordenar preguntas, ni cambiar columnas, porque rompería lo ya guardado.

El **impacto** se muestra en producción, al importar el paquete de corrección y antes de confirmarlo, porque las respuestas reales están allí y QA solo tiene datos de prueba. Son conteos sin datos personales de cuántos encuestados ya guardaron cada página afectada. Por ejemplo: "37 encuestados ya guardaron E1 con la regla anterior". *(Decisión del constructor: así QA no necesita acceso a producción.)*

La corrección aplica **solo hacia adelante**: las páginas ya guardadas no cambian (escritura única, D16), y no se reabren ni se marcan páginas.

Cada corrección:
- sube la versión;
- exige un motivo;
- queda en el historial y en la auditoría;
- viaja a producción con un paquete de corrección (§8).

## 8. Paso de QA a producción

- **Permisos** (mínimo privilegio): en QA, el usuario de la aplicación necesita DML sobre `FRM_*`. En producción solo lee `FRM_*`. Los cambios a `FRM_*` en producción los ejecuta la persona técnica con sus permisos.
- **Paquete de promoción** (ZIP descargable desde QA para un año publicado, o para una corrección):
  1. `reglas.json`: el archivo de reglas, con sus casos.
  2. `frm.sql`: un script idempotente con los `INSERT`/`UPDATE` de `FRM_*` del año, o solo el diferencial de la corrección.
     - Las tablas `FRM_*` de QA son copia fiel de las de producción, con los mismos ids (confirmado por el responsable). Por eso el script lleva los ids de QA tal cual.
     - Si un id está ocupado, se detiene sin cambios y lo informa: no renumera en silencio.
  3. `columnas.sql`: el script `ALTER TABLE` de las columnas nuevas, si hay.
  4. `manifiesto.json`: encuesta, año, versión, conteos esperados por tabla, lista de columnas requeridas y sumas de verificación de cada archivo.
- **En producción:** la persona técnica ejecuta `columnas.sql` y `frm.sql`, y luego importa el paquete en el administrador.
  - La importación verifica contra la base real que existan las filas y columnas que pide el manifiesto. Si algo falta, la rechaza y lista las diferencias.
  - Al importar, se limpian las cachés de preguntas y reglas (`evict`), sin reiniciar.

## 9. Interfaz (amplía §6.2 del spec general)

**Menú "Cuestionario"** (rol `ADMIN_CUESTIONARIO`), con selector de encuesta y año, y estado visible (borrador, publicado o versión N):
- **Preguntas:** árbol, edición y pendientes de columna.
- **Reglas:** constructor, efecto por página, avisos.
- **Simulador y casos de prueba.**
- **Publicar:** lista de comprobación de §3 con el resultado de cada punto.
- **Historial:** versiones, diferencias, motivo y restaurar (solo en borrador).
- **Paquetes:** generar o importar.

**Requisitos de la interfaz**
- WCAG 2.1 AA, igual que el resto: teclado completo, también para reordenar sin arrastre, y foco visible.
- Errores y avisos de §5 junto al elemento que los causa y en un resumen enlazado.
- Escritorio primero: la persona técnica trabaja en pantalla grande. Debe funcionar en tableta, no se exige móvil.

## 10. Restricciones

- Sin tablas nuevas en Oracle (D4); reglas, casos, estado e historial en `RULES_DIR` (D5, D6).
- Sin DDL desde la aplicación (D3); sin dependencias nuevas sin verificar en el registro oficial con versión fija.
- Lineamientos UNIMINUTO:
  - cobertura de al menos 80 % en el paquete de reglas y validación, y en la autorización del administrador;
  - al menos 60 % en el resto;
  - OpenAPI actualizado;
  - README con las once secciones.
- Auditoría en logs estructurados sin datos personales. Los conteos de impacto nunca muestran `C_C`.
- Concurrencia: un editor a la vez por año. La versión con `If-Match` da 409 y el diálogo de §7.3 del spec general.

## 11. Prerrequisitos

1. **DDL completo de `FRM_*` en QA y producción:** claves primarias, secuencias o disparadores de ids, `NOT NULL` y claves foráneas.
   - Se sabe que existen las claves primarias `PK_FRMCAPR` (`COD_CAP_PROY`), `PK_FRMCAPE` (`ID_PERIODO`) y `PK_FRMENCABEZADO` (`COD_ENC`), y las foráneas `FRM_CAP_PROY → FRM_PRE_GRUPO` y `FRM_ENCABEZADO → FRM_CAP_PROY`.
   - Falta saber cómo se generan los ids: secuencia, disparador o máximo más uno. Sin eso no se implementa la copia de año ni el paquete. Es lo primero del plan: leer el DDL de QA (la estructura, sin datos).
   - Si no hay secuencias, la aplicación asigna `MAX(id)+1` dentro de la misma transacción con bloqueo, y el script de producción lleva esos mismos ids. QA y producción son iguales.
2. **Permisos DML sobre `FRM_*`** para el usuario de la aplicación en QA (no en producción).
3. **Casos de prueba de 2026**, para sembrar los primeros casos: los de `docs/reglas/edin-2026.md` y la matriz de `DOCUMENTOS/MATRIZ DE PRUEBAS`.

## 12. Queda a criterio del constructor

- Representación interna del diferencial de una corrección y formato exacto de `frm.sql`, siempre que sea idempotente y verificable por manifiesto.
- Algoritmo del análisis acotado de §5, avisos 1, 2 y 4, dentro del límite de 4096 combinaciones.
- Diseño visual del árbol y del constructor, dentro de la identidad DANE y WCAG.
- Partición en planes: se sugiere (a) preguntas, copia de año y validación; (b) reglas, simulador y casos; (c) publicación, correcciones y paquetes.

## 13. Criterio de éxito

- El próximo cambio anual se hace completo con el editor, sin SQL a mano salvo los scripts generados, y sin tocar `FrmValida.js`.
- Ningún error de los tipos de §1 llega a producción: o el editor lo bloquea, o un caso de prueba lo detecta.
- El tiempo del cambio anual baja claramente desde los ~20 días actuales. **La meta concreta la fija el responsable** (pendiente).
