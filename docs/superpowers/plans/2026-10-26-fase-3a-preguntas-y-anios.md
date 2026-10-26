# Fase 3a — Preguntas, tablas por año y copia de año: plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** That the technical person can create next year's questionnaire from the previous one and edit its questions without SQL by hand. Editing covers:
- texts, headers and help texts;
- adding a question, with "column pending" status and its script;
- deactivating and reordering questions;
- choosing the answer table of each chapter.

Editing a draft year must never change another year or the other survey.

**Architecture:**
- **Year state and table mapping:** each survey and year has a rules file `<ENC>-<AAAA>.json` in `RULES_DIR`, which now also carries `estado` (borrador / publicado) and `tablas` (chapter → answer table). No new Oracle tables.
- **Questionnaire edits:** written to the `FRM_*` tables in QA through one repository, with id allocation and copy-on-write, inside a transaction under a per-year lock.
- **Respondent flow:** resolves its answer tables through a validated resolver instead of the fixed `Chapter.tableName()`.

**Tech Stack:** the same as Phases 1–2, with no new dependencies.
- Backend: Spring Boot 4.1.1, Java 21, `NamedParameterJdbcTemplate`, Jackson 3, Testcontainers Oracle Free.
- Frontend: React 19.3 with the React Router 7 data router, TanStack Query 5, RHF 7, Vitest 4 + MSW 2, Playwright 1.63 + axe.

**Spec:** `docs/superpowers/specs/2026-10-26-fase-3-administracion-design.md`: §2, §3 (Preparar, Borrador), §4.1, §4.1.1, §9, §10 and §11. It extends `docs/superpowers/specs/2026-10-14-formulario-web-design.md` §3.2, §4.3, §5.3 and §7.
- Plan 3b covers rules, validation, simulator and test cases.
- Plan 3c covers publishing, corrections and packages.

## Global Constraints

- **No new Oracle tables (D4) and no DDL from the application (D3).** Scripts are generated as text and the technical person runs them.
- **SQL identifiers only from validated sources:**
  - table names from `AnswerTableResolver`, matching `^[A-Z][A-Z0-9_]{1,29}$`, present in `ALL_TABLES` of the configured schema, and having columns `C_C`, `ESTADO` and `TC`;
  - column names matching `^[A-Z][0-9A-Z_]{1,29}$` and present in `ALL_TAB_COLUMNS`;
  - every value bound as a parameter.
- **Copy-on-write:** no edit may modify a `FRM_*` row used by another (PROY, ANIO) pair. Shared rows are copied for the year being edited and repointed first.
- **Writes:** only a year with `estado: "borrador"` accepts structural edits. A missing file, or a file without `estado`, means published and read-only. There is at most one draft per survey.
- **Text:** stored as plain text. HTML entities in existing text are decoded when loaded and written decoded only when that question is edited. It is always rendered escaped.
- **Roles:** every `/api/v1/admin/questionnaire/**` and `/api/v1/admin/years/**` endpoint requires `ADMIN_CUESTIONARIO`. Legacy ROL 1 already maps to it.
- **Audit:** one structured log line per change, with user, survey, year, operation and affected codes. No personal data and no question text.
- **Respondent flow (Phase 2):** must behave identically for 2026. All existing tests stay green unchanged, except the table lookups this plan moves.
- **Quality:**
  - Conventional Commits in Spanish with a `Co-Authored-By` trailer naming the real model;
  - coverage ≥ 80 % on `co.gov.dane.edi.security`, `co.gov.dane.edi.rules` and the new `co.gov.dane.edi.admin`, ≥ 60 % elsewhere;
  - WCAG 2.1 AA, desktop first, usable on tablet;
  - OpenAPI regenerated;
  - nothing pushed without Oscar's authorization.

## Review Focus

1. **Editing 2027 never changes how 2026 renders.**
   - Test `editingDraftYearLeavesOtherYearsUntouched` (Task 6): render 2026 page C1 before and after editing a 2027 text and reordering 2027. The results must be identical.
2. **Two browser tabs editing the same question.** The second save must not silently overwrite the first.
   - Test `staleEditIsRejectedWithConflict` (Task 6): an edit carries the `version` the client read and gets 409 if it changed.
3. **A table mapping naming a non-existent or malicious table** (`"X; DROP"`, `"DUAL"`, a table without `C_C`). It must give 422 with a Spanish message, never SQL.
   - Test `rejectsUnsafeOrForeignTables` (Task 3).
4. **Creating a year that already exists, or while another draft is open.** It must give 409 and leave zero partial rows.
   - Test `createYearIsAllOrNothing` (Task 8): it forces a failure in the middle of the copy and asserts the row counts are unchanged.
5. **Question text with markup** (`<script>`, `&lt;b&gt;`). It is stored as plain text and shown escaped in admin and respondent views.
   - Test `textIsStoredPlainAndRenderedEscaped` (Task 6 backend; Task 10 frontend).

---

## File Structure

```
db/dev/schema.sql, data.sql                       FRM_* aligned with the real structure (columns + PKs), help tables, 2027 left empty
db/reference/frm-ddl-qa.sql                       (Task 1, provided by Oscar) structure of FRM_* from QA, no data
backend/src/main/java/co/gov/dane/edi/
  admin/                    new package (questionnaire administration)
    QuestionnaireYear.java          record: survey, year, state, tables, version
    YearState.java                  enum BORRADOR, PUBLICADO
    YearCatalogService.java         list years, draft lookup, state guard
    AnswerTableResolver.java        chapter → validated table for (survey, year)
    FrmWriteRepository.java         inserts/updates on FRM_*, copy-on-write helpers
    FrmIdAllocator.java             next ids per FRM table (MAX+1 under lock, or sequence)
    QuestionAdminService.java       edit text/headers/help, reorder, add, deactivate
    YearCopyService.java            create year from previous
    ColumnScriptService.java        ALTER TABLE / CREATE TABLE script text
    AdminAudit.java                 structured audit log line
    controller/QuestionnaireAdminController.java, YearAdminController.java
    dto/  request and response records
  rules/   RuleSet (+ estado, tablas), RulesFileRepository (load/save by survey+year, history, atomic)
  repository/AnswerRepository, AnswerColumnRepository, service/SurveyAnswerService, SurveyProgressService,
           QuestionnaireService: table names via AnswerTableResolver; QuestionnaireRepository: year parameter
frontend/src/features/admin-questions/
  admin-api.ts, YearsPage.tsx, QuestionTreePage.tsx, QuestionEditor.tsx, AddQuestionDialog.tsx,
  TablesMappingPanel.tsx, ReorderList.tsx, *.test.tsx
frontend/src/components/AdminLayout.tsx            side menu (Cuestionario; later Reglas, Reportes…)
frontend/e2e/admin-cuestionario.spec.ts
```

---

### Task 1: Real FRM structure in dev, and how ids are generated

**Files:**
- Modify: `db/dev/schema.sql` and `db/dev/data.sql`
- Create: `db/reference/frm-ddl-qa.sql`, but only if Oscar provides it
- Test: `backend/src/test/java/co/gov/dane/edi/DevDataIT.java`

**Interfaces:**
- Produces: dev `FRM_*` tables with the real column lists and primary keys:
  - `FRM_PREGUNTA(COD_PRE PK, PREGUNTA, TIPO)`;
  - `FRM_PRE_GRUPO(COD_PRE_GRUPO PK, GRUPO, ORDEN, COD_PRE)`;
  - `FRM_CAP_PROY(COD_CAP_PROY PK, CAPITULO, NUMERAL, COD_PRE_GRUPO, PROY, ANIO VARCHAR2(4))`;
  - `FRM_CAP_PERIODO(ID_PERIODO PK, ANIO NUMBER(4), PROY, COD_CAP_PROY, NUMERAL)`;
  - `FRM_OBJ_PRE(COD_OBJ_PRE PK, COD_GRUPO, GRUPO, COD_OBJ, COD_CAP_PROY)`;
  - `FRM_SALTO(COD_SAL PK, COD_CAP_PROY, SALTO, VALOR_SALTO)`;
  - `FRM_RTA_GRUPO(COD_GRUPO PK, GRUPO, ORDEN, VALOR, COD_RTA)`;
  - `FRM_ENCABEZADO(COD_ENC PK, COD_CAP_PROY, TIPO, ENCABEZADO, ANIO)`;
  - `FRM_PRE_AYUDA(COD_AYU PK, COD_CAP_PROY, TEXTO)`;
  - `FRM_ENC_AYUDA(COD_AYU PK, COD_ENC, TEXTO)`;
  - the existing fictitious rows, rewritten with ids and the new columns.
- Produces: a written decision, at the top of `schema.sql` and in the README, saying how ids are generated: `MAX+1` or a named sequence per table.

These column lists come from the real 2026 metadata already used in Phase 2.
- If `db/reference/frm-ddl-qa.sql` is provided, it wins over this plan on column types, NOT NULL constraints, sequences and triggers. Record each difference in the task report.
- If it is not provided, ids use `MAX+1` (Task 5). Add a README technical-debt line saying the real DDL must be confirmed before going to QA.

- [ ] **Step 1: Write the failing test.** `DevDataIT.frmTablesHaveRealColumnsAndKeys` asserts, through `ALL_TAB_COLUMNS` and `ALL_CONSTRAINTS` (type `P`), the column set and the primary key of each table above. `DevDataIT.respondentFlowDataStillLoads` asserts the existing 2026 fictitious questionnaire still returns the same pages and items: B1 with B02, B06 and B99 pending, C1 with C01A and C01B, and so on.
- [ ] **Step 2: Run** `cd backend && ./mvnw -B verify -Dit.test=DevDataIT -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true`. Expected: FAIL on the missing columns and keys.
- [ ] **Step 3: Update `schema.sql` and `data.sql`.** Fill `FRM_CAP_PROY.PROY/ANIO/NUMERAL` from the existing period rows, and add one fictitious help row per page.
- [ ] **Step 4: Run the full `./mvnw -B verify`.** Expected: BUILD SUCCESS, with every Phase 2 test unchanged.
- [ ] **Step 5: Commit.** `test(db): estructura real de FRM_* en desarrollo`

### Task 2: Year file — state, table mapping, multi-year load and save

**Files:**
- Modify: `rules/RuleSet.java`, `rules/RulesFileRepository.java`, `rules/RuleSetValidator.java`, `rules/EffectiveRulesService.java`
- Create: `admin/YearState.java`, `admin/QuestionnaireYear.java`
- Test: `rules/RulesFileRepositoryTest.java` (extended), `rules/RuleSetValidatorTest.java` (extended)

**Interfaces:**
- Produces: `RuleSet` gains two components after `validations`:
  - `@JsonProperty("estado") YearState state` (null means `PUBLICADO`);
  - `@JsonProperty("tablas") Map<String, String> tables` (chapter code → table name; empty means the `Chapter.tableName()` defaults).

  Add `RuleSet withState(YearState)`, `RuleSet withTables(Map<String, String>)` and `RuleSet withVersion(int)`. Update every constructor call.
- Produces: `enum YearState { BORRADOR, PUBLICADO }`. The JSON values are `"borrador"` and `"publicado"`, via `@JsonValue`/`@JsonCreator`.
- Produces in `RulesFileRepository`:
  - `RuleSet load(String survey, int year)`, cached per key;
  - `Optional<RuleSet> find(String survey, int year)`, which never seeds;
  - `RuleSet save(RuleSet rules, int expectedVersion, String user, String reason)`;
  - `List<Integer> years(String survey)`, from the file names;
  - `void evict()`.

  The existing `load()` becomes `load(survey.code(), survey.year())`.
- `save` follows spec §5.3:
  1. Compare the version: a mismatch throws `ConflictException("Otro usuario modificó el cuestionario. Recargue la página.")`.
  2. Copy the current file to `RULES_DIR/historial/<ENC>-<AAAA>.<epochMillis>.json`.
  3. Write to a temporary file with version +1, then `ATOMIC_MOVE`.
  4. Write the audit line through `AdminAudit` (Task 5).
- `RuleSetValidator` also checks:
  - `tablas` keys are chapter codes from `Chapter`;
  - values match `^[A-Z][A-Z0-9_]{1,29}$`.

  The existence check lives in `AnswerTableResolver`.

- [ ] **Step 1: Write the failing tests.**
  - `stateDefaultsToPublishedWhenMissing`: the 2026 seed loads as `PUBLICADO` with empty `tablas`.
  - `saveWritesHistoryAndBumpsVersion`.
  - `saveWithStaleVersionConflicts`.
  - `saveIsAtomic`: a failure while writing the temp file leaves the old file intact.
  - `yearsListsExistingFiles`.
  - `validatorRejectsUnknownChapterOrBadTableName`.
- [ ] **Step 2: Run them.** They fail to compile.
- [ ] **Step 3: Implement.** Keep `EffectiveRulesService` working for the active year through `load(code, year)`.
- [ ] **Step 4: Run `./mvnw -B verify`.** Expected: BUILD SUCCESS, with the Phase 2 rules tests unchanged and the regenerated `openapi.yaml` gaining `estado` and `tablas` in `RuleSet`.
- [ ] **Step 5: Commit.** `feat(backend): estado y tablas por año en el archivo de reglas, con historial`

### Task 3: Answer tables resolved per survey and year

**Files:**
- Create: `admin/AnswerTableResolver.java`
- Modify: `repository/AnswerRepository.java`, `repository/AnswerColumnRepository.java`, `service/SurveyAnswerService.java`, `service/SurveyProgressService.java`, `service/QuestionnaireService.java`
- Test: `admin/AnswerTableResolverIT.java`; the Phase 2 ITs stay unchanged

**Interfaces:**
- Produces: `String AnswerTableResolver.tableFor(Chapter chapter, String survey, int year)`:
  - takes the name from the year's `tablas`, or falls back to `chapter.tableName()`;
  - validates it against the pattern, `ALL_TABLES` of `SurveyProperties.dbSchema()`, and the presence of `C_C`, `ESTADO` and `TC`;
  - caches the result, with `evict()`.

  An invalid name throws `AnswerValidationException(Map.of("tablas." + chapter.code(), "La tabla … no existe o no tiene las columnas C_C, ESTADO y TC."))`.
- Produces: `String AnswerTableResolver.activeTableFor(Chapter chapter)`, the same lookup for the active `SurveyProperties` survey and year.
- Every current `chapter.tableName()` and `Chapter.H.tableName()` use in the files above switches to `activeTableFor`. `Chapter.tableName()` stays as the default.

- [ ] **Step 1: Write the failing tests.**
  - `defaultsToFixedTablesWhenYearHasNoMapping`.
  - `usesMappedTable`: create a fictitious `EDIN_21_CAP_B_UBICA` in the test schema with `CREATE TABLE … AS SELECT * FROM EDIN_20_CAP_B_UBICA WHERE 1=0`, map B to it for a 2027 test file, and assert the resolver returns it.
  - `rejectsUnsafeOrForeignTables`: `"X; DROP"`, `"DUAL"`, a table without `C_C`, and a name that doesn't exist all give 422 with the field `tablas.B`.
- [ ] **Step 2: Run them.** They fail.
- [ ] **Step 3: Implement and switch the call sites.**
- [ ] **Step 4: Run the full `./mvnw -B verify`.** Expected: all Phase 2 ITs green (SurveyAnswerIT, SurveyFinishIT, SurveyProgressIT, AnswerRepositoryIT, Edin2026RulesTest).
- [ ] **Step 5: Commit.** `feat(backend): tablas de respuestas por encuesta y año validadas`

### Task 4: Respondent gate for a draft year, and the read API for the admin tree

**Files:**
- Create: `admin/YearCatalogService.java`, `admin/controller/QuestionnaireAdminController.java`, `admin/dto/AdminQuestionnaireView.java`, `admin/dto/AdminEntry.java`, `admin/dto/YearSummary.java`
- Modify: `controller/RespondentSurveyController.java`, `repository/QuestionnaireRepository.java` (year and project as parameters), `security/SecurityConfig.java`
- Test: `admin/QuestionnaireAdminIT.java`, `controller/SurveyDraftYearIT.java`

**Interfaces:**
- Produces: `List<YearSummary> YearCatalogService.years(String survey)`, where `YearSummary(int year, YearState state, int version, boolean active)`.
- Produces: `Optional<Integer> YearCatalogService.draftYear(String survey)`.
- Produces: `void YearCatalogService.requireDraft(String survey, int year)`, which throws `ConflictException("El cuestionario de <AAAA> está publicado y no se puede editar.")`.
- Produces: `GET /api/v1/admin/years/{encuesta}` → `List<YearSummary>`.
- Produces: `GET /api/v1/admin/questionnaire/{encuesta}/{anio}` → `AdminQuestionnaireView(String survey, int year, YearState state, int version, Map<String,String> tables, List<AdminPage> pages)`:
  - `AdminPage(String pageCode, String chapterCode, String tableName, List<AdminEntry> entries)`;
  - `AdminEntry(long capProyId, String numeral, String columnCode, String kind, String inputType, String text, ColumnStatus columnStatus, Integer optionGroup, List<SectionHeader> headers, String help, List<String> referencedByRules)`.
- `{encuesta}` must be `EDIN` or `EDID`. Anything else gives 404.
- Produces: the respondent API (`/api/v1/survey/**`) answers 503 problem+json "La encuesta aún no está disponible." when the active year's file has `estado: borrador`.
- `QuestionnaireRepository.findRowsByPage` gains an overload `(String pageCode, String project, String yearText)`. The respondent path keeps using the active one.
- Produces: `List<QuestionnaireEntry> QuestionnaireService.pageEntries(SurveyPage page, String survey, int year)`, cached per (page, survey, year) and cleared by the existing `evict()`. The current `pageEntries(SurveyPage)` delegates with the active survey and year.
- Produces: `RuleSet EffectiveRulesService.effectiveRules(String survey, int year)`. It does the same merge for any year, with legacy skips translated from that year's `pageEntries`. The current `effectiveRules()` delegates with the active year. `merge` becomes `public static` so `admin` can use it.

- [ ] **Step 1: Write the failing tests.**
  - `adminTreeListsPagesEntriesAndColumnStatus` (ADMIN_CUESTIONARIO): 2026 shows B99 as `PENDING_COLUMN` and C01A `referencedByRules` contains `legacy-C01A`.
  - `respondentAndOtherAdminsGet403`.
  - `unknownSurveyIs404`.
  - `draftActiveYearMakesRespondentApi503`.
  - `textIsStoredPlainAndRenderedEscaped`: entities in the fixture text arrive decoded in `text`.
- [ ] **Step 2: Run them.** They fail.
- [ ] **Step 3: Implement.** `referencedByRules` comes from the year's effective rules (file plus translated legacy skips), scanning each rule's conditions and targets.
- [ ] **Step 4: Run `./mvnw -B verify`.** Expected: green, with the contract regenerated.
- [ ] **Step 5: Commit.** `feat(backend): árbol del cuestionario por año para el administrador`

### Task 5: FRM write repository — id allocation, copy-on-write, audit

**Files:**
- Create: `admin/FrmWriteRepository.java`, `admin/FrmIdAllocator.java`, `admin/AdminAudit.java`
- Test: `admin/FrmWriteRepositoryIT.java`, `admin/FrmIdAllocatorIT.java`

**Interfaces:**
- Produces: `long FrmIdAllocator.next(FrmTable table)`, where `enum FrmTable` lists the id-bearing tables with their id column. Ids are `MAX(id)+1` under `LOCK TABLE <t> IN EXCLUSIVE MODE` inside the caller's transaction, unless Task 1 recorded a sequence for that table, in which case the sequence's `NEXTVAL` is used.
- Produces in `FrmWriteRepository`:
  - `long copyQuestionForYear(long capProyId, String project, String yearText)`: copy-on-write. If the row's `COD_PRE_GRUPO` or `COD_PRE` is used by any `FRM_CAP_PERIODO` row of another (PROY, ANIO), it copies `FRM_PREGUNTA` and `FRM_PRE_GRUPO` and repoints this `FRM_CAP_PROY`. It returns the `COD_PRE` to edit. If nothing is shared, it returns the existing one.
  - `void updateQuestionText(long codPre, String plainText)`.
  - `void updateHeader(long codEnc, String text)`.
  - `void updateHelp(long codAyu, String text)`.
  - `void updateOrder(long codPreGrupo, int order)`.
- Every method must run inside a Spring `@Transactional` boundary owned by the service, under `SaveLocks.withLock("admin:" + survey + ":" + year, …)`.
- Produces: `AdminAudit.record(String user, String survey, int year, String operation, List<String> codes)` writes a structured INFO line on logger `edi.admin.audit`. It never includes text.

- [ ] **Step 1: Write the failing tests.**
  - `allocatorReturnsMaxPlusOneAndIsSerialized`: two concurrent transactions get distinct ids.
  - `copyOnWriteDuplicatesSharedRowsOnly`: a 2027 row sharing `COD_PRE` with 2026 gets a new `COD_PRE`/`COD_PRE_GRUPO`, and 2026 still points to the old ones; an unshared row is reused.
  - `copyOnWriteAlsoProtectsOtherSurvey`: a row shared with EDID for the same year is copied.
  - `auditLineHasNoText`: use `OutputCaptureExtension`.
- [ ] **Step 2: Run them.** They fail.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run them, then the full `./mvnw -B verify`.** Expected: green.
- [ ] **Step 5: Commit.** `feat(backend): escritura en FRM_* con copia al escribir y auditoría`

### Task 6: Edit texts, headers and help, and reorder

**Files:**
- Create: `admin/QuestionAdminService.java`, `admin/dto/EditTextRequest.java`, `admin/dto/ReorderRequest.java`
- Modify: `admin/controller/QuestionnaireAdminController.java`
- Test: `admin/QuestionEditIT.java`

**Interfaces:**
- Produces:
  - `PUT /api/v1/admin/questionnaire/{encuesta}/{anio}/entries/{capProyId}/text`, with body `EditTextRequest(@NotBlank @Size(max=4000) String text, @NotNull Integer version)`;
  - `PUT …/headers/{codEnc}`, with the same body;
  - `PUT …/help/{codAyu}`, with the same body;
  - `PATCH …/pages/{pageCode}/order`, with body `ReorderRequest(@NotEmpty List<Long> capProyIds, @NotNull Integer version)` listing the page's entries in their new order.

  All of them return the refreshed `AdminQuestionnaireView` page.
- `version` is the year file's `version`. Every successful edit bumps it through `RulesFileRepository.save(rules, version, user, reason)`, which also serializes writers and detects stale clients. The reason is auto-generated, e.g. "Texto de C07A".
- After each change, call `QuestionnaireService.evict()`, `EffectiveRulesService` eviction, `AnswerColumnRepository.evict` where relevant, and `AnswerTableResolver.evict()`.
- Text is normalized to plain text: decode entities, strip control characters (`[\p{Cc}&&[^\n\t]]`), trim.
- Reorder rejects a list that is not exactly the page's entries: 422 "La lista no coincide con las preguntas de la página.". It returns the warning list `skipsTargetingEarlierQuestions` (codes) for the UI. Full rule validation comes in Plan 3b.

- [ ] **Step 1: Write the failing tests.**
  - `editsTextOfDraftYear`.
  - `editingDraftYearLeavesOtherYearsUntouched`: render 2026 C1 via `QuestionnaireService` before and after editing and reordering 2027; equal.
  - `staleEditIsRejectedWithConflict`.
  - `publishedYearIsReadOnly`: 2026 gives 409 with the message from Task 4.
  - `reorderRejectsMismatchedList`.
  - `textIsStoredPlainAndRenderedEscaped`: `<script>x</script>&amp;` is stored as the literal plain text and returned unchanged as JSON text.
  - `csrfRequired`.

  The fixture is a 2027 draft created by direct JDBC inserts that copy the 2026 fictitious rows. Task 8 will replace it with the real copy.
- [ ] **Step 2: Run them.** They fail.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run `./mvnw -B verify`.** Expected: green, with the contract regenerated.
- [ ] **Step 5: Commit.** `feat(backend): editar textos, encabezados, ayudas y orden del año en borrador`

### Task 7: Add a question (column pending, ALTER TABLE script) and deactivate

**Files:**
- Create: `admin/ColumnScriptService.java`, `admin/dto/NewQuestionRequest.java`
- Modify: `admin/QuestionAdminService.java`, `admin/controller/QuestionnaireAdminController.java`
- Test: `admin/AddDeactivateQuestionIT.java`, `admin/ColumnScriptServiceTest.java`

**Interfaces:**
- Produces: `POST /api/v1/admin/questionnaire/{encuesta}/{anio}/pages/{pageCode}/entries`, with body `NewQuestionRequest(...)`:
  - `@NotBlank String columnCode`;
  - `@NotBlank String numeral`;
  - `@NotBlank String text`;
  - `@NotNull InputType inputType` (`SINGLE_CHOICE`, `CHECKBOX` or `TEXT`);
  - `Integer optionGroup`, required for `SINGLE_CHOICE` and existing in `FRM_RTA_GRUPO`;
  - `Long afterCapProyId`, null meaning first;
  - `String columnType` (`VARCHAR2` or `NUMBER`);
  - `Integer columnLength`;
  - `@NotNull Integer version`.

  It inserts `FRM_PREGUNTA`, `FRM_PRE_GRUPO` (order after the anchor, shifting the following entries), `FRM_CAP_PROY`, `FRM_CAP_PERIODO` and `FRM_OBJ_PRE` in one transaction. It returns 201 with the page.
- Validation (422, field-keyed, Spanish):
  - `columnCode` matches `^[A-Z][0-9A-Z_]{1,29}$`;
  - the code is not already used in the page or chapter for this year;
  - the numeral is unique in the page;
  - the option group exists;
  - the column type and length are coherent (`VARCHAR2` 1–4000; `NUMBER` precision 1–38).
- Produces: `GET …/entries/{capProyId}/column-script`, which returns `text/plain` with `ALTER TABLE <tabla> ADD (<COL> <TYPE>(<n>));`. The table comes from `AnswerTableResolver.tableFor`. The response is 404 if the column already exists.
- Produces: `DELETE …/entries/{capProyId}?version=N`, which deactivates the entry by deleting its `FRM_CAP_PERIODO` row for (PROY, ANIO). It is rejected with 409 and `errors.rules` (the list of rule ids) if any effective rule references the column. Plan 3b extends this to test cases.
- Produces: `String ColumnScriptService.alterTable(String table, String column, String type, Integer length)` and `String ColumnScriptService.createTableLike(String newTable, String sourceTable)`. Both are pure, validate the identifiers with the same patterns, and throw `IllegalArgumentException` otherwise.

- [ ] **Step 1: Write the failing tests.**
  - `addedQuestionWithoutColumnIsPendingAndHasScript`.
  - `addedQuestionWithExistingColumnIsActive`.
  - `addRejectsDuplicateCodeOrNumeralOrBadGroup`.
  - `deactivateRemovesOnlyThisYearsPeriodRow`.
  - `deactivateRejectedWhenRulesReferenceIt`.
  - `ColumnScriptServiceTest` covers the exact script text, e.g. `ALTER TABLE EDIN_20_CAP_C_EXP_PERS ADD (C30A VARCHAR2(2));`, and rejects identifiers like `"C30A; DROP"`.
- [ ] **Step 2: Run them.** They fail.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run `./mvnw -B verify`.** Expected: green.
- [ ] **Step 5: Commit.** `feat(backend): agregar preguntas con columna pendiente y desactivar`

### Task 8: Create a year from the previous one, and edit the table mapping

**Files:**
- Create: `admin/YearCopyService.java`, `admin/controller/YearAdminController.java`, `admin/dto/CreateYearRequest.java`, `admin/dto/TablesRequest.java`
- Test: `admin/YearCopyIT.java`

**Interfaces:**
- Produces: `POST /api/v1/admin/years/{encuesta}`, with body `CreateYearRequest(@NotNull Integer year, @NotNull Integer fromYear)`. It returns 201 with `YearSummary`. In one transaction under the lock `"admin:" + survey`, it:
  1. Checks that no draft exists for the survey and that `year` has no rows or file. Otherwise it returns 409 "Ya existe un cuestionario para <AAAA>." or "Ya hay un borrador abierto (<AAAA>).".
  2. For every `FRM_CAP_PERIODO` row of (PROY = survey, ANIO = fromYear), creates:
     - a new `FRM_CAP_PROY` (same `CAPITULO`, `NUMERAL`, `COD_PRE_GRUPO` and `PROY`; `ANIO = year`);
     - a new `FRM_CAP_PERIODO`;
     - copies of its `FRM_OBJ_PRE`, `FRM_ENCABEZADO` (with `ANIO = year`) and `FRM_PRE_AYUDA` rows;
     - copies of `FRM_ENC_AYUDA` for the copied headers.
     
     It creates no `FRM_SALTO` rows.
  3. Writes `<ENC>-<year>.json` from the source year's effective rules, using `EffectiveRulesService.effectiveRules(survey, fromYear)` (Task 4). The legacy skips are materialized as `legacy-<COL>` rules, deduplicated with `EffectiveRulesService.merge`. It also carries `validaciones`, the source `tablas` mapping, `estado: borrador` and `version: 1`.

  The file is written only after the database transaction commits. If the file write fails, it compensates by deleting the copied rows and reports a 500 with the cause.
- Produces: `PUT /api/v1/admin/questionnaire/{encuesta}/{anio}/tables`, with body `TablesRequest(@NotNull Map<String,String> tables, @NotNull Integer version)`. Each entry is validated with `AnswerTableResolver` rules, except existence: a missing table is allowed in a draft and reported as `missing`. It is saved in the year file.
- Produces: `GET …/tables/{chapterCode}/create-script`, which returns `ColumnScriptService.createTableLike(new, previousYearTable)` as `text/plain`.

- [ ] **Step 1: Write the failing tests.**
  - `createYearCopiesRowsAndMaterializesLegacySkips`: the 2027 tree renders identically to 2026 (same numerals, columns, options, headers and help). The file has `legacy-C01A` and `estado: borrador`. No `FRM_SALTO` rows exist for 2027.
  - `createYearIsAllOrNothing`: inject a failure after the `FRM_CAP_PERIODO` inserts, for example with a test-only `YearCopyFailureHook` bean. All `FRM_*` counts are unchanged and there is no file.
  - `secondDraftOrExistingYearConflicts`.
  - `tablesMappingSavedAndMissingTableReported`.
  - `createScriptMatchesPreviousTable`.
- [ ] **Step 2: Run them.** They fail.
- [ ] **Step 3: Implement.** Replace Task 6's JDBC fixture with this service in the ITs of Tasks 6 and 7.
- [ ] **Step 4: Run `./mvnw -B verify`.** Expected: green, with the contract regenerated.
- [ ] **Step 5: Commit.** `feat(backend): crear el año a partir del anterior y elegir tablas por capítulo`

### Task 9: Admin layout, years page and the "create year" flow (frontend)

**Files:**
- Create: `frontend/src/components/AdminLayout.tsx`, `frontend/src/features/admin-questions/admin-api.ts`, `YearsPage.tsx`, `YearsPage.test.tsx`
- Modify: `frontend/src/App.tsx` (routes `/admin/cuestionario`, `/admin/cuestionario/:encuesta/:anio`), `src/features/admin/AdminHomePage.tsx`, `src/services/api-schema.d.ts` (regenerated)

**Interfaces:**
- Produces in `admin-api.ts`:
  - `fetchYears(survey)`;
  - `createYear(survey, year, fromYear)`;
  - `fetchQuestionnaire(survey, year)`;
  - `editText(...)`, `editHeader(...)`, `editHelp(...)`, `reorder(...)`, `addQuestion(...)`, `deactivate(...)`;
  - `fetchColumnScript(...)`, `saveTables(...)`, `fetchCreateTableScript(...)`.

  Each wraps `apiClient` with CSRF, like `survey-api.ts`.
- Produces: `AdminLayout` with a labelled `<nav aria-label="Administración">` side menu. The "Cuestionario" entry is visible for `ADMIN_CUESTIONARIO`; the other entries come in later phases. The page `<h1>` and `useDocumentTitle` follow the respondent pattern.
- `YearsPage`:
  - a survey selector (EDIN/EDID);
  - a table of years with state, version and active year, where each row links to its tree;
  - the button "Crear año", which opens a `ConfirmDialog` with a year input (default: the last year + 1) and a source (default: the last published year).

  Errors (409/422) appear in an alert.

- [ ] **Step 1: Write the failing tests.**
  - `listsYearsWithState`.
  - `createsYearAndNavigatesToTree`.
  - `showsConflictWhenDraftExists`.
  - `menuOnlyShowsQuestionnaireForRole`.
- [ ] **Step 2: Run them.** They fail.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** `npm run api:types && npm run lint && npm run format:check && npm run typecheck && npm run test:coverage && npm run build`. Expected: all green.
- [ ] **Step 5: Commit.** `feat(frontend): administración de años del cuestionario`

### Task 10: Question tree — edit, reorder, add, deactivate, tables (frontend)

**Files:**
- Create: `frontend/src/features/admin-questions/QuestionTreePage.tsx`, `QuestionEditor.tsx`, `AddQuestionDialog.tsx`, `ReorderList.tsx`, `TablesMappingPanel.tsx`, plus a `*.test.tsx` for each

**Interfaces:**
- Consumes: `admin-api.ts` (Task 9) and the `AdminQuestionnaireView` contract (Task 4).
- **Layout:** the tree is page → entries, with each entry showing numeral, column, type badge, column status (Activa / Pendiente de columna, as text, not color only) and "Usada por N reglas".
- **Published years:** they show a read-only notice, and all edit controls are absent.
- **Editing text:** inline, with an explicit "Guardar" (no autosave). The client sends the `version` it last read. A 409 shows "Otro usuario modificó el cuestionario. Recargue la página." with a "Recargar" button.
- **Reorder:** drag-and-drop and keyboard. Each item has "Subir" and "Bajar" buttons with `aria-label` "Subir C07A" and so on, and an `aria-live` announcement "C07A ahora es la pregunta 5 de 12". The skip warnings returned by the server are listed under the page.
- **Add question:** a dialog with the fields of `NewQuestionRequest`. The option group is a select of the existing groups, taken from the tree's options. A pending result shows "Descargar script de columna".
- **Deactivate:** asks for confirmation. A 409 lists the referencing rule ids.
- **Tables panel:** chapter → table inputs, with a `missing` badge and "Descargar script CREATE TABLE".
- **Rendering:** all text is rendered as text, never as HTML.

- [ ] **Step 1: Write the failing tests.**
  - `editsTextWithVersionAndHandlesConflict`.
  - `textIsStoredPlainAndRenderedEscaped`: a `<script>` text renders literally.
  - `reorderByKeyboardAnnouncesPosition`.
  - `addQuestionShowsPendingAndScriptLink`.
  - `deactivateShowsReferencingRules`.
  - `publishedYearIsReadOnly`.
  - `tablesPanelFlagsMissing`.
- [ ] **Step 2: Run them.** They fail.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run the frontend checks.** Expected: all green.
- [ ] **Step 5: Commit.** `feat(frontend): árbol del cuestionario con edición, orden y preguntas nuevas`

### Task 11: E2E of the administrator, and documentation

**Files:**
- Create: `frontend/e2e/admin-cuestionario.spec.ts`
- Modify: `README.md` (Usage, Environment Variables if any, technical debt), `CHANGELOG.md`, `docs/diagramas/` (one sequence diagram for "crear año" and one for "editar con copia al escribir", in Mermaid, render-checked), and `docs/superpowers/specs/2026-10-26-fase-3-administracion-design.md` (a "Desviaciones de implementación (3a)" section, if any)

**Interfaces:**
- Consumes: the whole of Tasks 1–10 and the local stack.

E2E against the local stack, logged in as fictitious admin `adm.nacional`:
1. Create 2027 from 2026.
2. Edit a text of C1 and save.
3. Reorder two items with the keyboard.
4. Add a question without a column, see it pending, and download its script.
5. Deactivate the new question.
6. Open the 2026 tree and verify it is read-only and its C1 text unchanged.
7. Run axe (WCAG 2.1 AA) on the years page and the tree.

Because the run creates 2027, it starts with a reset step. The README documents it: restarting the backend reloads `data.sql` and removes `RULES_DIR/EDIN-2027.json`.

- [ ] **Step 1: Write the E2E.**
- [ ] **Step 2: Run it against the local stack.** Expected: green on both Playwright projects. The flow uses a different year per project: 2027 for chromium and 2028 for movil, from 2026.
- [ ] **Step 3: Update the docs.**
- [ ] **Step 4: Final verification.** Run the backend `./mvnw -B verify`, the frontend checks plus `build`, and gitleaks.
- [ ] **Step 5: Commit.** `docs: administración de preguntas y años (Fase 3a)`
