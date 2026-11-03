# Fase 3b — Reglas, validación, simulador y casos de prueba: plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The technical person edits skips and validations of a draft year **by question code**. The editor refuses to save the inconsistent rules of spec §5 and explains the logic warnings. The person tries the form in a simulator that never writes answers, and saves test cases. The backend re-runs the cases on every save.

**Architecture:**
- **Analysis:** pure and in the `rules` package.
  - `RuleSetAnalyzer` takes the effective rules of a year (file plus translated legacy skips) and a map of `QuestionFacts` (what each active question is). It returns an `AnalysisReport` with errors and warnings.
  - `CaseRunner` runs one `TestCase` through the existing `RuleEvaluator`.
- **Admin layer:** `QuestionFactsService` builds the facts from `FRM_*` and `ALL_TAB_COLUMNS`. `RulesAdminService` edits the rules file under the admin lock with the version check, analyzes every candidate, and rejects a save that adds errors.
- **Storage:** test cases live in the rules file under `casos`.
- **Frontend:** reuses the respondent question components and the shared TypeScript evaluator for the simulator.

**Tech Stack:** the same as Phase 3a, with no new dependencies.
- Backend: Spring Boot 4.1.1, Java 21, Jackson 3, `NamedParameterJdbcTemplate`, Testcontainers Oracle Free.
- Frontend: React 19.3, React Router 7 data router, TanStack Query 5, RHF 7, Vitest 4 + MSW 2, Playwright 1.63 + axe.

**Spec:** `docs/superpowers/specs/2026-10-26-fase-3-administracion-design.md`, in particular:
- §2, objectives 3–5;
- §3 point 2 and the last bullet of §6, on copying cases;
- §4.1 "Desactivar", because a case also blocks it;
- §4.2, §5, §6, §9 (Reglas, Simulador) and §10;
- "Desviaciones de implementación (3a)", which still apply.

It builds on plan `docs/superpowers/plans/2026-10-26-fase-3a-preguntas-y-anios.md`. Publishing, hot corrections and packages are plan 3c.

## Global Constraints

- **Storage:** no new Oracle tables (D4) and no DDL from the application (D3). Rules, cases and history live in `RULES_DIR` (D5, D6).
- **Editing:** only a year with `estado: "borrador"` accepts rule, validation or case edits. Any other state returns 409 with "El cuestionario de AAAA está publicado y no se puede editar." (the existing `YearCatalogService.requireDraft`).
- **Concurrency:** every edit carries the `version` the client read, in the body or in the `?version=` query of a DELETE, as in 3a. A mismatch returns 409 with "Otro usuario modificó el cuestionario. Recargue la página." Writes run under `SaveLocks.withAdminLock`. The response version and data are built inside the lock.
- **Saving:**
  - `RulesFileRepository.save(rules, expectedVersion, user, operation, codes)`, as in 3a.
  - `operation` is one of `editar-salto`, `borrar-salto`, `editar-validacion`, `borrar-validacion`, `editar-caso`, `borrar-caso`, `importar-reglas`.
  - `codes` are rule ids, column codes or case ids, each matching `^[A-Za-z0-9_-]{1,40}$`.
  - No free text in the audit log.
- **Errors block saving, warnings never do.**
  - A save is rejected with 422 when the candidate's report contains an error that the current file's report does not have. Equality is by `code`, `element` and `questions`.
  - Errors that already exist (for example a copied 2026 file referencing columns absent from the dev questionnaire) are shown but do not block unrelated saves. Plan 3c requires zero errors to publish.
- **Rule ids** match `^[a-z0-9][a-z0-9-]{0,40}$` for new rules. Existing ids such as `legacy-C09G` stay accepted when imported.
- **Case ids** match `^[a-z0-9][a-z0-9-]{0,40}$` and are unique within the file. A case's name is 1–100 characters.
- **Respondent flow (Phase 2):**
  - must behave identically;
  - `GET /api/v1/survey/rules` keeps returning `RespondentRules`, never `casos`;
  - the respondent never sees the simulator.
- **Roles:** every new endpoint is under `/api/v1/admin/questionnaire/**`, which already requires `ADMIN_CUESTIONARIO`, and needs CSRF for state changes.
- **Analysis limit:** enumeration is bounded by `RuleSetAnalyzer.MAX_COMBINATIONS = 4096` per analyzed question or rule. Beyond that, the warning `NO_ANALIZABLE` is emitted instead.
- **Quality:**
  - Conventional Commits in Spanish with `Co-Authored-By` naming the real model;
  - JaCoCo ≥ 80 % lines on `co.gov.dane.edi.rules`, `co.gov.dane.edi.admin` and `co.gov.dane.edi.security`, ≥ 60 % elsewhere;
  - WCAG 2.1 AA, desktop first and usable on tablet;
  - OpenAPI regenerated through `OpenApiExportIT`, then `npm run api:types`;
  - gitleaks pre-commit never bypassed;
  - fictitious data only.
- **Git:** personal-project exception in Oscar's CLAUDE.md. Commit directly on `main`, with no feature branch and no PR. Implementers do not push; the controller pushes `main` after the final review.

## Review Focus

1. **An imported file whose cycle is indirect** (A hides B, and a rule triggered by B hides A's trigger) must give 422 with `CICLO` and leave the file and version unchanged.
   - Test `importWithIndirectCycleIsRejectedAndFileUnchanged` (Task 5).
2. **A rule with 13 two-option combo triggers** (8192 combinations > 4096) must give the warning `NO_ANALIZABLE` within one second and still allow saving.
   - Test `analysisBeyondLimitReportsNotAnalyzableQuickly` (Task 3).
3. **A case recorded in the browser simulator must pass on the backend:** the TypeScript and Java engines must agree.
   - Shared fixture `shared/rules-fixtures/07-caso-simulador.json`, run by `CaseRunnerTest.sharedSimulatorFixturePasses` (Task 4) and `simulator-state.test.ts > fixture 07 matches` (Task 9).
4. **Deactivating a question that only a test case uses** (no rule) must give 409 with the case id listed, and the question must stay active.
   - Test `deactivatingQuestionUsedOnlyByCaseIsRejected` (Task 6).
5. **A saved rule whose page later changes:** text edit or reorder of a question referenced by a case marks that case `porRevisar`. A case that references a question no longer active reports `POR_REVISAR` and is not counted as passed.
   - Tests `editingReferencedQuestionMarksCaseForReview` (Task 6) and `caseReferencingInactiveQuestionNeedsReview` (Task 4).

---

## File Structure

```
backend/src/main/java/co/gov/dane/edi/
  rules/
    TestCase.java              record: id, nombre, pagina, respuestas, esperado, porRevisar
    ExpectedOutcome.java       record: ocultas, errores
    RuleSet.java               (modify) + casos; withSkips/withValidations/withCases
    RuleSetValidator.java      (modify) problems(RuleSet) list; case structure checks
    EffectiveRulesService.java (modify) merge keeps casos
    QuestionFacts.java         record: what the analyzer needs to know about one active question
    Finding.java               record: code, element, message, questions
    AnalysisReport.java        record: errors, warnings; newErrorsComparedTo(previous)
    AnalysisCodes.java         constants for finding codes
    RuleSetAnalyzer.java       §5 errors and bounded warnings
    CaseRunner.java            runs a TestCase; CaseResult
    CaseResult.java            record: id, status, actualHidden, actualErrors, differences
  admin/
    QuestionFactsService.java  facts for (survey, year) from FRM_* and ALL_TAB_COLUMNS
    RulesAdminService.java     view, edits, import/export, simulator view
    InvalidRulesException.java 422 carrying the AnalysisReport
    YearCopyService.java       (modify) copies casos with porRevisar cleared
    QuestionAdminService.java  (modify) deactivate checks cases; edits mark cases porRevisar
    controller/RulesAdminController.java
    dto/RulesEditorView.java, RuleQuestion.java, RulesEditResult.java, SimulatorView.java,
        SkipRuleRequest.java, ValidationRequest.java, TestCaseRequest.java, ImportRulesRequest.java
  service/error/ReferencedByRulesException.java (modify) + case ids
  controller/ApiExceptionHandler.java           (modify) InvalidRulesException → 422 with "report"
backend/src/main/resources/rules-seed/EDIN-2026.json  (modify) real 2026 cases from docs/reglas/edin-2026.md
db/dev/rules-seed or equivalent seed path              dev case on C01A/C01B (see Task 4)
shared/rules-fixtures/07-caso-simulador.json            parity fixture (Task 4)
frontend/src/
  components/QuestionnaireTabs.tsx              Preguntas | Reglas | Simulador navigation
  features/survey/rules/rule-evaluator.ts       (modify) + explain()
  features/admin-rules/
    rules-api.ts, rule-description.ts, RulesPage.tsx, SkipRuleBuilder.tsx,
    ValidationEditor.tsx, AnalysisSummary.tsx, RulesJsonPanel.tsx
  features/admin-simulator/
    simulator-state.ts, SimulatorPage.tsx, SaveCaseDialog.tsx, CaseList.tsx
  App.tsx                                        (modify) routes …/reglas and …/simulador
frontend/e2e/admin-reglas.spec.ts
docs/diagramas/secuencia-guardar-regla.md, README.md, CHANGELOG.md, spec "Desviaciones (3b)"
```

---

### Task 1: Test cases in the rules file and a non-throwing validator

**Files:**
- Create: `backend/src/main/java/co/gov/dane/edi/rules/TestCase.java` and `ExpectedOutcome.java`
- Modify: `RuleSet.java`, `RuleSetValidator.java`, `EffectiveRulesService.java` (`merge`), `admin/YearCopyService.java` (`draftRulesFrom`)
- Test: `backend/src/test/java/co/gov/dane/edi/rules/RuleSetJsonTest.java`, `RuleSetValidatorTest.java`, `admin/YearCopyIT.java`

**Interfaces:**
- Produces:
  - `record TestCase(String id, String name, String pageCode, Map<String,String> answers, ExpectedOutcome expected, Boolean needsReview)`, with JSON names `id`, `nombre`, `pagina`, `respuestas`, `esperado`, `porRevisar`, and `porRevisar` omitted when null or false. Also `boolean isNeedsReview()` (`@JsonIgnore`), `TestCase withNeedsReview(boolean)`, and null collections normalized to empty.
  - `record ExpectedOutcome(List<String> hidden, Map<String,String> errors)`, with JSON `ocultas` and `errores`:
    - `ocultas` is normalized to empty and sorted;
    - a null `errores` means errors are not compared, and it is omitted from the JSON. Seeded cases only check hidden questions; simulator cases always carry `errores`.
  - `RuleSet` gains a last component `@JsonProperty("casos") @JsonInclude(NON_EMPTY) List<TestCase> cases`. The existing 7- and 8-argument constructors still compile, passing `List.of()`. It also gains `withSkips(List<SkipRule>)`, `withValidations(Map<String,FieldValidation>)` and `withCases(List<TestCase>)`. The existing `with*` methods keep `cases`.
  - `static List<String> RuleSetValidator.problems(RuleSet)`; `validate` throws only when it is non-empty, with the same message as today.

- [ ] **Step 1: Write the failing tests**
  - `RuleSetJsonTest.casesRoundTripAndAreOmittedWhenEmpty`:
    - a `RuleSet` with one case serializes `"casos":[{"id":"c01a-no","nombre":"…","pagina":"C1","respuestas":{"C01A":"2"},"esperado":{"ocultas":["C01B"],"errores":{}}}]`, with no `porRevisar`, and deserializes equal;
    - with no cases the JSON has no `casos` key.
  - `RuleSetValidatorTest.caseProblems`: each of these yields one problem, and a valid case yields none:
    - a duplicate case id;
    - id `"C 1"`;
    - an empty name;
    - a 101-character name;
    - page `"Z9"`.
  - `RuleSetValidatorTest.problemsDoesNotThrow`: `problems` returns the list for a rule without targets, and `validate` throws `IllegalStateException` for the same input.
  - `YearCopyIT.copiesCasesClearingReview`: create a year from a source whose file has a case with `porRevisar: true`. The new file has the case with `porRevisar` absent.
  - `EffectiveRulesServiceTest.mergeKeepsCases`.

- [ ] **Step 2: Run them to verify they fail**
  - Run: `cd backend && ./mvnw -q test -Dtest='RuleSetJsonTest,RuleSetValidatorTest,EffectiveRulesServiceTest'`
  - Expected: FAIL, compilation errors on `TestCase` and `cases()`.

- [ ] **Step 3: Implement the records, the `RuleSet` component, `problems`, the case checks (case id pattern, uniqueness, name length 1–100, `SurveyPage.find(pageCode)` present), `merge` carrying cases and `draftRulesFrom` copying `source.cases()` with `withNeedsReview(false)`.**

- [ ] **Step 4: Run the tests and the whole suite**
  - Run: `./mvnw -q verify`, on Windows with the short `TEMP` from README §"Ambiente de pruebas".
  - Expected: BUILD SUCCESS. `SurveyRulesIT` still shows no `casos`.

- [ ] **Step 5: Commit** — `feat(backend): casos de prueba en el archivo de reglas`

---

### Task 2: Analyzer errors (spec §5, errors 1–6)

**Files:**
- Create: `rules/QuestionFacts.java`, `Finding.java`, `AnalysisReport.java`, `AnalysisCodes.java`, `RuleSetAnalyzer.java`
- Test: `backend/src/test/java/co/gov/dane/edi/rules/RuleSetAnalyzerErrorsTest.java`

**Interfaces:**
- Consumes:
  - `RuleSet`, `SkipRule`, `Condition`, `FieldValidation`, `RuleSetValidator.problems` (Task 1);
  - `model.InputType`, `model.ColumnStatus`, `model.AnswerColumn`;
  - `service.SurveyAnswerService.checkColumn(String, AnswerColumn)`, a package-private static. Make it `public static`, because the capacity rule must be the one Phase 2 uses to save answers.
- Produces:
  - `record QuestionFacts(String column, String pageCode, int position, InputType inputType, List<String> optionValues, ColumnStatus columnStatus, AnswerColumn capacity)`:
    - `position` is the 0-based order over the whole year: `SurveyPage.ordered()`, then the page order;
    - `optionValues` is empty for TEXT and `["0","1"]` for CHECKBOX;
    - `capacity` is null when the column does not exist yet; then `RuleSetAnalyzer` uses the year's `NewColumn` for that column, if any.
  - `record Finding(String code, String element, String message, List<String> questions)`:
    - `element` is one of `regla:<id>`, `validacion:<COL>`, `caso:<id>`, `pregunta:<COL>`, `archivo`;
    - `message` is Spanish, at most one sentence, and names codes and values but never question text.
  - `record AnalysisReport(List<Finding> errors, List<Finding> warnings)` with `boolean hasErrors()` and `List<Finding> newErrorsComparedTo(AnalysisReport previous)`. The comparison is by code, element and questions.
  - `final class AnalysisCodes` with these error constants: `ESTRUCTURA`, `PREGUNTA_INEXISTENTE`, `CICLO`, `VALOR_NO_ES_OPCION`, `RANGO_INVALIDO`, `RANGO_EXCEDE_COLUMNA`, `VALIDACION_EN_OPCION`. Its warning constants are listed in Task 3.
  - `static AnalysisReport RuleSetAnalyzer.analyze(RuleSet effective, Map<String,QuestionFacts> facts)`, deterministic, with findings sorted by element then code.

Exact error semantics, one test each in Step 1:

| Code | Element | When |
| :-- | :-- | :-- |
| `ESTRUCTURA` | `archivo` | one finding per `RuleSetValidator.problems` entry, the message being that entry |
| `PREGUNTA_INEXISTENTE` | `regla:<id>`, `validacion:<COL>`, `caso:<id>` | a condition column, target, validation key or case answer/expected column is not a key of `facts` |
| `CICLO` | `regla:<id>` of each rule on the cycle | the graph trigger→target over all rules has a cycle; `questions` lists the cycle in order, starting at the smallest code |
| `VALOR_NO_ES_OPCION` | `regla:<id>` | op `eq`/`ne`/`in`/`notIn` on a SINGLE_CHOICE or CHECKBOX trigger with a value not in `optionValues` |
| `RANGO_INVALIDO` | `validacion:<COL>` | `min > max`, `longitudMax < 1`, or `tipo` `entero` with a non-integer `min`/`max` |
| `RANGO_EXCEDE_COLUMNA` | `validacion:<COL>` | on a NUMBER capacity, `checkColumn(min.toPlainString(), capacity)` or the same for `max` returns an error; on a VARCHAR2 capacity, `longitudMax` > `charLength` (char semantics) or > `dataLength` (byte semantics). Min/max on a VARCHAR2 column is allowed (text holding digits). With neither capacity nor `NewColumn`, no check |
| `VALIDACION_EN_OPCION` | `validacion:<COL>` | SINGLE_CHOICE or CHECKBOX with any of `min`, `max`, `longitudMax`, or `tipo` other than `opcion`; `obligatoria` alone is allowed |

- [ ] **Step 1: Write the failing tests** in `RuleSetAnalyzerErrorsTest`, using a small facts map:
  - `C01A` SINGLE_CHOICE with options `1`/`2`;
  - `C01B` TEXT with capacity `NUMBER(3,0)`;
  - `C02` TEXT with capacity `VARCHAR2(10 BYTE)`;
  - `C03` CHECKBOX.

  The tests:
  - `unknownQuestionInConditionTargetValidationAndCase` produces 4 findings with the elements above;
  - `directAndIndirectCycles`: `r1` (C01A→C01B) plus `r2` (C01B→C01A) gives one `CICLO` per rule, with questions `[C01A, C01B]`;
  - `valueNotAnOption`: `C01A eq "3"` is flagged, and `C01A eq "2"` is not;
  - `minGreaterThanMax`;
  - `maxExceedsNumberPrecision`: `max 1000` on `NUMBER(3,0)` is flagged, `max 999` is not;
  - `maxLengthExceedsBytes`: `longitudMax 11` on `VARCHAR2(10 BYTE)`;
  - `rangeOnCombo`;
  - `requiredOnlyOnComboIsFine`;
  - `structureProblemsBecomeFindings`;
  - `newErrorsComparedToIgnoresPreexisting`.
- [ ] **Step 2: Run them to verify they fail**
  - Run: `./mvnw -q test -Dtest=RuleSetAnalyzerErrorsTest`
  - Expected: compilation FAIL.
- [ ] **Step 3: Implement `analyze` for the error codes.** The cycle check is a DFS over the trigger→target graph. A rule with `y`/`o` contributes every leaf question as a trigger.
- [ ] **Step 4: Run the tests to verify they pass**
  - Expected: PASS.
- [ ] **Step 5: Commit** — `feat(backend): análisis de errores de reglas y validaciones`

---

### Task 3: Analyzer warnings with bounded enumeration (spec §5, warnings 1–5)

**Files:**
- Modify: `rules/RuleSetAnalyzer.java`, `AnalysisCodes.java`
- Test: `backend/src/test/java/co/gov/dane/edi/rules/RuleSetAnalyzerWarningsTest.java`

**Interfaces:**
- Produces:
  - warning constants `NUNCA_SE_MUESTRA`, `NUNCA_SE_CUMPLE`, `NO_ANALIZABLE`, `DESTINO_ANTES`, `REGLAS_SOLAPADAS`, `PENDIENTE_COLUMNA`;
  - `public static final int MAX_COMBINATIONS = 4096`.

**Enumeration (decided here, spec §12 leaves it to the builder).**
- **Domain:** for a set of trigger columns, each column's domain is:
  - SINGLE_CHOICE: `optionValues` plus `""` (unanswered);
  - CHECKBOX: `["0","1"]`;
  - TEXT: `""`, plus every literal used against that column in any condition. For each numeric literal `v`, also `v-1` and `v+1`. Also the sentinel `"x"`.
- **Product limit:** if the product of domain sizes is greater than `MAX_COMBINATIONS`, emit `NO_ANALIZABLE` for that element and skip it.
- **Evaluation:** each combination is evaluated with `RuleEvaluator.evaluate(effective, combination, List.of())`, so cascades apply exactly as for the respondent.
- **Relevant triggers of a question Q:** the closure of the triggers of rules that target Q, plus the triggers of rules that target those triggers.

| Code | Element | When |
| :-- | :-- | :-- |
| `NUNCA_SE_MUESTRA` | `pregunta:<COL>` | Q is a target and is in `disabled` for every combination of its relevant triggers |
| `NUNCA_SE_CUMPLE` | `regla:<id>` | no combination of the rule's relevant triggers makes `RuleEvaluator.matches(rule.when, …)` true after cascades |
| `DESTINO_ANTES` | `regla:<id>` | a target's `position` is lower than the lowest trigger `position` of that rule |
| `REGLAS_SOLAPADAS` | `regla:<a>` (a < b by id) | rules a and b share a target and some combination over the union of their triggers matches both |
| `PENDIENTE_COLUMNA` | `pregunta:<COL>` | `columnStatus == PENDING_COLUMN` |

- [ ] **Step 1: Write the failing tests**
  - `neverShown`: C01A SINGLE_CHOICE with options 1 and 2, plus three rules hiding C01B: `C01A eq "1"`, `C01A ne "1"` and `C01A empty`. Expect `NUNCA_SE_MUESTRA` on `pregunta:C01B`. Add a control in which the `empty` rule is removed and no warning appears, because unanswered C01A shows C01B.
  - `f04ShapeBeyondLimit`: rules `f04-todas-no` and `f04-alguna-si` from `docs/reglas/edin-2026.md` over F04A–F04H, each with options 1, 2 and 9, so the domain is 4 including `""`. Then 4⁸ = 65536 > 4096, so expect `NO_ANALIZABLE` on `pregunta:F04I` and on both rules. Real F04I is shown when a combo is unanswered, so it is not a never-shown case.
  - `ruleThatNeverMatches`: `C01A eq "1"` and `C01A eq "2"` joined by `y`.
  - `targetBeforeTrigger`.
  - `overlappingRules`.
  - `pendingColumnWarning`.
  - `warningsNeverBlock`: `hasErrors()` is false with only warnings.
  - `analysisBeyondLimitReportsNotAnalyzableQuickly`: 13 two-option combos, one rule using `alguna` of all 13. `NO_ANALIZABLE`, and `analyze` returns in < 1000 ms (`assertTimeoutPreemptively`).
- [ ] **Step 2: Run them to verify they fail.**
- [ ] **Step 3: Implement the warnings.** Compute the domain product with an overflow-safe multiply, stopping at > 4096.
- [ ] **Step 4: Run the tests; coverage stays ≥ 80 % on `rules`.**
  - Run: `./mvnw -q verify`
- [ ] **Step 5: Commit** — `feat(backend): avisos de lógica con análisis acotado`

---

### Task 4: Case runner, parity fixture and seeded cases

**Files:**
- Create: `rules/CaseRunner.java`, `rules/CaseResult.java`, `shared/rules-fixtures/07-caso-simulador.json`
- Modify: `backend/src/main/resources/rules-seed/EDIN-2026.json`, with the real 2026 cases and the version raised by one, and the dev seed so the fictitious C1 has a green case (see Step 3)
- Test: `backend/src/test/java/co/gov/dane/edi/rules/CaseRunnerTest.java`; the existing shared-fixture tests (Java and `rule-evaluator.test.ts`) pick up fixture 07 automatically if they iterate the folder (verify)

**Interfaces:**
- Produces:
  - `record CaseResult(String id, Status status, List<String> actualHidden, Map<String,String> actualErrors, List<String> differences)`:
    - `enum Status { PASA, FALLA, POR_REVISAR }`, serialized lowercase like `YearState`;
    - `differences` are Spanish lines such as "Se esperaba oculta C01B y está visible." or "Se esperaba el error REQUERIDA en C02 y no hubo error."
  - `static CaseResult CaseRunner.run(RuleSet effective, TestCase testCase, Map<String,QuestionFacts> facts)`:
    - **Status:** `POR_REVISAR` without evaluating when `testCase.isNeedsReview()`, or when any answer key or expected column is not an ACTIVE key of `facts`.
    - **Evaluation:** otherwise `RuleEvaluator.evaluate(effective, testCase.answers(), pageItems)`, where `pageItems` are the ACTIVE facts of `testCase.pageCode()` in position order.
    - **Hidden:** `actualHidden` = `disabled` ∩ ACTIVE facts keys, sorted.
    - **Pass:** `PASA` iff `actualHidden` equals `expected.hidden` and, when `expected.errors` is not null, `actualErrors` equals it.
  - `static List<CaseResult> CaseRunner.runAll(RuleSet effective, Map<String,QuestionFacts> facts)`, in file order.

- [ ] **Step 1: Write the failing tests**
  - `passesWhenHiddenAndErrorsMatch`;
  - `nullExpectedErrorsAreNotCompared`;
  - `failsListingEachDifference`;
  - `caseReferencingInactiveQuestionNeedsReview`;
  - `flaggedCaseIsNotEvaluated`;
  - `sharedSimulatorFixturePasses`: it loads `07-caso-simulador.json`, whose shape is the existing fixture shape plus a `"caso"` object, and asserts `PASA`. The fixture has a cascade: C01A=2 hides C01B, and a rule on C01B empty hides C02. It also includes a REQUERIDA error on page C1.
- [ ] **Step 2: Run them to verify they fail.**
- [ ] **Step 3: Implement and seed the cases.**
  - **Real 2026 cases:** one case per row of the table in `docs/reglas/edin-2026.md`. That gives 12 cases with ids equal to the rule id plus `-caso`, on the rule's page, holding only the answers that trigger the rule. Each case has `ocultas` and no `errores`, because the real page items are not in the repository. `ocultas` is what `RuleEvaluator` yields for those answers with the 2026 seed rules. Compute it with a throwaway test that is not committed, never by hand, and check it against the "Deshabilita" column of the doc. These cases report `POR_REVISAR` against the fictitious dev questionnaire, which lacks those columns, by design.
  - **Dev case:** find where the dev rules seed lives (`RULES_SEED_DIR` in `docker-compose.yml` / `RulesSeedProperties`). If the 2026 seed is the only one, add a case `c01a-no-oculta-c01b` on page C1 to it (C01A=2 → C01B hidden) so dev shows one green case. Document the choice in the report.
- [ ] **Step 4: Run backend `./mvnw -q verify` and frontend `npm test`, so fixture 07 passes in the TypeScript evaluator too.**
- [ ] **Step 5: Commit** — `feat(backend): ejecución de casos de prueba y casos sembrados de 2026`

---

### Task 5: Rules administration API

**Files:**
- Create: `admin/QuestionFactsService.java`, `admin/RulesAdminService.java`, `admin/InvalidRulesException.java`, `admin/controller/RulesAdminController.java`, and the DTOs listed in File Structure
- Modify: `controller/ApiExceptionHandler.java`, `docs/api/openapi.yaml` (regenerated)
- Test: `admin/QuestionFactsServiceIT.java`, `admin/RulesAdminIT.java`

**Interfaces:**
- Consumes:
  - `QuestionnaireService.pageEntries(SurveyPage, String, int)`;
  - `AnswerTableResolver.resolve(Chapter, String, int)`;
  - `AnswerColumnRepository.findColumnDetails(String)`, read fresh for a draft as in 3a deviation 11;
  - `YearCatalogService.requireYear` / `requireDraft`;
  - `EffectiveRulesService.effectiveRules(String, int)` and `merge`;
  - `LegacySkipTranslator`, through the effective rules;
  - `RulesFileRepository.save`;
  - `SaveLocks.withAdminLock`;
  - the evict calls of 3a (`questionnaire`, `effectiveRules`, `tables`).
- Produces:
  - `Map<String,QuestionFacts> QuestionFactsService.facts(String survey, int year)`, a `LinkedHashMap` in position order with ITEM entries only.
  - DTO `record RuleQuestion(String column, String pageCode, String numeral, String text, String inputType, List<AnswerOption> options, String columnStatus)`. `text` is the plain preview text, as in `AdminEntry.text`.
  - DTO `record RulesEditorView(String survey, int year, YearState state, int version, List<SkipRule> skips, List<String> legacySkipIds, Map<String,FieldValidation> validations, List<TestCase> cases, AnalysisReport report, List<CaseResult> caseResults, List<RuleQuestion> questions)`. `skips` are the effective rules; `legacySkipIds` are those not present in the file, which are read-only.
  - DTO `record RulesEditResult(int version, AnalysisReport report, List<CaseResult> caseResults)`.
  - Requests:
    - `SkipRuleRequest(SkipRule rule, int version)`;
    - `ValidationRequest(FieldValidation validation, int version)`;
    - `TestCaseRequest(TestCase testCase, int version)`;
    - `ImportRulesRequest(RuleSet rules, int version)`.
  - `InvalidRulesException(AnalysisReport report)` maps to 422, title "Las reglas tienen errores.", with property `report` and only the new errors listed in it.

**Endpoints** (base `/api/v1/admin/questionnaire/{encuesta}/{anio}/rules`):

| Method and path | Effect |
| :-- | :-- |
| `GET` | `RulesEditorView`; works for published years too (read-only) |
| `PUT /skips/{ruleId}` | upsert by id (path id must equal `rule.id`, else 422 "El id de la regla no coincide."); new ids must match the rule-id pattern |
| `DELETE /skips/{ruleId}?version=N` | 404 "La regla no existe." if absent |
| `PUT /validations/{column}` / `DELETE /validations/{column}?version=N` | same pattern, column must match `^[A-Z][0-9A-Z_]{1,29}$` |
| `PUT /cases/{caseId}` / `DELETE /cases/{caseId}?version=N` | same pattern; saving a case clears its `porRevisar` |
| `GET /export` | the stored file, `Content-Disposition: attachment; filename="<ENC>-<AAAA>.json"` |
| `PUT /import` | replaces only `saltos`, `validaciones` and `casos`; body `encuesta`/`anio` must match the path (422 otherwise); `estado`, `tablas`, `columnasNuevas` and `version` from the body are ignored |

**Every write:**
1. Take the admin lock.
2. `requireDraft`; then the version check, 409 on mismatch.
3. Build the candidate file and compute `before = analyze(effective(current))` and `after = analyze(effective(candidate))`.
4. If `after.newErrorsComparedTo(before)` is non-empty, throw `InvalidRulesException`.
5. Otherwise save, evict, and return `RulesEditResult(saved.version(), after, CaseRunner.runAll(...))`, built inside the lock.

- [ ] **Step 1: Write the failing tests**
  - `QuestionFactsServiceIT.factsForDevYear`: 2026 dev facts contain C01A as SINGLE_CHOICE with its real options, C01B, position order across pages and capacities from `ALL_TAB_COLUMNS`.
  - `RulesAdminIT` (with `DraftYearFixture` from 3a):
    - `viewListsRulesReportCasesAndQuestions`;
    - `saveSkipReturnsNewVersionReportAndCases`;
    - `ruleAddingAnErrorIsRejectedAndFileUnchanged` (value 3 on C01A);
    - `preexistingErrorsDoNotBlockUnrelatedSave`;
    - `importWithIndirectCycleIsRejectedAndFileUnchanged`;
    - `importKeepsStateTablesAndNewColumns`;
    - `exportDownloadsStoredFile`;
    - `staleVersionGets409`;
    - `publishedYearRulesAreReadOnly` (409 on 2026);
    - `pathIdMismatchIs422`;
    - `deleteMissingRuleIs404`;
    - `respondentAndNationalAdminGet403`;
    - `writesRequireCsrf`.
- [ ] **Step 2: Run them to verify they fail.**
- [ ] **Step 3: Implement the service, controller (OpenAPI annotations in Spanish like `QuestionnaireAdminController`) and exception mapping.**
- [ ] **Step 4: Run `./mvnw -q verify`; then in `frontend/` run `npm run api:types` and `npx tsc --noEmit`.**
  - Expected: green, and `openapi.yaml` updated in the same commit.
- [ ] **Step 5: Commit** — `feat(backend): API de edición de reglas, validaciones y casos`

---

### Task 6: Simulator view and cases inside question edits

**Files:**
- Modify:
  - `admin/RulesAdminService.java`, adding the simulator view;
  - `admin/controller/RulesAdminController.java`;
  - `admin/QuestionAdminService.java`;
  - `service/error/ReferencedByRulesException.java`;
  - `ApiExceptionHandler.java`.
- Create: `admin/dto/SimulatorView.java`
- Test: `admin/RulesAdminIT.java`, `admin/QuestionEditIT.java`

**Interfaces:**
- Produces:
  - `GET /api/v1/admin/questionnaire/{encuesta}/{anio}/simulator` returns `record SimulatorView(int version, RespondentRules rules, List<PageDetail> pages)`:
    - `rules` holds the effective rules;
    - `pages` follow `SurveyPage.ordered()` with ACTIVE and PENDING entries from `pageEntries(page, survey, year)`, `state` null and `answers` empty;
    - it never reads or writes answer tables.
  - `ReferencedByRulesException(List<String> ruleIds, List<String> caseIds)`. The 409 body keeps `errors.rules` and adds `errors.cases`.
- **Deactivation:** `QuestionAdminService.deactivate` now also rejects when any case uses the column in `respuestas`, `esperado.ocultas` or `esperado.errores`.
- **Marking cases for review:** text edits on an entry and reorders mark every case that references a changed column with `withNeedsReview(true)`, through the existing `fileChange` of `apply`. Reorders change position, so they mark the cases of that page. Header and help edits do not mark cases.

- [ ] **Step 1: Write the failing tests**
  - `simulatorViewHasPagesAndEffectiveRulesWithoutAnswers`: it also asserts that no row was inserted in any answer table, by counting rows of `EDIN_20_CAP_C_*` before and after;
  - `deactivatingQuestionUsedOnlyByCaseIsRejected`;
  - `editingReferencedQuestionMarksCaseForReview`;
  - `reorderMarksCasesOfThatPage`;
  - `headerEditDoesNotMarkCases`.
- [ ] **Step 2: Run them to verify they fail.**
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run `./mvnw -q verify`, `npm run api:types` and `npx tsc --noEmit`.**
- [ ] **Step 5: Commit** — `feat(backend): vista del simulador y casos por revisar al editar preguntas`

---

### Task 7: Frontend foundations — API client, plain-language rules, `explain`, navigation

**Files:**
- Create:
  - `frontend/src/features/admin-rules/rules-api.ts`;
  - `features/admin-rules/rule-description.ts`;
  - `components/QuestionnaireTabs.tsx`.
- Modify:
  - `features/survey/rules/rule-evaluator.ts`;
  - `App.tsx`;
  - `features/admin-questions/QuestionnaireTreePage.tsx`, which renders the tabs.
- Test: `rules-api.test.ts`, `rule-description.test.ts`, `rule-evaluator.test.ts`, `QuestionnaireTabs.test.tsx`

**Interfaces:**
- Produces:
  - `rules-api.ts`, using the same `ensureCsrfToken` + `unwrap` pattern as `admin-api.ts`:
    - `rulesQueryKey(survey, year)`, `simulatorQueryKey(survey, year)`;
    - `fetchRules(survey, year): Promise<RulesEditorView>`;
    - `saveSkip(survey, year, rule, version)`, `deleteSkip(survey, year, ruleId, version)`;
    - `saveValidation(survey, year, column, validation, version)`, `deleteValidation(…)`;
    - `saveCase(survey, year, testCase, version)`, `deleteCase(…)`;
    - `importRules(survey, year, rules, version)`;
    - `downloadRules(survey, year)`, through the existing `download-file.ts`;
    - `fetchSimulator(survey, year): Promise<SimulatorView>`.

    Every write returns `Promise<RulesEditResult>`. A 422 rejects with an `ApiError` whose `report` property carries the `AnalysisReport`.
  - `rule-description.ts`:
    - `describeRule(rule: SkipRule, questions: Map<string, RuleQuestion>): string`;
    - `describeCondition(condition, questions): string`.

    The exact phrasing is: `Si E03 = Sí → no se muestra E04`. Option values are shown by their label, and several targets are joined with ", ". The operators read:

    | Operator | Reading |
    | :-- | :-- |
    | `eq` | `=` |
    | `ne` | `≠` |
    | `in` | `es una de {a, b}` |
    | `notIn` | `no es ninguna de {a, b}` |
    | `gt` | `>` |
    | `lt` | `<` |
    | `empty` | `está vacía` |
    | `notEmpty` | `tiene respuesta` |

    Two more forms:
    - `todas` reads `todas de C09A, C09B = No`, and `alguna` reads `alguna de …`;
    - `y`/`o` read `(… y …)` and `(… o …)`.
  - `explain(rules: RuleSet, answers: Record<string, string | undefined>): Record<string, string[]>` in `rule-evaluator.ts`. It maps each disabled column to the ids of the rules whose condition matches in the final, stable round. It uses the same rounds as `evaluate`.
  - `QuestionnaireTabs({ survey, year })`: a `nav` with `aria-label="Secciones del cuestionario"` and three `NavLink`s, Preguntas, Reglas and Simulador. The current one gets `aria-current="page"`.
  - Routes `cuestionario/:encuesta/:anio/reglas` and `cuestionario/:encuesta/:anio/simulador` under the existing `ADMIN_CUESTIONARIO` guard. Each lazily renders a placeholder until Tasks 8 and 9.

- [ ] **Step 1: Write the failing tests**
  - `describeRule` for each operator, `todas`/`alguna`, `y`/`o` nesting, and a missing question, which falls back to the bare code;
  - `explain` on fixtures 02 (cascade) and 07;
  - the tabs mark the current section;
  - MSW handlers for the client, including a 422 carrying `report`.
- [ ] **Step 2: Run `npm test -- rule-description rule-evaluator rules-api QuestionnaireTabs`.** Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run `npm run lint && npx tsc --noEmit && npm test`.** Expected: green.
- [ ] **Step 5: Commit** — `feat(frontend): cliente de reglas, lectura en lenguaje claro y navegación del cuestionario`

---

### Task 8: Rules page — builder, validations, analysis, JSON view

**Files:**
- Create:
  - `features/admin-rules/RulesPage.tsx`;
  - `SkipRuleBuilder.tsx`;
  - `ValidationEditor.tsx`;
  - `AnalysisSummary.tsx`;
  - `RulesJsonPanel.tsx`.
- Test: `RulesPage.test.tsx`, `SkipRuleBuilder.test.tsx`, `ValidationEditor.test.tsx`, `AnalysisSummary.test.tsx`

**Behavior (decided here):**
- **Page filter.** A page selector filters rules and validations to those touching that page.
  - "Efecto en la página" lists each question of the page that some rule hides, with the rule ids and their `describeRule` text (spec §4.2).
- **Rules list.** Each rule shows its id, its `describeRule` sentence and Editar / Eliminar buttons.
  - Legacy rules (`legacySkipIds`) show "Heredada, solo lectura" and have no buttons.
  - Every button is disabled when `state` is `publicado`.
- **`SkipRuleBuilder`** (dialog with focus trap, like `AddQuestionDialog`):
  - id field (new rules only);
  - description;
  - a list of conditions joined by a single "Y" / "O" selector, one level only;
  - each condition offers mode (una pregunta / todas / alguna), question pickers (a select of `RuleQuestion`, labeled "código — texto"), the operator and values;
  - values come from `options` for SINGLE_CHOICE (select, or checkboxes for `in`/`notIn`), from Sí/No for CHECKBOX, and from a text field only for TEXT;
  - target questions use a multi-select of `RuleQuestion`.

  A rule whose condition nests deeper opens read-only, with "Esta regla solo se edita importando el archivo." Live checks reuse `describeRule` as a preview and flag an empty target list before sending.
- **`ValidationEditor`**, per question of the page:
  - TEXT offers obligatoria, tipo (entero, decimal, texto), mínimo, máximo and longitud máxima;
  - SINGLE_CHOICE and CHECKBOX offer obligatoria only;
  - an empty field means the key is omitted.
- **`AnalysisSummary`:**
  - errors first, then warnings, each with its code turned into a Spanish heading and the message;
  - a link to the element: `regla:<id>` points to the rule row's anchor, `validacion:` / `pregunta:` to the question row, and `caso:` to the simulator route with `#caso-<id>`;
  - a 422 from a save shows only the new errors, at the top, focused, with `role="alert"`;
  - findings are also shown next to the element that causes them (spec §9).
- **`RulesJsonPanel`:**
  - a read-only `<pre>` of the export;
  - "Descargar JSON" (`downloadRules`);
  - "Importar JSON", a file input of at most 1 MB. It parses the file client-side, shows a parse error, and confirms with `ConfirmDialog` before `importRules`.
- **Conflicts:** a 409 opens the 3a conflict dialog. After every successful save the page invalidates `rulesQueryKey` and announces "Regla guardada." through a polite live region.

- [ ] **Step 1: Write the failing tests**
  - Builder, filled by keyboard only: it produces the exact `SkipRule` JSON for `C01A = 2 → C01B` and for `todas de C09A,C09B = 2 → C10`;
  - TEXT values are only offered for TEXT questions;
  - a nested rule opens read-only;
  - the validation editor hides range fields for combos;
  - the summary links to the elements, and a 422 focuses the alert;
  - legacy and published states are read-only;
  - import: an invalid JSON shows an error, and a valid one calls the API after confirmation;
  - `axe` has no violations on `RulesPage` with data.
- [ ] **Step 2: Run the tests to verify they fail.**
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run `npm run lint && npx tsc --noEmit && npm test`.**
- [ ] **Step 5: Commit** — `feat(frontend): editor de reglas y validaciones con análisis`

---

### Task 9: Simulator and test cases

**Files:**
- Create:
  - `features/admin-simulator/simulator-state.ts`;
  - `SimulatorPage.tsx`;
  - `SaveCaseDialog.tsx`;
  - `CaseList.tsx`.
- Test: `simulator-state.test.ts`, `SimulatorPage.test.tsx`, `SaveCaseDialog.test.tsx`, `CaseList.test.tsx`

**Interfaces:**
- Consumes:
  - `fetchSimulator`, `fetchRules`, `saveCase` and `deleteCase` (Task 7);
  - `evaluate` and `explain` (Task 7);
  - `QuestionField` from `features/survey/questions/QuestionField.tsx`.
- Produces:
  - `simulator-state.ts`:
    - `type SimulatorAnswers = Record<string, string>`, the answers of all pages, in memory only, never in `localStorage`;
    - `simulatePage(view: SimulatorView, pageCode: string, answers: SimulatorAnswers): { hidden: Record<string, string[]>; errors: Record<string, string> }`, where `hidden` maps column → rule ids from `explain`, and errors are `evaluate` errors restricted to the page's active items, the same scope as `CaseRunner`;
    - `caseFromSimulation(id, name, pageCode, answers, result): TestCase`, holding only the answers of columns that are active items in the year, the sorted `ocultas` and `errores`.

**Behavior:**
- **Navigation.** A select or list jumps to any page (spec §6).
  - The page renders its entries with `QuestionField` and RHF, without the respondent save. Changing pages keeps the answers.
  - Hidden questions stay visible but disabled, with the note "Oculta por la regla c07a-no: Si C07A = No → no se muestra C08A…", from `describeRule`.
  - Errors show the respondent messages (`answer-error-messages.ts`).
- **Actions.**
  - "Reiniciar respuestas" clears everything.
  - "Guardar como caso de prueba" opens `SaveCaseDialog` (id, name) and saves the current page's case.
- **`CaseList`:**
  - every case with its `CaseResult` status: Pasa (green), Falla (red, with its `differences`) or Por revisar (amber, with "Abrir en el simulador" to load its answers and page);
  - each row has an anchor `caso-<id>`;
  - statuses are text plus icon, not color alone;
  - deleting a case asks for confirmation.
- **Read-only.** On a published year the simulator works, but saving cases is disabled.

- [ ] **Step 1: Write the failing tests**
  - `simulator-state.test.ts > fixture 07 matches`: `caseFromSimulation` over fixture 07's answers yields exactly the fixture's `"caso"` object. This is the same fixture `CaseRunnerTest` asserts as `PASA`.
  - The hidden-question note names the rule.
  - Answers survive page changes.
  - Saving a case sends the exact body.
  - The case list shows the three statuses with text, and "Abrir en el simulador" restores answers and page.
  - Nothing is written to `localStorage`, checked with a `vi.spyOn(Storage.prototype, 'setItem')` that is never called.
  - `axe` has no violations.
- [ ] **Step 2: Run the tests to verify they fail.**
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run `npm run lint && npx tsc --noEmit && npm test`.**
- [ ] **Step 5: Commit** — `feat(frontend): simulador del cuestionario y casos de prueba`

---

### Task 10: E2E, documentation and spec deviations

**Files:**
- Create: `frontend/e2e/admin-reglas.spec.ts`, `docs/diagramas/secuencia-guardar-regla.md`
- Modify:
  - `README.md`, adding the API references, usage, environment notes and debt, while keeping the eleven sections in order;
  - `CHANGELOG.md`, under `[Sin publicar]`;
  - the spec, adding a section "Desviaciones de implementación (3b)";
  - `docs/reglas/edin-2026.md`, noting that the cases are seeded.

**E2E** (Chromium, the same restart rules as `admin-cuestionario.spec.ts`; móvil skips visibly with an `omitida` annotation, because spec §9 does not require mobile):
1. Log in as `adm.nacional` and create 2027 from 2026 (or reuse the open draft created by this spec in the same run). Open Reglas.
2. Create the rule `c01a-dos` (`C01A = 2 → C01B`) with the keyboard. The list shows "Si C01A = No → no se muestra C01B".
3. Import a JSON with an indirect cycle. The alert lists `CICLO` and the rule list is unchanged.
4. In the Simulador, answer C01A = No. C01B shows as hidden by `c01a-dos`. Save case `c01a-dos-caso`; it shows **Pasa**.
5. Edit the rule to target a different question. The case shows **Falla** with its difference.
6. Run axe (WCAG 2.1 AA) on Reglas and Simulador.

**Documentation:**
- The diagram shows a rule save: lock → `requireDraft` → version → analysis before and after → 422 with the new errors, or save → cases → response.
- The deviations section lists:
  - version in the body instead of `If-Match`;
  - "new errors only" blocking;
  - builder limited to one level;
  - the enumeration domains;
  - case scope (one page, hidden over the year);
  - `porRevisar` triggers;
  - the 2026 cases being `POR_REVISAR` in dev;
  - anything else decided during execution.

- [ ] **Step 1: Write the E2E spec and the docs.**
- [ ] **Step 2: Run the stack locally (README "Ambiente de pruebas", backend restart and clean `RULES_DIR`), then `npx playwright test e2e/admin-reglas.spec.ts --project=chromium`.**
  - Expected: green.
  - Then run `npm run e2e` in full. Expected: green or visibly skipped only.
- [ ] **Step 3: Run `./mvnw -q verify`, `npm run lint && npx tsc --noEmit && npm test`, and `npx prettier --check .`.**
- [ ] **Step 4: Commit** — `docs: reglas, simulador y casos de prueba (Fase 3b)`
