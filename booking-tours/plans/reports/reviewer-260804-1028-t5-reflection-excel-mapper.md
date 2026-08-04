# T5 Reflection-based Excel Mapper — Code Review

**Score: 7.5 / 10**
**Verdict: APPROVE_WITH_FIXES**

---

## Scope

| File | Role |
|---|---|
| `excel/annotation/ExcelColumn.java` | New annotation |
| `excel/ExcelMapper.java` | New generic mapper (175 LOC) |
| `excel/ExcelValueCodec.java` | New codec (48 LOC) |
| `excel/dto/BookingExcelRow.java` | New DTO |
| `excel/dto/TourExcelRow.java` | New DTO |
| `excel/BookingExcelExporter.java` | Refactored — delegates to mapper |
| `excel/TourExcelImporter.java` | Refactored — uses mapper in parseRow |
| `test/excel/ExcelMapperTest.java` | 7 new tests |

---

## Spec Compliance

| Criterion | Result | Notes |
|---|---|---|
| T5.1 `@ExcelColumn(header, order, required, dateFormat)` | PASS | All 4 attributes present, correct retention/target |
| T5.2 `export(List, Class, sheetName)` — reflection, styles, autosize | PASS | Header #BDD7EE, alternating rows, THIN borders, autosize all present |
| T5.3 `importRow(String[], Class)` — no-arg ctor, field.set, required, IAE | PASS | No POI imports in that method path; required check throws IAE |
| T5.4 `BookingExcelRow` 8 fields; `BookingExcelExporter` delegates | PASS | 8 @ExcelColumn fields; exporter is a thin delegator |
| T5.5 `TourExcelRow` 9 fields; `TourExcelImporter.parseRow()` uses mapper; domain rules intact | PASS | 9 fields; all domain checks (price > 0, future date, category lookup) preserved |
| T5.6 7 test cases covering headers, formatting, round-trip, valid import, required missing, bad date, bad number | PASS (weak) | 7 tests pass; assertion quality issues — see findings |

---

## Findings

### HIGH — Usability-breaking

**H1: Parse error message omits field name**
- Location: `ExcelValueCodec.java:45`, `ExcelMapper.java:106`
- When `ExcelValueCodec.parse()` throws, the message is `"Invalid value: 'abc'"` — no column name, no row context. The calling code in `importRow` does not wrap it with field context before propagating. A user uploading a 500-row file gets `"Invalid value: 'abc'"` with no way to know which column to fix.
- Compare: the required-check message at line 103 correctly includes the header name (`"Price is required"`).
- Fix: wrap the parse call in `importRow`:
  ```java
  try {
      f.set(instance, ExcelValueCodec.parse(raw, f.getType(), ann.dateFormat()));
  } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(ann.header() + ": " + e.getMessage(), e);
  }
  ```

---

### MEDIUM — Performance / Maintainability

**M1: `orderedFields()` called twice per row in `importSheet()`**
- Location: `ExcelMapper.java:118` (inside loop) and `ExcelMapper.java:97` (inside `importRow`)
- For N rows: 2N calls to `getDeclaredFields()` + sort. For a 10 000-row tour import that is 20 000 reflective field scans. On modern JVMs this is unlikely to be a wall-clock bottleneck, but it is unnecessary work given that `type` never changes within a single import operation.
- Fix: compute `fields` once before the loop in `importSheet`, pass it through (or cache with a `ConcurrentHashMap<Class<?>, List<Field>>`). The simpler fix is to extract the cells-array sizing out of the loop using the same pre-computed list.

**M2: `COLUMN_COUNT = 9` in `TourExcelImporter` is a magic number with no compile-time guard**
- Location: `TourExcelImporter.java:29`
- If a developer adds or removes a field on `TourExcelRow` without updating `COLUMN_COUNT`, the last column silently gets `""` (or a required-field error with no value). There is no assertion or test that catches the mismatch.
- Fix: drive `COLUMN_COUNT` from the annotation at startup:
  ```java
  private static final int COLUMN_COUNT =
      (int) Arrays.stream(TourExcelRow.class.getDeclaredFields())
                  .filter(f -> f.isAnnotationPresent(ExcelColumn.class))
                  .count();
  ```
  Or add a unit assertion: `assertThat(COLUMN_COUNT).isEqualTo(actualAnnotatedFieldCount)`.

**M3: `importRow` declares `throws Exception` in its public signature**
- Location: `ExcelMapper.java:95`
- `getDeclaredConstructor().newInstance()` throws `ReflectiveOperationException` (checked). Declaring `throws Exception` is valid but forces every caller to catch the widest possible exception. The only checked exception path is reflection instantiation; parse failures are already `IllegalArgumentException` (unchecked). Narrowing to `throws ReflectiveOperationException` would be more precise and would make call-sites safer.

---

### LOW — Test Quality

**L1: Tests 5, 6, 7 use `isNotBlank()` — no assertion on error message content**
- Location: `ExcelMapperTest.java:194`, `ExcelMapperTest.java:211`
- Tests 6 and 7 assert only that `error()` is not blank. They would pass even if the mapper swallowed the exception and returned a generic internal-error string. At minimum test 6 should assert that the message contains the field header (`"Departure Date"`) and test 7 should assert it contains `"Price"`.
- Test 5 is better — it asserts `containsIgnoringCase("Title")`.

**L2: No test for empty-list export**
- Location: `ExcelMapperTest.java` — absent
- Spec says `export()` must handle empty lists (header row only, no data rows). Not tested. The implementation handles it correctly (loop body never executes, autoSizeColumn still runs on columns), but the behavior is untested.

**L3: Round-trip test (`exportImportRoundTrip`) uses only STRING cells; NUMERIC cell path in `getCellString` is never exercised**
- Location: `ExcelMapperTest.java:98`
- All cells created via `cell.setCellValue(String)` (POI creates STRING type). A real user-uploaded xlsx will have NUMERIC cells for price and integer columns. The `getCellString` NUMERIC branch (`BigDecimal(cell.getNumericCellValue()).stripTrailingZeros().toPlainString()`) is untested. This path is correct, but a dedicated test covering numeric cell reading would close the gap.

---

## Edge Cases (scouted, no bugs found)

- **Empty list export** — `export(emptyList, ...)`: loop body skips, header row + autoSizeColumn execute correctly. Safe.
- **Null field in export** — `ExcelValueCodec.format(null, ...)` returns `""`. Safe.
- **`cells[]` shorter than field count** — `importRow` line 101 guards with `(i < cells.length) ? cells[i] : ""`. Required fields still trigger their error. Safe.
- **`IllegalAccessException` in export** — silently writes `""` (line 81). Acceptable for export; in practice Lombok-generated fields with `setAccessible(true)` won't throw this.
- **Shared mutable state in `@Component ExcelMapper`** — none. All state is method-local. Thread-safe.
- **`importRow` worker-safety (no POI in worker thread)** — confirmed. `importRow(String[], Class)` has no POI imports in its execution path. Only `importSheet` (single-thread) and `TourExcelImporter.getCellString` (single-thread calling side) touch POI objects.
- **Workbook style limit** — `export()` creates 3 styles per workbook. Called once per HTTP request. Not at risk of the POI 64 000-style ceiling.
- **`setAccessible(true)` on Java 21** — no `module-info.java` in the project, so no module system restrictions apply. Works as expected.

---

## Positive Observations

- **Clean separation**: `ExcelValueCodec` extracted as a package-private final class — keeps `ExcelMapper` under 200 lines and the codec independently testable.
- **Thread-safety design**: the POI read / worker fan-out separation in `TourExcelImporter` is well-structured and correctly enforced. The comments explaining the boundary (`// POI reads happen on the calling thread BEFORE fan-out`) are clear.
- **Null guards in `BookingExcelExporter.toRow()`**: all entity navigation paths are null-checked, matching Day-4 behavior.
- **`ImportResult` record**: clean value type; the `ok`/`error` factory pattern is readable.
- **`importSheet` row-number convention**: `r + 1` for 1-based user-facing error messages is a good practice.
- **`ExcelColumn` annotation is self-describing**: `dateFormat` default `"yyyy-MM-dd"` and `required = false` are sensible defaults that reduce boilerplate for the common case.

---

## Contract Status

| Contract | Status |
|---|---|
| `ExcelExportService.exportBookings(...)` | OK — signature unchanged |
| `ExcelImportService.importTours(...)` | OK — signature unchanged |
| `ExcelImportService.generateTemplate()` | OK — signature unchanged |
| `TourExcelImporter.parseRows(...)` | OK — signature unchanged |
| `TourExcelImporter.ImportRowResult` record | OK — fields unchanged |

**contractStatus: OK**

---

## Recommended Actions (priority order)

1. **(Must fix)** H1 — add field name to parse error message in `importRow`. One-line fix; directly improves user experience in production.
2. **(Should fix)** M2 — replace magic `COLUMN_COUNT = 9` with a reflection-derived constant or add an assertion test. Prevents silent data truncation if `TourExcelRow` evolves.
3. **(Should fix)** L1 — strengthen error message assertions in tests 6 and 7 to include the failing column header.
4. **(Defer)** M1 — cache `orderedFields` result. Worthwhile if imports routinely exceed a few thousand rows; otherwise safe to defer.
5. **(Defer)** M3 — narrow `throws Exception` to `throws ReflectiveOperationException`. Low-risk refactor.
6. **(Defer)** L2 — add empty-list export test. Behavior is correct; test gap only.
7. **(Defer)** L3 — add NUMERIC cell round-trip test. Behavior is correct; test gap only.

---

## Unresolved Questions

None — all contracts examined, no UNKNOWN areas.
