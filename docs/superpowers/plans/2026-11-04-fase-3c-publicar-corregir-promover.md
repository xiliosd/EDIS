# Fase 3c — Publicar, corregir en recolección y promover a producción: plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Three things become possible:
- publishing a draft year through an explicit checklist that locks it;
- correcting texts, skips and validations of a published year with a mandatory reason, without touching what was already collected;
- taking a published year or a correction from QA to production as a verifiable package.

Every version keeps a browsable history.

**Architecture:**
- **Rules file.** The year's rules file (`RULES_DIR`) keeps being the single source of state. It gains `publicacion` (who, when, which version, which warnings) and `ultimoCambio` (who, when, operation, reason).
- **Publication and corrections.** `PublicationService` reruns the 3b analysis and case runner as a checklist before switching `estado`. Edits on a published year go through a new `YearCatalogService.requireEditable(survey, year, EditKind)` guard: corrections are allowed and require a reason; structural edits are not.
- **Promotion package.** `PromotionPackageService` snapshots the year's `FRM_*` closure and writes four files into a ZIP:
  - an idempotent PL/SQL `frm.sql`;
  - `columnas.sql`;
  - `reglas.json`;
  - `manifiesto.json` with counts and SHA-256 sums.

  In production, `PackageImportService` checks the ZIP against itself and against the real database, shows the impact, and only then writes the rules file. The application never writes `FRM_*` there.

**Tech Stack:** the same as 3a and 3b, with no new dependencies. ZIP uses `java.util.zip` and SHA-256 uses `java.security.MessageDigest`.
- Backend: Spring Boot 4.1.1, Java 21, Jackson 3, `NamedParameterJdbcTemplate`, Testcontainers Oracle Free.
- Frontend: React 19.3, React Router 7 data router, TanStack Query 5, RHF 7, Vitest 4 + MSW 2, Playwright 1.63 + axe.

**Spec:** `docs/superpowers/specs/2026-10-26-fase-3-administracion-design.md`, sections:
- §2, objectives 6–8, and the anti-objectives;
- §3, steps 3–5;
- §7 and §8;
- §9 (Publicar, Historial, Paquetes);
- §10 and §11.

The 3a and 3b deviation sections still apply, and so do plans `2026-10-26-fase-3a-…` and `2026-11-03-fase-3b-…`.

## Global Constraints

- **No new Oracle tables and no DDL from the application (D3, D4).** Scripts are text for the technical person.
- **Production database access.** The application **only reads** `FRM_*` in production. Package import never executes `frm.sql` or `columnas.sql`.
- **Version check, as in 3a/3b.** Every write carries the `version` that was read (in the body, or in `?version=` for DELETE). A mismatch returns 409 "Otro usuario modificó el cuestionario. Recargue la página." Writes run under `SaveLocks.withAdminLock`, and the response is built inside the lock.
- **Structural edits.** Add, deactivate, reorder and tables mapping are allowed only in `estado: "borrador"`. On a published year they return 409 "El cuestionario de AAAA está publicado: solo se permiten correcciones de textos, saltos y validaciones."
- **Corrections** are text, header and help edits, and skip, validation and case edits, on a published year.
  - Each one requires `motivo`, trimmed to 10–500 characters, or 422 "Indique el motivo de la corrección (entre 10 y 500 caracteres).".
  - The reason is stored only in the rules file (`ultimoCambio.motivo`), never in logs.
  - The UI warns "No escriba datos personales en el motivo.".
- **Audit.** Operations are fixed keys:
  - `publicar`;
  - `importar-paquete`;
  - `restaurar-version`;
  - `corregir-` plus the existing operation (for example `corregir-editar-texto` and `corregir-editar-salto`).

  Codes follow `^[A-Za-z0-9_-]{1,40}$`, as in 3a.
- **Publish gate.** Publishing requires all of the following:
  - zero analysis errors;
  - every case `pasa`, so `falla` and `por_revisar` both block;
  - every active question with its column (`ACTIVE`);
  - every mapped table valid per `AnswerTableResolver.check`.

  Warnings never block. Their codes and elements are recorded in `publicacion.avisos`.
- **Corrections never reopen or recompute saved pages (D16).** The impact shows counts only and never `C_C`.
- **Respondent flow.** It is unchanged:
  - `SURVEY_YEAR` still chooses the active year;
  - `GET /api/v1/survey/rules` keeps returning `RespondentRules` without the new fields;
  - publishing the active year lifts the 503 gate of 3a deviation 9 by itself.
- **Git and quality:**
  - commits go directly on `main` (personal-project exception), in Spanish Conventional Commits with `Co-Authored-By` naming the real model;
  - implementers do not push, and the controller pushes after the final review;
  - coverage ≥ 80 % on `rules`, `admin` and `security`, and ≥ 60 % elsewhere;
  - WCAG 2.1 AA;
  - OpenAPI regenerated through `OpenApiExportIT` plus `npm run api:types`, with explicit unique `operationId`s;
  - gitleaks never bypassed;
  - fictitious data only.

## Review Focus

1. **Publishing while a case is `falla` or `por_revisar`, or while a column is pending.** The response is 422 with the failing checklist items, and the year stays `borrador` at the same version. Test `publishBlockedByRedOrUnreviewedCaseOrPendingColumn` (Task 2).
2. **A structural edit on a published year.** It returns 409 even when a reason is sent, and a correction without a reason returns 422. In both cases the file and the `FRM_*` rows stay unchanged. Test `publishedYearAllowsOnlyCorrectionsWithReason` (Task 3).
3. **`frm.sql` run twice, or against a database where an id is already used by a different row.** The second run changes nothing. A year package stops at the occupied id with `ORA-20001` and leaves nothing written. A correction package updates the differing rows. Test `frmScriptIsIdempotentAndStopsOnOccupiedId` (Task 5), which runs the script against the Testcontainers Oracle.
4. **Importing a package into a database that does not match it.** This covers `frm.sql` not run, a row edited by hand, and a missing column. The response is 422 listing every differing table or column, and the rules file and its version stay unchanged. Test `importRejectsWhenDatabaseDiffersFromManifest` (Task 6).
5. **A tampered or hostile ZIP.** This covers a `reglas.json` edited after generation, an extra or missing entry, an entry name with `../`, and a ZIP above 5 MB or above 20 MB uncompressed. The response is 422 (413 for size) and nothing is extracted to disk. Test `importRejectsTamperedOrHostileZip` (Task 6).

---

## File Structure

```
backend/src/main/java/co/gov/dane/edi/
  rules/
    Publication.java          record: fecha, usuario, version, avisos (List<String> "CODE@element")
    ChangeNote.java           record: usuario, fecha, operacion, motivo (nullable)
    RuleSet.java              (modify) + publicacion, ultimoCambio; withPublication
    RulesFileRepository.java  (modify) save(..., reason) stamps ultimoCambio; history(); historyVersion(); replaceWithPackage()
    HistoryEntry.java         record for one stored version
  admin/
    EditKind.java             enum STRUCTURAL, CORRECTION
    YearCatalogService.java   (modify) requireEditable(survey, year, kind)
    PublicationService.java   checklist + publish
    HistoryService.java       list, read, restore
    FrmSnapshotRepository.java   year closure of FRM_* rows (read-only)
    FrmScriptWriter.java      idempotent PL/SQL text from a snapshot
    PromotionPackageService.java build ZIP
    PackageImportService.java    verify + preview impact + confirm
    controller/PublicationAdminController.java, HistoryAdminController.java, PackageAdminController.java
    dto/PublicationChecklist.java, ChecklistItem.java, HistoryView.java, ImportPreview.java,
        PageImpact.java, PublishRequest.java, RestoreRequest.java
  (modify) QuestionAdminService, RulesAdminService, AnswerTableMappingService: guards + motivo
frontend/src/
  features/admin-publish/   publish-api.ts, PublishPage.tsx, ChecklistView.tsx
  features/admin-history/   history-api.ts, HistoryPage.tsx, version-diff.ts
  features/admin-packages/  packages-api.ts, PackagesPage.tsx, ImportPreviewView.tsx
  components/CorrectionReasonBar.tsx   shared "motivo" field for corrections
  components/QuestionnaireTabs.tsx     (modify) + Publicar, Historial, Paquetes
frontend/e2e/admin-publicar.spec.ts
docs/diagramas/secuencia-publicar.md, secuencia-promover.md; README, CHANGELOG, spec "Desviaciones (3c)"
```

---

### Task 1: Publication and change metadata in the rules file, and history access

**Files:**
- Create: `rules/Publication.java`, `rules/ChangeNote.java`, `rules/HistoryEntry.java`
- Modify: `rules/RuleSet.java`, `rules/RulesFileRepository.java`, `rules/EffectiveRulesService.java` (`merge` keeps the new fields)
- Test: `RuleSetJsonTest`, `RulesFileRepositoryTest`

**Interfaces:**
- Produces:
  - `record Publication(Instant publishedAt, String user, int version, List<String> warnings)`. Its JSON names are `fecha`, `usuario`, `version` and `avisos`. Each entry of `avisos` is `CODE@element`, for example `NO_ANALIZABLE@regla:f04-alguna-si`.
  - `record ChangeNote(String user, Instant at, String operation, String reason)`. Its JSON names are `usuario`, `fecha`, `operacion` and `motivo`, and `motivo` is omitted when null.
  - `RuleSet` gains two last components, both `NON_NULL`: `@JsonProperty("publicacion") Publication publication` and `@JsonProperty("ultimoCambio") ChangeNote lastChange`. It also gains `withPublication(Publication)`. Every existing `with*` method and every constructor keeps them. Remove the trap from 3b: make the 8-argument constructor delegate with the existing `cases`. If that is impossible, delete it and update its callers.
  - `RulesFileRepository`:
    - `save(rules, expectedVersion, user, operation, codes, String reason)` stamps `ultimoCambio = ChangeNote(user, clock.instant(), operation, reason)`. The existing 5-argument `save` delegates with a null reason. Use the existing `Clock` bean (`config/ClockConfig`).
    - `List<HistoryEntry> history(String survey, int year)`. Each entry is `HistoryEntry(int version, Instant savedAt, String user, String operation, String reason, boolean current)`, read from the history copies and the current file, ordered newest first. Copies that fail to parse are skipped with a WARN log line that never contains the content.
    - `Optional<RuleSet> historyVersion(String survey, int year, int version)`.
    - `RuleSet replaceWithPackage(RuleSet incoming, String user)`. It keeps `incoming.version()` as is, so the same version number exists in QA and production. It copies the current file to history, stamps `ultimoCambio` with the operation `importar-paquete`, and audits. It refuses with 409 "La versión del paquete (N) no es posterior a la de este ambiente (M)." when `incoming.version() <= current`. The exception is when the content is identical: then it returns the current file unchanged, so importing twice is a no-op.

- [ ] **Step 1: Write the failing tests**
  - `publicationAndLastChangeRoundTripAndAreOmittedWhenNull`.
  - `withersKeepPublicationAndLastChange`, which extends the 3b `withersKeepCases`.
  - `saveStampsLastChangeWithClockAndReason`, using a fixed `Clock`.
  - `historyListsVersionsNewestFirstWithReasons`: three saves with reasons give versions 3, 2 and 1, and only 3 has `current = true`.
  - `historyVersionReadsAnOldCopy`.
  - `replaceWithPackageKeepsIncomingVersion`, `replaceWithPackageRejectsOlderVersion` and `replaceWithPackageIsNoOpWhenIdentical`.
  - `respondentRulesNeverExposePublicationOrLastChange`, in `SurveyRulesIT`.
- [ ] **Step 2: Run them and confirm they fail**
  - Run: `./mvnw -q test -Dtest='RuleSetJsonTest,RulesFileRepositoryTest'`.
  - Expected: they do not compile.
- [ ] **Step 3: Implement**
- [ ] **Step 4: Verify**
  - Run: `./mvnw -q verify`, with the short `TEMP` on Windows.
  - Expected: green.
- [ ] **Step 5: Commit** with the message `feat(backend): publicación, último cambio e historial en el archivo de reglas`

---

### Task 2: Publication checklist and publish

**Files:**
- Create: `admin/PublicationService.java`, `admin/controller/PublicationAdminController.java`, `admin/dto/PublicationChecklist.java`, `ChecklistItem.java`, `PublishRequest.java`
- Test: `admin/PublicationIT.java`

**Interfaces:**
- Consumes:
  - from 3b: `RuleSetAnalyzer.analyze`, `CaseRunner.runAll` and `QuestionFactsService.facts`;
  - from 3a: `AnswerTableResolver.check` and the `ColumnScriptService` column script;
  - from Task 1: `RulesFileRepository.save(…, reason)` and `withPublication`.
- Produces:
  - `record ChecklistItem(String code, boolean passed, String detail, List<String> elements)`. `code` is one of `SIN_ERRORES`, `CASOS_EN_VERDE`, `COLUMNAS_CREADAS` or `TABLAS_VALIDAS`. `detail` is a Spanish sentence:

    | `code` | `detail` when it passes | `detail` when it fails |
    | :-- | :-- | :-- |
    | `SIN_ERRORES` | "El análisis no tiene errores." | "El análisis tiene N errores." |
    | `CASOS_EN_VERDE` | "Los N casos de prueba pasan." | "Hay casos que fallan o están por revisar." |
    | `COLUMNAS_CREADAS` | — | "Faltan N columnas; descargue el script." |
    | `TABLAS_VALIDAS` | — | "Hay tablas del mapeo que no existen o no son válidas." |

    `CASOS_EN_VERDE` passes with zero cases, and then its detail is "No hay casos de prueba." `elements` lists the offending elements (rule ids, case ids, columns or chapters), capped at 50.
  - `record PublicationChecklist(int version, boolean ready, List<ChecklistItem> items, List<String> warnings)`.
  - `GET /api/v1/admin/questionnaire/{encuesta}/{anio}/publication` returns the `PublicationChecklist`, for any state. On a published year it is informative.
  - `POST /api/v1/admin/questionnaire/{encuesta}/{anio}/publication` with body `PublishRequest(Integer version)`:
    - It runs under the admin lock with `requireDraft` and the version check, and recomputes the checklist inside the lock.
    - If the checklist is not ready, it responds 422 "El cuestionario no cumple las condiciones para publicarse." with a `checklist` property.
    - Otherwise it saves `withState(PUBLICADO).withPublication(Publication(now, user, newVersion, warnings))`, where `newVersion` is the version saved by that same save. The operation is `publicar` and the codes are empty. It evicts the `effectiveRules`, questionnaire and tables caches.
    - It returns the new `PublicationChecklist`.

- [ ] **Step 1: Write the failing tests** in `PublicationIT`, using `DraftYearFixture`:
  - `checklistOfCleanDraftIsReady`;
  - `publishBlockedByRedOrUnreviewedCaseOrPendingColumn`: three sub-cases, each asserting 422, the failing item and that the file is unchanged;
  - `publishSwitchesStateRecordsWarningsAndLiftsSurveyGate`: publishing the active draft year makes `/api/v1/survey/chapters` 200 again for a respondent;
  - `publishedYearCannotBePublishedAgain` (409);
  - `staleVersionGets409`;
  - `nonAdminGets403AndCsrfRequired`.
- [ ] **Step 2: Run them and confirm they fail.**
- [ ] **Step 3: Implement.** Explicit `operationId`s: `publicationChecklist` and `publishYear`.
- [ ] **Step 4: Run `./mvnw -q verify`, then `npm run api:types` and `npx tsc --noEmit`.**
- [ ] **Step 5: Commit** with the message `feat(backend): lista de comprobación y publicación del año`

---

### Task 3: Corrections on a published year

**Files:**
- Create: `admin/EditKind.java`
- Modify:
  - `admin/YearCatalogService.java`;
  - `admin/QuestionAdminService.java`;
  - `admin/RulesAdminService.java`;
  - `admin/AnswerTableMappingService.java`;
  - the request DTOs that carry `version`: `EditTextRequest`, the header and help requests, `SkipRuleRequest`, `ValidationRequest`, `TestCaseRequest` and `ImportRulesRequest`, which gain an optional `String motivo`;
  - the DELETE endpoints of rules, validations and cases, which gain an optional `?motivo=`.
- Test: `admin/CorrectionIT.java`

**Interfaces:**
- Produces:
  - `enum EditKind { STRUCTURAL, CORRECTION }`.
  - `YearCatalogService.requireEditable(String survey, int year, EditKind kind, String reason): String`. It returns the normalized reason, or null for a draft.
    - Draft: any kind passes, and the reason is ignored and returns null.
    - Published + `STRUCTURAL`: 409 with the Global Constraints message.
    - Published + `CORRECTION`: requires a valid reason (422 otherwise) and returns it trimmed.
- Mapping. `CORRECTION` covers:
  - text, header and help edits;
  - skip, validation and case save and delete;
  - import of rules.

  `STRUCTURAL` covers add, deactivate, reorder, tables mapping and column script changes. Every existing `requireDraft` call in these services becomes `requireEditable` with the right kind.
- A correction:
  - saves with operation `corregir-<op>` and the reason through `save(…, reason)`;
  - keeps the 3b rule "no new errors". Because a published year had zero errors, this means a correction can never introduce an error;
  - is rejected with 422 "La corrección hace fallar casos de prueba: …" (listing the ids) if any case that was `pasa` before becomes `falla`;
  - may leave cases `por_revisar`, for example after a text edit.

- [ ] **Step 1: Write the failing tests** in `CorrectionIT`. They use a year published via Task 2:
  - `publishedYearAllowsOnlyCorrectionsWithReason`. Add, deactivate, reorder and table mapping each return 409 even with a reason. A text edit without a reason returns 422. A text edit with a reason returns 200, version + 1, and `ultimoCambio` holding the reason and `corregir-editar-texto`. In every rejected case, `FRM_*` counts and the version stay unchanged.
  - `correctionCannotIntroduceErrors`.
  - `correctionThatTurnsACaseRedIsRejected`.
  - `reasonNeverReachesTheLogs`. Capture the `AdminAudit` log output (or the appender used by its existing test) and assert the reason text is absent.
  - `draftEditsStillNeedNoReason`, a regression test for 3a and 3b.
- [ ] **Step 2: Run them and confirm they fail.**
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run `./mvnw -q verify`, then `npm run api:types && npx tsc --noEmit && npm test`.** Frontend callers keep compiling because `motivo` is optional.
- [ ] **Step 5: Commit** with the message `feat(backend): correcciones con motivo sobre un año publicado`

---

### Task 4: History, differences and restore

**Files:**
- Create: `admin/HistoryService.java`, `admin/controller/HistoryAdminController.java`, `admin/dto/HistoryView.java`, `RestoreRequest.java`
- Test: `admin/HistoryIT.java`

**Interfaces:**
- Produces:
  - `GET …/{encuesta}/{anio}/history` returns `HistoryView(int version, YearState state, List<HistoryEntry> entries)`.
  - `GET …/history/{version}` returns the stored `RuleSet` of that version (404 "La versión no existe.").
  - `POST …/history/{version}/restore` with `RestoreRequest(Integer version)`:
    - It is allowed only on a draft. On a published year it returns 409 "Solo se puede restaurar una versión en borrador.".
    - It replaces `saltos`, `validaciones`, `casos`, `tablas` and `columnasNuevas` with those of the chosen version.
    - It keeps `estado` and `publicacion`.
    - It goes through the same 3b analysis gate: new errors give 422.
    - It saves with operation `restaurar-version` and codes `["v<N>"]`.
    - `FRM_*` rows are not restored, and the UI says so.
- The diff between two versions is computed in the frontend (Task 8). No diff endpoint.

- [ ] **Step 1: Write the failing tests:**
  - `historyListsPublishAndCorrectionsWithReasons`;
  - `readingAnOldVersion`;
  - `restoreReplacesRulesButNotStateOrFrm`;
  - `restoreOnPublishedYearIs409`;
  - `restoreThatAddsErrorsIs422`;
  - `missingVersionIs404`.
- [ ] **Step 2: Run them and confirm they fail.**
- [ ] **Step 3: Implement.** Explicit `operationId`s: `historyView`, `historyVersion` and `historyRestore`.
- [ ] **Step 4: Run `./mvnw -q verify`, then `npm run api:types`.**
- [ ] **Step 5: Commit** with the message `feat(backend): historial de versiones y restauración en borrador`

---

### Task 5: Year snapshot and the idempotent `frm.sql`

**Files:**
- Create: `admin/FrmSnapshotRepository.java`, `admin/FrmScriptWriter.java`
- Modify: `admin/FrmTable.java`, which gains `List<String> columns()` with the real column lists of `db/dev/schema.sql` (id first). Those lists are the only columns that enter generated SQL.
- Test: `admin/FrmSnapshotRepositoryIT.java`, `admin/FrmScriptWriterTest.java`, `admin/FrmScriptExecutionIT.java`

**Interfaces:**
- Produces:
  - `record FrmSnapshot(String project, int year, Map<FrmTable, List<Map<String, Object>>> rows)`. Rows are ordered by id, and a column value is a `String`, a `BigDecimal` or null.
  - `FrmSnapshot FrmSnapshotRepository.snapshot(String project, int year)`. It is read-only. It reads the year's closure:
    - `FRM_CAP_PERIODO` by `PROY = ? AND ANIO = ?`;
    - `FRM_CAP_PROY` by those `COD_CAP_PROY`;
    - `FRM_PRE_GRUPO` referenced by them, and `FRM_PREGUNTA` referenced by those groups;
    - `FRM_ENCABEZADO`, `FRM_PRE_AYUDA` and `FRM_OBJ_PRE` by `COD_CAP_PROY`;
    - `FRM_ENC_AYUDA` by `COD_ENC`.

    It uses the same joins as `YearCopyRepository` and binds every value as a parameter.
  - `static String FrmSnapshot.rowHash(FrmTable table, Map<String,Object> row)`. It is the SHA-256 hex of the canonical form `COL=value` joined with `\u001F`, in `columns()` order. Null is written `\u0000`, and a number uses `BigDecimal.stripTrailingZeros().toPlainString()`.
  - `static String FrmSnapshot.tableSum(List<String> rowHashes)`. It is the SHA-256 of the sorted hashes joined with `\n`.
  - `enum PackageKind { ANIO, CORRECCION }`, with the JSON values `anio` and `correccion`.
  - `String FrmScriptWriter.write(FrmSnapshot snapshot, PackageKind kind)`. The script:
    - starts with `SET DEFINE OFF`, `WHENEVER SQLERROR EXIT ROLLBACK` and a comment header with survey, year and table counts;
    - has then one anonymous PL/SQL block per row, in dependency order (`PREGUNTA`, `PRE_GRUPO`, `CAP_PROY`, `CAP_PERIODO`, `ENCABEZADO`, `PRE_AYUDA`, `OBJ_PRE`, `ENC_AYUDA`);
    - ends with `COMMIT;`.

    Each row's block works like this:
    1. It counts the rows with the same id.
    2. With 0, it runs `INSERT`.
    3. With 1, it compares every column with null-safe equality (`(a = b OR (a IS NULL AND b IS NULL))`). If equal, it does nothing. If different: an `ANIO` package does `RAISE_APPLICATION_ERROR(-20001, 'Id ocupado: <TABLE> <ID>=<value>')`, and a `CORRECCION` package does an `UPDATE` of the non-id columns.

    Literals:
    - strings are single-quoted with `'` doubled;
    - newlines are kept;
    - a string above 4000 bytes is impossible by the 3a cap, but assert it anyway.

    Identifiers come only from `FrmTable`.

- [ ] **Step 1: Write the failing tests:**
  - `FrmSnapshotRepositoryIT.snapshotOfDevYearCoversTheClosure`, checking the counts per table for 2026 EDIN.
  - `FrmSnapshotRepositoryIT.hashesAreStableAcrossReads`.
  - `FrmScriptWriterTest.escapesQuotesAmpersandsAndNewlines`: a text `O'Brien & <b>\nX` appears as a valid literal, and the script starts with `SET DEFINE OFF`.
  - `FrmScriptWriterTest.yearPackageRaisesOnDifferentRowCorrectionUpdates`.
  - `FrmScriptExecutionIT.frmScriptIsIdempotentAndStopsOnOccupiedId`. It runs against Testcontainers Oracle and executes each PL/SQL block through JDBC, skipping the SQL*Plus directives:
    1. Snapshot 2027 (created via `YearCopyService`), delete the 2027 rows, run the script and check that the snapshot equals the original.
    2. Run it again and check that nothing changes.
    3. Change one `FRM_ENCABEZADO.ENCABEZADO`. The `ANIO` script must fail with `ORA-20001` and roll back, leaving all counts unchanged. The `CORRECCION` script must restore the text.
- [ ] **Step 2: Run them and confirm they fail.**
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run `./mvnw -q verify`.**
- [ ] **Step 5: Commit** with the message `feat(backend): fotografía FRM del año y script idempotente para producción`

---

### Task 6: Promotion package — build and import

**Files:**
- Create:
  - `admin/PromotionPackageService.java`;
  - `admin/PackageImportService.java`;
  - `admin/controller/PackageAdminController.java`;
  - `admin/dto/ImportPreview.java`, `PageImpact.java`, `PackageManifest.java`.
- Test: `admin/PromotionPackageIT.java`, `admin/PackageImportIT.java`

**Interfaces:**
- Consumes: Task 1 (`replaceWithPackage`), Task 5, `ColumnScriptService`, and `AnswerTableResolver`.
- Produces:
  - **`PackageManifest`** (JSON `manifiesto.json`) carries:
    - `encuesta`, `anio`, `version`;
    - `tipo` (`anio` or `correccion`);
    - `generado` (Instant) and `generadoPor`;
    - `tablas`: `{ "FRM_X": { "filas": n, "suma": hex } }`;
    - `columnasRequeridas`: `{ "TABLA": ["COL", …] }`, holding the active questions' columns per mapped table;
    - `archivos`: `{ "reglas.json": hex, "frm.sql": hex, "columnas.sql": hex }`, the SHA-256 of each file's bytes.
  - **`GET …/{encuesta}/{anio}/package?tipo=anio|correccion`** returns `application/zip`, named `<ENC>-<AAAA>-v<N>-<tipo>.zip`, with exactly the four entries `reglas.json`, `frm.sql`, `columnas.sql` and `manifiesto.json`.
    - It requires a published year, or 409 "Solo se generan paquetes de un año publicado.".
    - `correccion` additionally requires `version > publicacion.version`, or 409 "No hay correcciones posteriores a la publicación.".
    - After those checks, the year must still pass the publication checklist (the same one the import runs), or 422 "El año no pasa la lista de publicación: corríjalo antes de generar el paquete." with the `checklist` property. A case left `porRevisar` by a correction therefore blocks the package in QA instead of at import in production.
    - `columnas.sql` holds the 3a column script plus `CREATE TABLE` for missing mapped tables. It is empty with a comment when nothing is missing.
    - The audit operation is `generar-paquete`.
  - **`POST …/{encuesta}/{anio}/package/import`** is multipart with a `archivo` part and `confirmar=false|true`. The limits are 5 MB for the request, with 413 "El paquete supera 5 MB.", and 20 MB uncompressed in total. The import runs these checks in this order, each failing with 422 and a Spanish message plus a `differences` list:
    1. The ZIP is exactly the four entry names, with no directories and no `..`.
    2. The SHA-256 of each file equals the manifest.
    3. `reglas.json` parses, passes `RuleSetValidator` and has `estado: publicado`, and its `encuesta`, `anio` and `version` match the manifest and the path.
    4. `FrmSnapshotRepository.snapshot` in this database gives, for every table, the manifest's `filas` and `suma`. Any difference is reported per table.
    5. Every `columnasRequeridas` column exists in `ALL_TAB_COLUMNS`, and every mapped table passes `AnswerTableResolver.check`.
    6. The version is newer than this environment's, or identical with the same content, which gives "El paquete ya estaba importado.".

    It then computes `ImportPreview(int version, String kind, List<PageImpact> impact, boolean alreadyImported)`.
    - `PageImpact(String pageCode, long savedCount)` covers each page that has a column which is the trigger or target of an added, removed or changed skip, or whose validation changed, between this environment's current file and the package.
    - `savedCount` is the number of rows in the page's chapter table whose first active item column is not null, which is the same "saved" rule as `SurveyProgressService`.
    - No `C_C` and no answer values ever leave the database.

    With `confirmar=true` and every check passing, it calls `replaceWithPackage` (operation `importar-paquete`) and evicts all caches (`questionnaire`, `effectiveRules`, `tables`, `AnswerColumnRepository`). The ZIP is processed in memory and nothing is written to disk except the rules file.

- [ ] **Step 1: Write the failing tests:**
  - `PromotionPackageIT.yearPackageHasFourFilesWithMatchingSums`.
  - `PromotionPackageIT.correctionPackageNeedsALaterVersion`.
  - `PromotionPackageIT.draftYearHasNoPackage`.
  - `PackageImportIT.importRejectsWhenDatabaseDiffersFromManifest`. Generate the package, then change a `FRM_PRE_AYUDA.TEXTO` and drop a required column through a test-only DDL on the container. Expect 422 listing `FRM_PRE_AYUDA` and the column, with the rules file unchanged.
  - `PackageImportIT.importRejectsTamperedOrHostileZip`, with six sub-cases:
    - edited `reglas.json` (422);
    - extra entry (422);
    - missing entry (422);
    - `../x` entry (422);
    - more than 5 MB (413);
    - a ZIP bomb with more than 20 MB uncompressed (422).
  - `PackageImportIT.previewShowsImpactCountsWithoutRespondentCodes`. Seed two fictitious respondents with page C1 saved, change a skip on C1, expect `C1 → 2`, and assert the response body contains no `C_C` value.
  - `PackageImportIT.confirmWritesRulesKeepsVersionAndEvicts`.
  - `PackageImportIT.reimportIsANoOp`.
  - `PackageImportIT.olderPackageIs409`.
- [ ] **Step 2: Run them and confirm they fail.**
- [ ] **Step 3: Implement.** The explicit `operationId`s are `packageDownload` and `packageImport`. Set the multipart limits in `application.yml` only for this endpoint if possible, or globally to 5 MB.
- [ ] **Step 4: Run `./mvnw -q verify`, then `npm run api:types`.**
- [ ] **Step 5: Commit** with the message `feat(backend): paquete de promoción verificable e importación en producción`

---

### Task 7: Frontend — Publicar page and correction mode

**Files:**
- Create:
  - `features/admin-publish/publish-api.ts`, `PublishPage.tsx`, `ChecklistView.tsx`;
  - `components/CorrectionReasonBar.tsx`.
- Modify:
  - `components/QuestionnaireTabs.tsx`, adding the Publicar, Historial and Paquetes tabs;
  - `App.tsx`, adding the routes `…/publicar`, `…/historial` and `…/paquetes`. Historial and Paquetes are placeholders until Task 8;
  - the 3a and 3b editors: `QuestionnaireTreePage`, `QuestionEditor`, `RulesPage`, `SimulatorPage` and the API clients, which send `motivo` when the year is published.
- Test: `PublishPage.test.tsx`, `CorrectionReasonBar.test.tsx`, and updates to the existing editor tests.

**Behavior:**
- **`PublishPage`:**
  - It shows `ChecklistView`: each item with a pass or fail icon plus text, the detail, and links to the offending elements:
    - a rule → `…/reglas#regla-<id>`;
    - a case → `…/simulador#caso-<id>`;
    - a column → `…/cuestionario` with the column script download;
    - a chapter → the tables panel.
  - The warnings are listed.
  - "Publicar" is enabled only when `ready` and the year is a draft. It asks for confirmation with `ConfirmDialog`: "Publicar bloquea la edición estructural del año AAAA. ¿Continuar?".
  - A 422 re-renders the returned checklist with focus on the summary.
  - On a published year the page shows "Publicado el <fecha> por <usuario>, versión N" and the recorded warnings.
- **`CorrectionReasonBar`:**
  - It appears on Preguntas, Reglas and Simulador when `estado` is `publicado`. It contains a required `textarea` labeled "Motivo de la corrección", 10–500 characters, with the hint "No escriba datos personales en el motivo.".
  - Saves are disabled until the reason is valid, and every correction request carries it.
  - Structural controls (add, deactivate, reorder, tables) stay disabled on a published year, with the text "Solo en borrador.".
  - The reason is kept per page in memory and cleared after each successful save.

- [ ] **Step 1: Write the failing tests:**
  - the checklist renders pass and fail with text, and its links point to the right anchors;
  - publish is disabled when not ready; confirm, then the API is called with the version; a 422 re-renders the checklist;
  - on a published year, saves are blocked without a reason and send the trimmed `motivo` once it is valid;
  - structural buttons are disabled on a published year;
  - `RequireRole` guards the new routes.
- [ ] **Step 2: Run the tests to confirm they fail.**
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run `npm run lint && npx tsc --noEmit && npm test && npx prettier --check .`.**
- [ ] **Step 5: Commit** with the message `feat(frontend): publicar el año y modo corrección con motivo`

---

### Task 8: Frontend — Historial and Paquetes

**Files:**
- Create:
  - `features/admin-history/history-api.ts`, `HistoryPage.tsx`, `version-diff.ts`;
  - `features/admin-packages/packages-api.ts`, `PackagesPage.tsx`, `ImportPreviewView.tsx`.
- Test: `version-diff.test.ts`, `HistoryPage.test.tsx`, `PackagesPage.test.tsx`

**Interfaces:**
- Produces: `versionDiff(older: RuleSet, newer: RuleSet): { skips: Change[]; validations: Change[]; cases: Change[]; tables: Change[] }`.
  - `Change` is `{ id: string; kind: 'agregada' | 'eliminada' | 'cambiada'; before?: string; after?: string }`.
  - Skips are described with `describeRule` (3b). Validations, cases and tables are compared by key with stable JSON.

**Behavior:**
- **`HistoryPage`:**
  - It shows a table of versions with these columns: version, fecha, usuario, operación, motivo, actual.
  - Choosing two versions shows their `versionDiff`.
  - "Restaurar esta versión" is available only on a draft. It asks for confirmation and says "Los textos y preguntas (tablas FRM_*) no se restauran.".
  - A 422 from the restore shows the 3b analysis summary.
- **`PackagesPage`:**
  - "Descargar paquete del año" and "Descargar paquete de corrección" call `download-file`. The correction button is disabled when there is no later version.
  - The import is a file input (`.zip`, ≤ 5 MB checked on the client) followed by "Verificar". `confirmar=false` shows `ImportPreviewView`:
    - the impact table, with the page and "N encuestados ya guardaron esta página con la regla anterior.";
    - or the list of differences on a 422.
  - Then "Importar" (`confirmar=true`), after a confirmation.
  - The page says "Ejecute primero columnas.sql y frm.sql con su usuario de base de datos.".

- [ ] **Step 1: Write the failing tests:**
  - `versionDiff` on added, removed and changed skips, validations and cases;
  - history restore is disabled on a published year;
  - the import preview renders the impact sentence exactly;
  - the 422 differences list;
  - import requires a preview first;
  - the client rejects a file larger than 5 MB.
- [ ] **Step 2: Run the tests to confirm they fail.**
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run `npm run lint && npx tsc --noEmit && npm test && npx prettier --check .`.**
- [ ] **Step 5: Commit** with the message `feat(frontend): historial de versiones y paquetes de promoción`

---

### Task 9: E2E, documentation and spec deviations

**Files:**
- Create: `frontend/e2e/admin-publicar.spec.ts`, `docs/diagramas/secuencia-publicar.md`, `docs/diagramas/secuencia-promover.md`
- Modify:
  - `README.md`, keeping the eleven sections in order. Add the API references, the usage of publish, corrections, history and packages, and the step-by-step production procedure;
  - `CHANGELOG.md`;
  - the spec, adding a section "Desviaciones de implementación (3c)".

**E2E** runs in Chromium. Mobile is skipped with a visible annotation, as in 3b. The scenario:
1. Create 2027 from 2026 on a fresh backend. Edit the text of C01A, which marks the copied case `c01a-no-oculta-c01b` as `por_revisar`. Publicar shows `CASOS_EN_VERDE` failing, with a link to the case. Open it in the simulator and save it again so it passes. Publicar is now ready: publish.
2. On the published year, adding a question is disabled. Correct a text with the reason "Corrección de prueba ficticia" and see version + 1 in Historial with that reason.
3. Paquetes: download the correction package (check the file name), import it in the same environment with `confirmar=false`, and see "El paquete ya estaba importado.". The application must not need production for this.
4. Run axe on Publicar, Historial and Paquetes.

**Docs:**
- Two Mermaid diagrams: publish (lock → checks → state change), and promote (QA package → DBA runs scripts → import verify → preview → confirm).
- In the deviations section:
  - reason only in the file;
  - corrections cannot turn a case red;
  - restore does not restore `FRM_*`;
  - impact counts only rule changes, not text changes;
  - `frm.sql` semantics per kind;
  - same version number in QA and production;
  - ZIP limits;
  - anything else decided during execution.

- [ ] **Step 1: Write the spec and the docs.**
- [ ] **Step 2: Run `npx playwright test e2e/admin-publicar.spec.ts --project=chromium` on a fresh stack, then the full `npm run e2e`.** Expected: green, or visibly skipped only.
- [ ] **Step 3: Run `./mvnw -q verify`, `npm run lint && npx tsc --noEmit && npm test` and `npx prettier --check .`.**
- [ ] **Step 4: Commit** with the message `docs: publicación, correcciones y paquetes de promoción (Fase 3c)`
