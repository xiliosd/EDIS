# Fase 4 — Reportes, novedades y claves: plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrar al sistema nuevo los siete reportes del operativo con exportación `.xlsx`, la gestión de
novedades por la territorial y la generación y descarga de claves de la muestra, con autorización por rol
y por departamento validada en el servidor.

**Architecture:** Paquete backend nuevo `co.gov.dane.edi.operation` (repositorios JDBC parametrizados,
servicios, controladores bajo `/api/v1/admin/{reports|novelties|keys}`), un servicio de alcance
(`OperationalAccessService`) que resuelve rol + departamentos en cada petición, un escritor `.xlsx` común
y un almacén de cortes en archivos JSON. Frontend: carpetas `features/admin-reports`,
`features/admin-novelties`, `features/admin-keys`, con el mismo patrón de `features/admin-history`
(`*-api.ts` + páginas + React Query).

**Tech Stack:** Spring Boot 4.1, Oracle (JDBC `NamedParameterJdbcTemplate`), Apache POI (`poi-ooxml`,
streaming `SXSSFWorkbook`), React 19 + TypeScript + React Router 7 + TanStack Query + Tailwind, Vitest,
Playwright + axe, Testcontainers (Oracle Free).

**Spec:** `docs/superpowers/specs/2026-11-07-fase-4-reportes-novedades-claves-design.md` (léalo completo
antes de empezar; este plan no lo repite).

## Global Constraints

- Lineamientos (CLAUDE.md del usuario): capas controlador/servicio/repositorio; nombres descriptivos;
  Conventional Commits en español; cada commit termina con línea en blanco y
  `Co-Authored-By: <modelo real> <noreply@anthropic.com>`; commits en `main`, **sin push**; nunca
  saltarse gitleaks; nunca commitear `docs/plan-trabajo-gantt-feb-may-2027.xlsx`.
- Datos: **solo ficticios** en pruebas, `db/dev` y documentación. Nunca abrir ni copiar el respaldo de
  `Downloads`. Nada de nombres, cédulas, claves ni texto libre de novedades en logs, URLs (path o query),
  nombres de archivo ni archivos guardados en el servidor.
- SQL siempre parametrizado; los nombres de tabla solo desde `SurveyProperties.sampleTableName()` y
  `AnswerTableResolver.tableFor(...)`.
- Sin DDL en la base real. Escrituras permitidas: `EDIN_MUESTRA.IDFNOV`, `NOV_OTRA_CAUSA`, `LGN`, `PSW`.
- Textos libres hacia Oracle: `Latin9Text.encode` y límite con `Latin9Text.fits(texto, 500)` (bytes
  Latin-9).
- Cada escritura nueva bajo `/api/v1/admin/**` lleva `@AllowedInReadOnlyQuestionnaire` (deben funcionar
  en producción, donde `QUESTIONNAIRE_WRITES_ENABLED=false`).
- Respuestas con datos personales (listado nominal, novedades, rendimientos, gestión de novedades,
  claves): cabecera `Cache-Control: no-store`.
- Dependencia nueva: verificar `org.apache.poi:poi-ooxml` en Maven Central y fijar la última versión
  estable (sin rangos); revisar que el análisis de dependencias del build no reporte vulnerabilidades
  críticas.
- OpenAPI: después de cada tarea de backend que cambie el contrato, `OpenApiExportIT` regenera
  `docs/api/openapi.yaml` y en `frontend/` `npm run api:types`; ambos archivos van en el commit.
- Cobertura: ≥ 80 % (JaCoCo / Vitest) en `operation.novelty`, `operation.keys` y los listados nominales;
  ≥ 60 % en el resto.
- Accesibilidad WCAG 2.1 AA: tablas con `caption` y `th scope`, filtros con `label`, foco visible,
  contraste ≥ 4,5:1, objetivos táctiles ≥ 24×24 px; axe sin violaciones en pruebas y E2E.
- Rendimiento: ningún reporte hace consultas por departamento en bucle; cobertura nacional < 5 s con la
  muestra real (diseño §8.1).
- Textos de interfaz en español; cifras con separador de miles `es-CO` y porcentajes con dos decimales.
- Verificación backend: `./mvnw -q verify` (≈10 min, Docker Desktop arriba; si no, iniciarlo con
  `Start-Process "C:\Program Files\Docker\Docker\Docker Desktop.exe"`). Falla ambiental conocida:
  solo `ForwardedClientIpIT` ("Unable to establish loopback connection"). Formato con
  `./mvnw spotless:apply`.
- Verificación frontend: `npm run lint && npx tsc --noEmit && npm test && npx prettier --check .`

## Decisiones de este plan (no están en el diseño)

- **Departamentos por petición, no en el token.** El diseño decía "al iniciar sesión"; el plan los
  resuelve en cada petición con `OperationalAccessService` (consulta pequeña, cacheada 60 s por usuario).
  Así no cambia el JWT y un cambio en `GRAL_TERRITORIAL_ACCESO` aplica sin cerrar sesión.
- **Cédula fuera de las URL.** Las operaciones sobre un funcionario reciben `C_C` en el cuerpo JSON (POST),
  nunca en path ni query, porque las URL quedan en logs de acceso.
- **Parámetro `encuesta` de los reportes**: `GENERAL | EDI | EDID` (filtro `ENC` nulo, `'1'`, `'2'` sobre
  la tabla de muestra de la encuesta configurada). `anio` por defecto el de `SurveyProperties`.
- **Código de "otra causa"**: propiedad `app.operation.novelty-other-cause-code`
  (`NOVELTY_OTHER_CAUSE_CODE`, por defecto `8`); el README pide verificarlo contra `GRAL_NOVEDAD` real.
- **Cortes**: directorio `app.operation.cuts-dir` (`REPORTS_DIR`, por defecto `/data/reports`), volumen
  `reports-data` en `docker-compose.yml`.

## Review Focus

1. Un territorial que pide por parámetro o cuerpo un departamento que no es suyo (reportes, cortes,
   novedades) recibe 403 y no ve ni cambia nada → pruebas en Tareas 2, 4, 5, 6, 8.
2. "Otra causa" con caracteres fuera de Latin-9 (emoji, comillas tipográficas) y en el límite de 500
   bytes → se guarda codificada; 501 bytes → 422 → prueba en Tarea 8.
3. Dos personas cambian la misma novedad a la vez → la segunda recibe 409 y nada se pisa → Tarea 8.
4. Año o departamento sin muestra, o muestra efectiva 0 (todo novedad) → reporte vacío o porcentajes
   0,00, nunca error ni división por cero → Tarea 3.
5. Un nombre o una entidad que empieza por `=`, `+`, `-` o `@` en el `.xlsx` → celda de texto, nunca
   fórmula → Tarea 4 (`XlsxWriterTest`).

---

### Task 1: Datos de desarrollo con la estructura real

**Files:**
- Modify: `db/dev/schema.sql`, `db/dev/data.sql`
- Modify: `db/verificacion/verificar-base-produccion.sql`
- Create: `backend/src/test/java/co/gov/dane/edi/support/OperationalSampleFixture.java`
- Test: `backend/src/test/java/co/gov/dane/edi/support/OperationalSampleFixtureIT.java`

**Interfaces:**
- Produces: `OperationalSampleFixture` (componente de prueba) con
  `void load(int year)` y `void clear(int year)`; constantes `DEPARTMENT_ANTIOQUIA = 5`,
  `DEPARTMENT_BOGOTA = 11`, `DEPARTMENT_VALLE = 76`, `ENTITY_MIXED = 900001` (funcionarios web y DMC),
  `ENTITY_EDID = 900002`, `ENTITY_NO_CATALOG = 900099` (no está en `GRAL_ENTI`); usuarios ficticios
  `territorial-multi` (rol 2, departamentos 5 y 11 para `'EDIN, EDID'`), `territorial-solo` (rol 2,
  `COD_DPTO = 76`, sin filas de acceso), `operativo` (rol 3), `ingeniero` (rol 1).

- [ ] **Step 1: Ampliar `schema.sql`**
  - `EDIN_MUESTRA`: agregar `ENC VARCHAR2(20)`, `AREA_TRA NUMBER(1)`, `TC2 NUMBER(15)`,
    `IDFNOV NUMBER(22,3)`, `NOV_OTRA_CAUSA VARCHAR2(500)`, `IDMUES VARCHAR2(15)`; mantener los tipos que
    ya usa el código (no cambiar `COD_DEPTO`/`COD_ENT` a NUMBER si rompe pruebas existentes; si se
    cambian, `./mvnw verify` completo debe pasar).
  - `EDIN_20_CAP_H_PREV_PRACT`: agregar `H06 VARCHAR2(2)`.
  - Tablas nuevas con el DDL real del diseño §4: `GRAL_NOVEDAD`, `GRAL_ENTI`, `GRAL_DEPARTAMENTOS`,
    `GRAL_TERRITORIAL_ACCESO`; `GRAL_USUARIOS_ADM` ya existe.
- [ ] **Step 2: Escribir `OperationalSampleFixtureIT`** que llama `load(2026)` y comprueba por SQL:
  existe una fila por caso de la tabla siguiente y `clear(2026)` deja 0 filas de 2026 en la muestra.
  Casos mínimos (nombres `Persona Ficticia NN`, cédulas `9000000NN`): completo; completo con novedad;
  incompleto; sin diligenciar; novedad sin iniciar; novedad "otra causa"; DMC (`TC1=2`) en
  `ENTITY_MIXED`; EDID (`ENC='2'`); inactivo (`ATV=0`); entidad sin catálogo; sin clave (`LGN` nulo)
  web y DMC; niveles 1, 2 y 3; un nombre `=SUMA(1;2)` y una entidad `@Ficticia` (Review Focus 5).
- [ ] **Step 3: Ejecutar** `./mvnw -q -Dit.test=OperationalSampleFixtureIT verify` → FAIL (clase no existe).
- [ ] **Step 4: Implementar el fixture** (inserciones parametrizadas; borrado por `ANIO` y por los
  códigos ficticios en catálogos y capítulos).
- [ ] **Step 5: Ejecutar** el IT → PASS.
- [ ] **Step 6: `data.sql`**: unas pocas filas ficticias equivalentes para el entorno `dev` (incluye los
  cuatro usuarios con clave ficticia).
- [ ] **Step 7: Script de verificación**: agregar consultas de solo lectura a `ALL_TAB_COLUMNS` para
  `GRAL_NOVEDAD`, `GRAL_ENTI`, `GRAL_DEPARTAMENTOS`, `GRAL_TERRITORIAL_ACCESO` y a `USER_TAB_PRIVS` /
  `SESSION_PRIVS` para UPDATE sobre `EDIN_MUESTRA`; y `SELECT IDFNOV, NOVEDAD FROM GRAL_NOVEDAD` (catálogo,
  sin datos personales) para confirmar el código de "otra causa".
- [ ] **Step 8: `./mvnw -q verify`** → todo verde (salvo la falla ambiental conocida).
- [ ] **Step 9: Commit** `test: muestra ficticia de la Fase 4 y estructura real de catálogos`.

### Task 2: Alcance operativo por rol y departamento

**Files:**
- Create: `backend/src/main/java/co/gov/dane/edi/operation/OperationalAccess.java`,
  `OperationalAccessService.java`, `DepartmentAccessRepository.java`, `Department.java`,
  `operation/controller/OperationalScopeController.java`
- Modify: `backend/src/main/java/co/gov/dane/edi/security/SecurityConfig.java`
- Test: `backend/src/test/java/co/gov/dane/edi/operation/OperationalAccessServiceIT.java`,
  `operation/OperationalScopeIT.java`

**Interfaces:**
- Produces:
  - `record Department(int code, String name)`.
  - `record OperationalAccess(boolean allDepartments, List<Department> departments, boolean
    canManageNovelties, boolean canManageKeys)` con
    `void requireDepartment(int departmentCode)` (lanza `AccessDeniedException` → 403) y
    `List<Integer> departmentCodesOrAll(Integer requested)` (requested nulo → los suyos o todos; ajeno →
    403).
  - `OperationalAccessService.accessFor(SessionPrincipal principal): OperationalAccess` y
    `OperationalAccessService.current(Jwt jwt)` (atajo con `SessionTokenService.principalFrom`).
  - `GET /api/v1/admin/operation/scope` → `OperationalScope { allDepartments, departments:[{code,name}],
    canManageNovelties, canManageKeys, defaultYear }` (operationId `operationScope`).
- Reglas: `ADMIN_NACIONAL` y `ADMIN_OPERATIVO` → todos los departamentos de `GRAL_DEPARTAMENTOS`;
  `ADMIN_TERRITORIAL` → filas de `GRAL_TERRITORIAL_ACCESO` con `COD_USUA` del usuario y
  `(',' || REPLACE(PROY,' ','') || ',') LIKE '%,' || :survey || ',%'`; si no hay filas,
  `GRAL_USUARIOS_ADM.COD_DPTO`; `canManageNovelties` solo territorial; `canManageKeys` solo nacional.
  Solo `ADMIN_CUESTIONARIO` → 403 en todo `/operation`, `/reports`, `/novelties`, `/keys`.

- [ ] **Step 1: Pruebas** `OperationalAccessServiceIT` (con el fixture): `territorial-multi` → [5, 11];
  `territorial-solo` → [76]; `operativo` y `ingeniero` → `allDepartments` con los 3 departamentos;
  `requireDepartment(76)` para `territorial-multi` lanza `AccessDeniedException`;
  `departmentCodesOrAll(null)` de territorial → sus códigos. `OperationalScopeIT`: respuesta JSON por
  rol y 403 para una sesión solo `ADMIN_CUESTIONARIO`.
- [ ] **Step 2: Ejecutar** → FAIL.
- [ ] **Step 3: Implementar** el repositorio, el servicio (caché `ConcurrentHashMap` con vencimiento de
  60 s por `subject`) y el controlador. En `SecurityConfig`, antes de la regla general de admin:
  `/api/v1/admin/operation/**` y `/api/v1/admin/reports/**` → `ADMIN_NACIONAL, ADMIN_TERRITORIAL,
  ADMIN_OPERATIVO`; `/api/v1/admin/novelties/**` → `ADMIN_TERRITORIAL`; `/api/v1/admin/keys/**` →
  `ADMIN_NACIONAL`.
- [ ] **Step 4: Ejecutar** → PASS. Regenerar OpenAPI y tipos.
- [ ] **Step 5: Commit** `feat: alcance operativo por rol y departamentos del territorial`.

### Task 3: Cálculo de cobertura

**Files:**
- Create: `backend/src/main/java/co/gov/dane/edi/operation/report/ReportSurvey.java`,
  `SampleStatus.java`, `CoverageRepository.java`, `CoverageService.java`,
  `operation/report/dto/{CoverageReport,DepartmentCoverage,EntityCoverage,LevelCoverage,CoverageTotals}.java`
- Test: `backend/src/test/java/co/gov/dane/edi/operation/report/CoverageTotalsTest.java`,
  `CoverageServiceIT.java`

**Interfaces:**
- Consumes: `OperationalAccess.departmentCodesOrAll`, `AnswerTableResolver.tableFor(Chapter, String,
  int)`, `SurveyProperties.sampleTableName()`.
- Produces:
  - `enum ReportSurvey { GENERAL(null), EDI("1"), EDID("2") }` con `String encCode()`.
  - `enum SampleStatus { COMPLETE, NOVELTY, INCOMPLETE, NOT_STARTED }` con el SQL `CASE` del diseño §5.1
    expuesto como constante `STATUS_CASE` (`b`/`h`/`m` como alias) para reutilizarlo en las Tareas 6 y 7.
  - `record CoverageTotals(long sample, long novelties, long effectiveSample, long complete, long
    incomplete, long notStarted, BigDecimal completePct, BigDecimal incompletePct, BigDecimal
    notStartedPct)` con `static CoverageTotals of(long sample, long novelties, long complete, long
    incomplete)` (efectiva = muestra − novedades; sin diligenciar = efectiva − completas − incompletas;
    porcentajes `HALF_UP` a 2 decimales; efectiva 0 → 0.00) y `CoverageTotals plus(CoverageTotals)`.
  - `record LevelCoverage(int level, CoverageTotals totals)`;
    `record EntityCoverage(long code, String name, CoverageTotals totals, List<LevelCoverage> webLevels)`;
    `record DepartmentCoverage(int code, String name, List<EntityCoverage> entities, CoverageTotals
    totals)`; `record CoverageReport(ReportSurvey survey, int year, OffsetDateTime generatedAt,
    List<DepartmentCoverage> departments, int departmentCount, int entityCount, CoverageTotals national,
    List<LevelCoverage> nationalWebLevels)`.
  - `CoverageService.coverage(OperationalAccess access, ReportSurvey survey, int year, Integer
    departmentCode): CoverageReport`.
- Algoritmo: **una** consulta agrupada por `COD_DEPTO, NOM_DEPTO, COD_ENT, ENTIDAD, AREA_TRA, TC1,
  estado` sobre la muestra (`ANIO = :year`, `ATV = 1`, `ENC` si aplica, `COD_DEPTO IN (:departments)`),
  `LEFT JOIN` a capítulos B y H por `C_C`, `JOIN GRAL_DEPARTAMENTOS`, `LEFT JOIN GRAL_ENTI` (sin catálogo →
  nombre `Entidad <código>`); la agregación por entidad, departamento y nación se hace en Java. Una fila
  por entidad (web + DMC sumados); niveles solo con `TC1 = 1`. Orden: departamento por nombre, entidad por
  código.
- Si la tabla del capítulo B del año no tiene la columna `B07` o la del H no tiene `H06` (consulta a
  `ALL_TAB_COLUMNS` con el esquema de `SurveyProperties.dbSchema()`, cacheada por año), el servicio lanza
  `ReportUnavailableException` (nueva, en `operation/report`) que `ApiExceptionHandler` traduce a 422 con título "Reporte no
  disponible" y detalle "El cuestionario de <año> no tiene las preguntas B07 y H06 que definen una
  encuesta completa." (diseño §5.1).

- [ ] **Step 1: `CoverageTotalsTest`**: `of(10, 2, 5, 1)` → efectiva 8, sin diligenciar 2, completas
  62.50, incompletas 12.50, sin diligenciar 25.00; `of(3, 3, 0, 0)` → efectiva 0 y los tres porcentajes
  `0.00`; `of(0,0,0,0)` → ceros.
- [ ] **Step 2: `CoverageServiceIT`** (fixture 2026), con cifras exactas calculadas a mano del fixture:
  `ENTITY_MIXED` aparece **una** vez y suma web + DMC (corrección 1); completo con novedad cuenta en
  completas y no en novedades (corrección 5); `EDI` no incluye la fila `ENC='2'` (corrección 3);
  `entityCount` cuenta entidades, no (entidad, TC1) (corrección 9); inactivo excluido; entidad sin
  catálogo presente con nombre `Entidad 900099`; un año cuya tabla H no tiene `H06` → 422 "Reporte no
  disponible"; año 2019 sin muestra → `departments` vacío y totales en
  cero (Review Focus 4); territorial con departamento ajeno → 403.
- [ ] **Step 3: Ejecutar** → FAIL. **Step 4: Implementar.** **Step 5: Ejecutar** → PASS.
- [ ] **Step 6: Commit** `feat: cálculo de cobertura con criterio único de estado`.

### Task 4: Endpoint de cobertura, años disponibles y exportación `.xlsx`

**Files:**
- Modify: `backend/pom.xml` (dependencia `poi-ooxml` fijada)
- Create: `backend/src/main/java/co/gov/dane/edi/operation/excel/XlsxWriter.java`,
  `operation/excel/XlsxSheet.java`, `operation/report/CoverageExcel.java`,
  `operation/controller/ReportController.java`, `operation/report/ReportYearsRepository.java`
- Create: `docs/fase-4-diferencias-reportes.md`
- Test: `operation/excel/XlsxWriterTest.java`, `operation/report/CoverageReportIT.java`

**Interfaces:**
- Produces:
  - `record XlsxSheet(String name, String title, List<String> headers, List<List<Object>> rows)`;
    `XlsxWriter.write(List<XlsxSheet> sheets): byte[]` — `SXSSFWorkbook`, fila de título, encabezados
    congelados, `String` siempre como celda de texto (nunca fórmula), `Number` numérica,
    `BigDecimal` con formato `0.00`, `LocalDate`/`LocalDateTime` con formato de fecha.
  - `ReportController.excelResponse(byte[] content, String fileName): ResponseEntity<byte[]>` (helper
    estático: `Content-Disposition: attachment`, tipo
    `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`, `Cache-Control: no-store`);
    nombre de archivo `<reporte>-<encuesta>-<anio>[-dpto<NN>]-<yyyy-MM-dd>.xlsx`.
  - `GET /api/v1/admin/reports/years` → `List<Integer>` (años de la muestra, descendente).
  - `GET /api/v1/admin/reports/coverage?encuesta=GENERAL&anio=2026[&departamento=5]` → `CoverageReport`
    (operationId `reportCoverage`).
  - `GET /api/v1/admin/reports/coverage/excel?...` → `.xlsx` (operationId `reportCoverageExcel`): hoja
    "Cobertura" con columnas Departamento, Código entidad, Entidad, Nivel (Total / 1 / 2 / 3), Muestra,
    Novedades, Muestra efectiva, Completas, % completas, Incompletas, % incompletas, Sin diligenciar,
    % sin diligenciar; filas de total por departamento y hoja "Consolidado nacional".
  - `CoverageExcel.sheets(CoverageReport): List<XlsxSheet>` (reutilizado por la Tarea 5).

- [ ] **Step 1: Verificar `poi-ooxml`** en Maven Central, agregarla con versión fija.
- [ ] **Step 2: `XlsxWriterTest`**: lee el resultado con `XSSFWorkbook`; texto `=SUMA(1;2)` y `@Ficticia`
  quedan como `CellType.STRING` (Review Focus 5); encabezado congelado; `BigDecimal("62.50")` numérico
  con formato `0.00`.
- [ ] **Step 3: `CoverageReportIT`**: JSON por rol (operativo ve 3 departamentos; `territorial-multi`
  solo 5 y 11; `departamento=76` para él → 403); Excel con nombre esperado, `no-store` y la cifra de
  `ENTITY_MIXED` en la hoja; `encuesta=OTRA` → 400; `years` contiene 2026.
- [ ] **Step 4: Ejecutar** → FAIL. **Step 5: Implementar.** **Step 6: Ejecutar** → PASS.
- [ ] **Step 7: `docs/fase-4-diferencias-reportes.md`**: las nueve correcciones del diseño §5.4, cada una
  con: reporte PHP afectado, causa, efecto en las cifras y cómo comprobarlo (consulta de solo lectura de
  ejemplo, sin datos).
- [ ] **Step 8:** Regenerar OpenAPI y tipos. **Commit** `feat: reporte de cobertura con exportación a Excel`.

### Task 5: Cortes de cobertura e histórico

**Files:**
- Create: `backend/src/main/java/co/gov/dane/edi/operation/report/CoverageCut.java`,
  `CoverageCutStore.java`, `CoverageCutService.java`, `operation/report/dto/{CutRequest,CutSummary}.java`,
  `operation/OperationProperties.java`
- Modify: `ReportController.java`, `application.yml`, `docker-compose.yml`, `.env.example`
- Test: `operation/report/CoverageCutStoreTest.java`, `operation/report/CoverageCutIT.java`

**Interfaces:**
- Consumes: `CoverageService.coverage`, `CoverageExcel.sheets`, `XlsxWriter.write`, `AdminAudit.record`.
- Produces:
  - `@ConfigurationProperties("app.operation") record OperationProperties(String cutsDir, int
    noveltyOtherCauseCode)` (`REPORTS_DIR` por defecto `/data/reports`; `NOVELTY_OTHER_CAUSE_CODE` por
    defecto `8`).
  - `record CoverageCut(String id, ReportSurvey survey, int year, int departmentCode, String generatedBy,
    OffsetDateTime generatedAt, CoverageReport report)`; `id` = `yyyyMMdd-HHmmss-<6 hex aleatorios>`,
    validado con `[0-9]{8}-[0-9]{6}-[0-9a-f]{6}` antes de tocar el disco.
  - `CoverageCutStore.save(CoverageCut)`, `list(int year, Collection<Integer> departments):
    List<CutSummary>` (más reciente primero), `find(String id): Optional<CoverageCut>`; escritura atómica
    (archivo temporal + `ATOMIC_MOVE`), un archivo por corte `<anio>/<id>.json`.
  - `POST /api/v1/admin/reports/cuts` body `CutRequest(encuesta, anio, departamento)` → `CutSummary(id,
    survey, year, departmentCode, departmentName, generatedBy, generatedAt)`;
    `@AllowedInReadOnlyQuestionnaire`; auditoría `report-cut-create` con códigos `[dpto, id]`.
  - `GET /api/v1/admin/reports/cuts?anio=2026[&departamento=5]` → `List<CutSummary>`.
  - `GET /api/v1/admin/reports/cuts/{id}/excel` → `.xlsx` del corte (403 si el departamento del corte
    no es del usuario; 404 si no existe o el id es inválido).
- El corte guarda solo `CoverageReport` (cifras y nombres de entidad/departamento): ningún dato personal.

- [ ] **Step 1: `CoverageCutStoreTest`** (`@TempDir`): guardar y leer; dos cortes el mismo día se
  conservan; id `../x` → `find` vacío sin tocar el disco; lista filtrada por departamento.
- [ ] **Step 2: `CoverageCutIT`**: generar en modo solo lectura (`QUESTIONNAIRE_WRITES_ENABLED=false`) →
  200; territorial genera corte de su departamento y no ve el de otro; descarga Excel; línea de
  auditoría sin nombres.
- [ ] **Step 3: Ejecutar** → FAIL. **Step 4: Implementar** (+ volumen `reports-data:/data/reports` y
  variables en `docker-compose.yml` y `.env.example`). **Step 5: Ejecutar** → PASS.
- [ ] **Step 6:** OpenAPI y tipos. **Commit** `feat: cortes de cobertura e histórico descargable`.

### Task 6: Estado de mi operativo (listado nominal)

**Files:**
- Create: `backend/src/main/java/co/gov/dane/edi/operation/report/OperationalStatusRepository.java`,
  `OperationalStatusService.java`, `operation/report/dto/{StatusFilter,PersonStatusRow,PersonStatusPage}.java`
- Modify: `ReportController.java`
- Test: `operation/report/OperationalStatusIT.java`

**Interfaces:**
- Consumes: `SampleStatus.STATUS_CASE`, `OperationalAccess`, `AnswerTableResolver`.
- Produces:
  - `enum StatusFilter { ALL, COMPLETE, INCOMPLETE, NOT_STARTED, NOVELTY }`.
  - `record PersonStatusRow(long entityCode, String entityName, String respondentCode, String name, Integer
    level, String loadType /* WEB|DMC */, SampleStatus status, LocalDateTime completedAt /* FECHA de H si
    completo */, Integer noveltyCode, String noveltyName, String otherCause, List<String> missingChapters)`.
  - `record PersonStatusPage(List<PersonStatusRow> rows, int page, int size, long total)`.
  - `GET /api/v1/admin/reports/operational-status?encuesta&anio&departamento&entidad&estado&page=0&size=100`
    (`departamento` obligatorio; `size` máx. 100) y `.../excel` (todas las filas, hoja "Estado
    operativo"). `no-store`.
- `missingChapters`: capítulos B–H sin fila para el `C_C` (siete `LEFT JOIN` vía `AnswerTableResolver`).
  El estado sigue §5.1 (no "7 capítulos presentes": corrección 6). Orden entidad, tipo de carga, nombre.

- [ ] **Step 1: `OperationalStatusIT`**: cada filtro devuelve exactamente los casos del fixture; completo
  con novedad sale como `COMPLETE` con su novedad visible; `NOT_STARTED` respeta el departamento en
  JSON **y** en Excel (corrección 4); paginación (`size=2`); `size=500` → 400; departamento ajeno → 403;
  cabecera `no-store`.
- [ ] **Step 2: Ejecutar** → FAIL. **Step 3: Implementar.** **Step 4: Ejecutar** → PASS.
- [ ] **Step 5:** OpenAPI y tipos. **Commit** `feat: reporte nominal de estado del operativo`.

### Task 7: Reportes de novedades y de rendimientos

**Files:**
- Create: `operation/report/NoveltyReportRepository.java`, `PerformanceReportRepository.java`,
  `operation/report/dto/{NoveltyReportRow,PerformanceRow,ChapterTimestamp}.java`
- Modify: `ReportController.java`
- Test: `operation/report/NoveltyAndPerformanceReportIT.java`

**Interfaces:**
- Produces:
  - `record NoveltyReportRow(int departmentCode, String departmentName, long entityCode, String
    entityName, String respondentCode, String name, Integer level, int noveltyCode, String noveltyName,
    String otherCause, boolean complete)`.
  - `record ChapterTimestamp(String chapter, LocalDate date, LocalTime time)`;
    `record PerformanceRow(long entityCode, String entityName, String respondentCode, String name, Integer
    level, List<ChapterTimestamp> chapters /* B..H, null si no hay fila */)`.
  - `GET /api/v1/admin/reports/novelties?encuesta&anio[&departamento]` y `/excel`;
    `GET /api/v1/admin/reports/performance?encuesta&anio[&departamento]` y `/excel` (solo B07 no nulo;
    Excel con columnas Fecha B, Hora B … Fecha H, Hora H en ese orden: corrección 8). Sin paginación en
    JSON (el volumen por departamento es acotado); filtro por departamento según alcance. `no-store`.
- Catálogo de novedades: `GRAL_NOVEDAD` con `PROY LIKE :survey || '%'` y `ANIO = :year`.

- [ ] **Step 1: IT**: novedades incluye "otra causa" con su texto y marca `complete=true` en el caso
  completo con novedad; territorial solo ve sus departamentos (en el PHP veía todo el país); rendimientos
  excluye a quien no inició y en Excel la columna "Hora H" contiene la hora; `no-store`.
- [ ] **Step 2: Ejecutar** → FAIL. **Step 3: Implementar.** **Step 4: Ejecutar** → PASS.
- [ ] **Step 5:** OpenAPI y tipos. **Commit** `feat: reportes de novedades y de rendimientos`.

### Task 8: Gestión de novedades (backend)

**Files:**
- Create: `backend/src/main/java/co/gov/dane/edi/operation/novelty/NoveltyRepository.java`,
  `NoveltyService.java`, `operation/novelty/dto/{NoveltyCatalogEntry,NoveltyEntity,NoveltyPerson,
  NoveltyChangeRequest,NoveltyRemoveRequest,NoveltyPeopleRequest}.java`,
  `operation/controller/NoveltyController.java`
- Test: `operation/novelty/NoveltyServiceIT.java`, `operation/novelty/NoveltyControllerIT.java`

**Interfaces:**
- Consumes: `OperationalAccess.requireDepartment`, `OperationProperties.noveltyOtherCauseCode`,
  `Latin9Text.encode/fits`, `AdminAudit.record`, `SampleStatus.STATUS_CASE`.
- Produces (todas solo `ADMIN_TERRITORIAL`, `no-store`):
  - `GET /api/v1/admin/novelties/catalog?anio` → `List<NoveltyCatalogEntry(int code, String name, boolean
    requiresText)>`.
  - `GET /api/v1/admin/novelties/entities?anio&departamento` → `List<NoveltyEntity(long code, String
    name, long sampleSize, long withNovelty)>`.
  - `POST /api/v1/admin/novelties/people` body `NoveltyPeopleRequest(anio, departamento, entidad)` →
    `List<NoveltyPerson(String respondentCode, String name, Integer level, SampleStatus status, Integer
    noveltyCode, String otherCause)>` (POST para que la consulta no quede en la URL; se marca
    `@AllowedInReadOnlyQuestionnaire`, no escribe).
  - `POST /api/v1/admin/novelties/assign` body `NoveltyChangeRequest(int anio, String respondentCode, int
    noveltyCode, String otherCause, Integer expectedNoveltyCode, String expectedOtherCause)` → 204.
  - `POST /api/v1/admin/novelties/remove` body `NoveltyRemoveRequest(int anio, String respondentCode,
    Integer expectedNoveltyCode, String expectedOtherCause)` → 204 (limpia `IDFNOV` y `NOV_OTRA_CAUSA`).
- Reglas: código debe existir en el catálogo del año (si no, 422); "otra causa" exige texto no vacío y
  `Latin9Text.fits(text, 500)` (si no, 422); otros códigos guardan `NOV_OTRA_CAUSA` nulo.
  `UPDATE ... WHERE C_C = :cc AND ANIO = :year AND COD_DEPTO IN (:departments) AND
  DECODE(IDFNOV, :expectedCode, 1, 0) = 1 AND DECODE(NOV_OTRA_CAUSA, :expectedText, 1, 0) = 1`: si
  actualiza 0 filas, se distingue con una lectura por `C_C`/año/departamentos: no existe o es de otro
  departamento → 404 (sin revelar cuál); existe con otro valor → 409. Auditoría `novelty-assign` /
  `novelty-remove` con códigos `[IDMUES o "sin-idmues", anterior o "ninguna", nuevo o "ninguna"]`
  (`IDMUES` solo si cumple el patrón de `AdminAudit`).

- [ ] **Step 1: `NoveltyServiceIT`**: asignar retiro y leerla; modificar a "otra causa" con
  `“Cambio de sede” — ✓` → se guarda con entidades y se lee igual (Review Focus 2); texto de exactamente
  500 bytes Latin-9 → OK, 501 → 422; código inexistente → 422; "otra causa" sin texto → 422; eliminar
  limpia ambas columnas; `expectedNoveltyCode` desactualizado → 409 y la fila no cambia (Review Focus 3);
  funcionario de departamento ajeno → 404 y sin cambios (Review Focus 1); la auditoría no contiene la
  cédula, el nombre ni el texto.
- [ ] **Step 2: `NoveltyControllerIT`**: `operativo` e `ingeniero` → 403; territorial en modo solo
  lectura → 204; sin CSRF → 403; la cédula nunca aparece en la URL de ninguna operación (revisar los
  mapeos).
- [ ] **Step 3: Ejecutar** → FAIL. **Step 4: Implementar.** **Step 5: Ejecutar** → PASS y cobertura del
  paquete ≥ 80 %.
- [ ] **Step 6:** OpenAPI y tipos. **Commit** `feat: gestión de novedades por la territorial`.

### Task 9: Claves de la muestra (backend)

**Files:**
- Create: `backend/src/main/java/co/gov/dane/edi/operation/keys/CredentialGenerator.java`,
  `SampleKeysRepository.java`, `SampleKeysService.java`,
  `operation/keys/dto/{KeysSummary,DepartmentKeys,KeysGenerateRequest,KeysGenerateResult}.java`,
  `operation/controller/SampleKeysController.java`
- Test: `operation/keys/CredentialGeneratorTest.java`, `operation/keys/SampleKeysIT.java`

**Interfaces:**
- Produces (solo `ADMIN_NACIONAL`):
  - `CredentialGenerator(SecureRandom random)` con `String next()` → 8 caracteres de `A`–`Z` (26 letras,
    sin repetir la E del PHP).
  - `GET /api/v1/admin/keys/summary?anio` → `KeysSummary(int year, List<DepartmentKeys(int code, String
    name, long eligible, long withKey, long withoutKey)>)` donde elegible = `TC1 = 1 AND ATV = 1`.
  - `POST /api/v1/admin/keys/generate` body `KeysGenerateRequest(int anio, long expectedWithoutKey)` →
    `KeysGenerateResult(long generated)`; `@AllowedInReadOnlyQuestionnaire`; 409 si
    `expectedWithoutKey` no coincide con lo pendiente (la confirmación de la interfaz quedó vieja).
  - `GET /api/v1/admin/keys/excel?anio&departamento` → `.xlsx` hoja "Claves" con Entidad, Código
    entidad, Cédula, Nombre, Usuario, Clave de todos los elegibles con clave del departamento; `no-store`;
    nombre `claves-<encuesta>-<anio>-dpto<NN>-<fecha>.xlsx`.
- Algoritmo de `generate` (una transacción): `SELECT C_C FROM <muestra> WHERE ANIO = :y AND TC1 = 1 AND
  ATV = 1 AND LGN IS NULL FOR UPDATE`; cargar en un `Set` todos los `LGN` del año; por cada fila generar
  `LGN` hasta que no esté en el conjunto y una `PSW`; `batchUpdate` `SET LGN, PSW WHERE C_C = :cc AND
  ANIO = :y AND LGN IS NULL`. Auditoría `keys-generate` `[cantidad]` y `keys-download` `[dpto]`; nunca
  las claves.

- [ ] **Step 1: `CredentialGeneratorTest`** con `SecureRandom` sembrado vía un `Random` de prueba:
  longitud 8, solo `[A-Z]`; 10 000 valores usan las 26 letras.
- [ ] **Step 2: `SampleKeysIT`**: el resumen cuenta elegibles/con clave/sin clave por departamento;
  generar asigna solo a web activos sin clave (DMC, inactivos y quienes ya tenían clave no cambian);
  segunda generación → `generated = 0`; `LGN` únicos en el año; el login del encuestado
  (`AuthService`) acepta una clave generada; `expectedWithoutKey` viejo → 409; en solo lectura → 200;
  territorial y operativo → 403; Excel con los usuarios del departamento pedido y `no-store`; ningún log
  contiene una clave (capturar salida).
- [ ] **Step 3: Ejecutar** → FAIL. **Step 4: Implementar.** **Step 5: Ejecutar** → PASS, cobertura ≥ 80 %.
- [ ] **Step 6:** OpenAPI y tipos. **Commit** `feat: generación y descarga de claves de la muestra`.

### Task 10: Interfaz de reportes de cobertura y cortes

**Files:**
- Create: `frontend/src/features/admin-reports/reports-api.ts`, `ReportFilters.tsx`,
  `CoveragePage.tsx`, `CoverageTable.tsx`, `CutsPage.tsx`, `format-number.ts`, y sus `*.test.tsx`/`*.test.ts`
- Create: `frontend/src/features/admin/operation-scope.ts` (`useOperationScope()` con React Query sobre
  `operationScope`)
- Modify: `frontend/src/App.tsx`, `frontend/src/components/AdminLayout.tsx`

**Interfaces:**
- Consumes: operaciones `operationScope`, `reportYears`, `reportCoverage`, `reportCoverageExcel`,
  `reportCutCreate`, `reportCuts`, `reportCutExcel` (los operationId que dejaron las Tareas 2, 4 y 5 en
  `api-schema.d.ts`; si el nombre difiere, usar el del esquema).
- Produces: `ReportFilters` (props `{ survey?: boolean; department: 'required' | 'optional'; value;
  onChange }`, con selector de departamento limitado al alcance y de año desde `reportYears`) y
  `formatCount(n)`, `formatPercent(n)` (`es-CO`), reutilizados en las Tareas 11–13; helper
  `downloadReportExcel(path, params, fallbackName)` en `reports-api.ts` (mismo patrón que
  `features/admin-packages`).
- Rutas: `/admin/reportes/cobertura`, `/admin/reportes/cortes` bajo `RequireRole` con `ADMIN_NACIONAL`,
  `ADMIN_TERRITORIAL`, `ADMIN_OPERATIVO`. Menú: grupo "Reportes" con Cobertura, Cortes e histórico,
  Estado del operativo, Novedades, Rendimientos; "Novedades" (gestión) si `canManageNovelties`; "Claves"
  si `canManageKeys`.
- Cobertura: pestañas General / EDI / EDID, tabla por departamento (filas de entidad expandibles a
  niveles web), fila de total por departamento y bloque de consolidado nacional; botón "Exportar a Excel".
  Cortes: botón "Generar corte" (departamento obligatorio) y tabla del histórico con fecha, hora, quién y
  "Descargar".

- [ ] **Step 1: Pruebas**: `format-number.test.ts` (`1234` → `1.234`; `62.5` → `62,50 %`);
  `CoveragePage.test.tsx` con respuesta simulada: muestra la entidad una vez, el consolidado, el
  territorial no ve departamentos ajenos en el selector, "Exportar a Excel" llama a la descarga; axe sin
  violaciones; `CutsPage.test.tsx`: generar agrega la fila al histórico; error 403 muestra
  `ErrorState`. `AdminLayout` muestra los enlaces según rol (prueba existente ampliada).
- [ ] **Step 2: Ejecutar** `npm test` → FAIL. **Step 3: Implementar.** **Step 4:** verificación frontend
  completa → PASS.
- [ ] **Step 5: Commit** `feat: interfaz de cobertura, cortes e histórico`.

### Task 11: Interfaz de estado del operativo, novedades y rendimientos

**Files:**
- Create: `frontend/src/features/admin-reports/OperationalStatusPage.tsx`, `NoveltyReportPage.tsx`,
  `PerformancePage.tsx` y sus pruebas
- Modify: `frontend/src/App.tsx`

**Interfaces:**
- Consumes: `ReportFilters`, `formatCount`, `downloadReportExcel` (Tarea 10); operaciones de las Tareas 6
  y 7.
- Rutas: `/admin/reportes/estado`, `/admin/reportes/novedades`, `/admin/reportes/rendimientos`.
- Estado: filtros departamento (obligatorio), entidad, estado (Todos, Completos, Incompletos, Sin
  diligenciar, Novedad); tabla paginada de 100 con estado en texto (no solo color) y capítulos
  faltantes; "Exportar a Excel". Novedades y rendimientos: tabla + Excel.

- [ ] **Step 1: Pruebas**: cambiar el filtro de estado vuelve a consultar con el parámetro correcto;
  paginación "Siguiente/Anterior" accesible; el estado "Novedad (SD)" se lee en texto; rendimientos
  muestra "—" en capítulos sin guardar; axe sin violaciones en las tres.
- [ ] **Step 2:** FAIL. **Step 3: Implementar.** **Step 4:** verificación frontend → PASS.
- [ ] **Step 5: Commit** `feat: interfaz de estado del operativo, novedades y rendimientos`.

### Task 12: Interfaz de gestión de novedades

**Files:**
- Create: `frontend/src/features/admin-novelties/novelties-api.ts`, `NoveltiesPage.tsx`,
  `NoveltyEditor.tsx` y pruebas
- Modify: `frontend/src/App.tsx`

**Interfaces:**
- Consumes: operaciones de la Tarea 8; `ConfirmDialog`, `ConflictNotice`, `TextField`, `Button`
  existentes; `latin9-text.ts` para contar bytes en el cliente.
- Ruta `/admin/novedades` (`RequireRole` `ADMIN_TERRITORIAL`). Flujo: departamento (si tiene varios) →
  entidad → tabla de funcionarios con estado y novedad → "Asignar", "Cambiar" o "Quitar" por fila.
  El editor muestra el catálogo; si el código requiere texto, campo "Otra causa" obligatorio con contador
  de bytes Latin-9 (máx. 500). Quitar pide confirmación. Un 409 muestra `ConflictNotice` y recarga la
  lista.

- [ ] **Step 1: Pruebas**: asignar envía `expectedNoveltyCode` con el valor leído; "otra causa" sin texto
  no se envía y muestra el error junto al campo; 501 bytes bloquea el envío; 409 → aviso y recarga;
  quitar requiere confirmar; foco vuelve a la fila tras cerrar el editor; axe sin violaciones.
- [ ] **Step 2:** FAIL. **Step 3: Implementar.** **Step 4:** verificación frontend → PASS y cobertura de
  la carpeta ≥ 80 %.
- [ ] **Step 5: Commit** `feat: interfaz de gestión de novedades`.

### Task 13: Interfaz de claves

**Files:**
- Create: `frontend/src/features/admin-keys/keys-api.ts`, `KeysPage.tsx` y prueba
- Modify: `frontend/src/App.tsx`

**Interfaces:**
- Consumes: operaciones de la Tarea 9; `ConfirmDialog`; `downloadReportExcel`.
- Ruta `/admin/claves` (`RequireRole` `ADMIN_NACIONAL`). Tabla por departamento (elegibles, con clave,
  sin clave) y total; "Generar claves" abre confirmación "Se generarán N claves para los funcionarios web
  sin clave del año AAAA. Las claves existentes no cambian." y envía `expectedWithoutKey = N`; 409 →
  aviso y recarga del resumen; "Descargar" por departamento.

- [ ] **Step 1: Pruebas**: la confirmación muestra N; tras generar, el resumen se recarga; 409 muestra el
  aviso; con 0 pendientes el botón está deshabilitado con explicación; axe sin violaciones. Las claves
  nunca se muestran en pantalla (solo en el Excel).
- [ ] **Step 2:** FAIL. **Step 3: Implementar.** **Step 4:** verificación frontend → PASS, cobertura ≥ 80 %.
- [ ] **Step 5: Commit** `feat: interfaz de generación y descarga de claves`.

### Task 14: E2E, documentación y cierre de la fase

**Files:**
- Create: `frontend/e2e/admin-operativo.spec.ts`
- Modify: `README.md`, `CHANGELOG.md`, `docs/diagramas/*` (MER: `GRAL_NOVEDAD`, `GRAL_ENTI`,
  `GRAL_DEPARTAMENTOS`, `GRAL_TERRITORIAL_ACCESO` y su relación con la muestra),
  `docs/superpowers/specs/2026-10-14-formulario-web-design.md` (nota en §7.1/§7.2 que remite al diseño de
  la Fase 4)

- [ ] **Step 1: E2E** con `docker compose` y `data.sql`: (a) operativo abre cobertura, cambia a EDI y
  exporta Excel (descarga con nombre esperado); (b) territorial asigna una novedad "otra causa", la ve en
  el reporte de novedades y la quita; (c) ingeniero ve el resumen de claves y descarga un departamento;
  axe en cada pantalla; proyecto móvil incluido. Si el stack no se puede levantar, anotarlo como
  pendiente con el motivo. Dejar el stack como estaba.
- [ ] **Step 2: README** (las once secciones): Usage con reportes, novedades y claves por rol; variables
  `REPORTS_DIR` y `NOVELTY_OTHER_CAUSE_CODE` (con la instrucción de verificar el código en
  `GRAL_NOVEDAD`); volumen de cortes; nota sobre `SurveyProperties` EDID → `EDID_MUESTRA` (tabla que no
  está en el respaldo; los reportes usan `ENC` de la muestra configurada, diseño §4); enlace a `docs/fase-4-diferencias-reportes.md`; pendientes:
  retiro del admin PHP en el despliegue, D7 sin cambios.
- [ ] **Step 3: CHANGELOG** sección de la versión con la Fase 4 (SemVer MINOR).
- [ ] **Step 4: Verificación completa**: `./mvnw -q verify` y verificación frontend → verde; `npm run
  e2e` si el stack está arriba.
- [ ] **Step 5: Commit** `docs: Fase 4 en README, CHANGELOG y diagramas` (y `test: E2E del administrador
  operativo` aparte).
