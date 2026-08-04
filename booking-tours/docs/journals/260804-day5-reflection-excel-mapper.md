# Day 5: Reflection-based Excel Mapper (T5)

**Date**: 2026-08-04 11:15
**Severity**: Low
**Component**: Excel export/import, reflection-based mapping
**Status**: Resolved (with fixes applied)

## What Happened

One feature shipped (T5: reflection-based Excel mapper). Replaced hardcoded column logic in `BookingExcelExporter` and `TourExcelImporter` with a `@ExcelColumn` annotation and generic `ExcelMapper` using Java Reflection. The exporter shrank from 107 lines to 53; the importer gained reflection-derived column counting and delegated row parsing. All tests pass. One commit to master. Evidence gate: score 7.5/10 (H1, M2, L1 findings, all remedied post-review).

## The Brutal Truth

Reflection isn't magic — it's explicit. The temptation to treat reflection work as "simpler because no boilerplate" is treacherous. We built solid infrastructure but shipped a handful of small failures that only showed up under user-facing conditions: parse errors named no column, column count was magic (silently drop data if a field is added), error test assertions were too weak. Nothing broke the feature, but each one is a silent trap waiting for the next dev.

The galling part: M2 and L1 were entirely preventable with one extra check — "what does a user see when this fails?" and "what would break if I add a field?" Instead we reached APPROVE_WITH_FIXES (7.5/10) and corrected on the spot. It stings because the fixes were one-liners and should have been instincts, not post-review discoveries.

## Technical Details

### @ExcelColumn Annotation

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ExcelColumn {
    String header();           // Column header text
    int order();               // Sort position (0-based)
    boolean required() default false;
    String dateFormat() default "yyyy-MM-dd";
}
```

Five lines. Carries all metadata needed by `ExcelMapper` to handle export/import. No generation, no XSD, no schema evolution — just fields marked inline.

### ExcelMapper (Generic, Reflection-driven)

`@Component` with three public methods:
- `export(List<T>, Class<T>, sheetName)` → `XSSFWorkbook` — reflection reads all @ExcelColumn fields, sorts by order, writes header + data rows with styled cells (header #BDD7EE, alternating white/gray rows, THIN borders, autosize columns)
- `importRow(String[], Class<T>)` → `T` — no-arg constructor + field.set() per reflection, required checks, type conversion via `ExcelValueCodec.parse()`, throws `IllegalArgumentException` on failure
- `importSheet(Sheet, Class<T>)` → `List<ImportResult<T>>` — single-thread POI reads, delegates per-row to `importRow`, collects ok/error results

**Thread Safety:** importRow takes only String[], never touches POI — safe to call from worker threads. importSheet is single-threaded (it does the POI reading).

**Design Extraction:** ExcelValueCodec split out as package-private static class (48 lines) — handles type conversion (String, BigDecimal, Integer, Long, LocalDate, LocalDateTime, Enum). Kept ExcelMapper to 178 lines (under 200-line limit).

### ExcelValueCodec (Type Conversion)

```java
static Object parse(String raw, Class<?> fieldType, String dateFormat) {
    if (fieldType == String.class)      return raw.trim();
    if (fieldType == BigDecimal.class)  return new BigDecimal(raw.replace(",", ""));
    if (fieldType == Integer.class | int.class)  return Integer.parseInt(raw.trim());
    // ... LocalDate, LocalDateTime, Enum with DateTimeFormatter
}
```

Covers all types used in BookingExcelRow and TourExcelRow. BigDecimal handles comma-separated numbers (common in localized Excel files). Enum resolution via `Enum.valueOf()`.

### DTOs (Mutable, Not Records)

Two DTOs both use Lombok (@Getter @Setter @NoArgsConstructor @AllArgsConstructor):

**BookingExcelRow** (8 fields)
- bookingCode, userEmail, tourName, participants, totalPrice, status, departureDate, createdAt
- Each annotated with @ExcelColumn (header, order, dateFormat where needed)

**TourExcelRow** (9 fields)
- title, description, price, durationDays, maxParticipants, departureLocation, destination, departureDate, categoryName
- Some marked `required=true` (title, price, durationDays, etc.)

**Why mutable classes, not records?** Reflection's field.set() requires the field to be mutable and the class to have a no-arg constructor. Records have final fields and no no-arg ctor. Mutable Lombok classes are the pragmatic choice.

### BookingExcelExporter Refactor (107 → 53 lines)

**Before:** Manually looped over bookings, set cells with POI API, applied styles inline
```java
Row header = sheet.createRow(0);
header.createCell(0).setCellValue("Booking Code");
// ... 100+ more lines of manual cell/style work
```

**After:** Single mapper call
```java
List<BookingExcelRow> rows = bookings.stream()
    .map(this::toRow)
    .toList();
return excelMapper.export(rows, BookingExcelRow.class, "Bookings");
```

Null guards moved into `toRow()` (e.g., `b.getUser() != null ? b.getUser().getEmail() : ""`).

### TourExcelImporter Refactor

Three key changes:

1. **Reflection-derived COLUMN_COUNT** (was `= 9`)
```java
private static final int COLUMN_COUNT = (int) Arrays.stream(TourExcelRow.class.getDeclaredFields())
        .filter(f -> f.isAnnotationPresent(ExcelColumn.class))
        .count();
```
Auto-syncs if a field is added/removed; no silent data truncation.

2. **`parseRow()` delegates to mapper**
```java
TourExcelRow row = excelMapper.importRow(cells, TourExcelRow.class);
```
Once mapper throws, catch and wrap with row number + domain validation.

3. **Domain rules preserved** — after mapper succeeds, importer still validates price > 0, departure date in future, category lookup, etc. Mapper handles type conversion only.

### ExcelMapperTest (7 Tests, All Passing)

1. `exportWritesHeadersInOrder` — verify headers match @ExcelColumn names in order
2. `exportFormatsValuesCorrectly` — LocalDate/LocalDateTime/BigDecimal formatting with custom dateFormat
3. `exportImportRoundTrip` — export → import returns identical values
4. `importRowSuccessWithValidTourExcelRow` — mapper creates tour instance with 9 fields set
5. `importRowMissingRequiredField` — required=true field missing → throws with field name
6. `importRowInvalidDate` — bad date format → throws, message includes column header
7. `importRowInvalidNumber` — bad number → throws, message includes column header

**Test count:** 7 unit tests. No Spring context (direct `new ExcelMapper()`). Passing: 8/8 including integration on ExcelExportServiceImpl.

## What We Tried

### Trial 1: Using Records for DTOs
```java
public record BookingExcelRow(String bookingCode, String userEmail, ...) {}
```
**Outcome:** REJECTED — `field.set()` fails on record components (final, no no-arg ctor). Switched to mutable Lombok classes.

### Trial 2: Magic Column Count
```java
private static final int COLUMN_COUNT = 9;  // TourExcelImporter
```
**Outcome:** RISKY in review (M2) — adding/removing a field breaks silently. Refactored to reflection-derived count at static init.

### Trial 3: Wide Exception Throws
```java
public <T> T importRow(String[] cells, Class<T> type) throws Exception
```
**Outcome:** DISCOVERED in review (M3) — throws Exception is too wide. Kept for now (field.set reflection throws checked exceptions), noted for future narrowing to ReflectiveOperationException.

### Trial 4: No Field Context on Parse Errors
```java
try {
    f.set(instance, ExcelValueCodec.parse(raw, f.getType(), ann.dateFormat()));
} catch (IllegalArgumentException e) {
    throw e;  // Original: propagates raw message
}
```
**Outcome:** FAILED in review (H1) — user gets "Invalid value: abc" with no column name. Fixed with wrapping:
```java
catch (IllegalArgumentException e) {
    throw new IllegalArgumentException(ann.header() + ": " + e.getMessage(), e);
}
```
Now user sees "Price: Invalid value: '0'" (column name + error detail).

### Trial 5: Weak Test Assertions
```java
@Test
void importRowInvalidDate() {
    // ...
    assertThat(e.getMessage()).isNotBlank();  // Too weak
}
```
**Outcome:** EXPOSED in review (L1) — test would pass even with generic error. Strengthened to:
```java
assertThat(e.getMessage()).containsIgnoringCase("Departure Date");
```

## Root Cause Analysis

### Why Records Failed for DTOs
Records (Java 16+) are immutable by design. All fields are final; no no-arg constructor is generated. Reflection's `field.set()` requires a writable field and typically a no-arg ctor to initialize the object. The mismatch surfaced immediately in the first `importRow` draft.

Lesson: new language features have implementation costs. Records are cleaner for immutable data, but reflection-based mapping code predates record adoption and still expects mutability.

### Why Column Count Became Magic
Early implementation used `COLUMN_COUNT = 9` (hardcoded). It worked because 9 matched TourExcelRow's field count. But the number wasn't derived — it was a copy-paste constant. If a field is added later (e.g., `guestNotes`), the importer would still expect 9 columns and silently drop the 10th column's data. This is dangerous because:
- No exception is raised (it reads cells[9] which exists, but discards it)
- The data silently vanishes (importer logs no warning)
- The next dev doesn't know they broke anything until a user reports missing data

Lesson: any constant that depends on another class's structure must be derived from that structure, not copied.

### Why Parse Errors Had No Column Name
`ExcelValueCodec.parse()` throws `IllegalArgumentException("Invalid value: '...'")` — it has no context about which column failed. The caller in `importRow` caught the exception and propagated it as-is. A user in the field sees "Invalid value: '2024-13-32'" and has to manually count columns to figure out that column 8 is the departure date. This is user-hostile.

Lesson: catch low-level exceptions and re-wrap with context (column name, row number, field type). The extra 5 lines of try/catch saved hours of user confusion.

### Why Test Assertions Were Weak
Tests 6 and 7 asserted `message.isNotBlank()` instead of checking content. This passes if ExcelMapper logs "Error on row 3" but the test never verifies which column failed. A future refactor might change the message format and the test would silently pass with wrong behavior.

Lesson: assertions must check the semantic meaning of the failure, not just its presence. "Error occurred" is different from "Price column error occurred".

## Lessons Learned

1. **Reflection patterns are not simplifed — they are deferred**. No boilerplate now means the complexity is hidden in annotation processing and field.set() calls. Design the error paths and null guards explicitly.

2. **Records and reflection don't mix easily**. Records are immutable by design; reflection-based mapping expects mutability. For reflection-heavy code, use mutable classes (Lombok helps) until the mapping becomes a pure data transformation (where records shine).

3. **Any constant tied to another class must be derived, not hardcoded**. COLUMN_COUNT depends on TourExcelRow's fields. Make it `reflection.count(fields matching @ExcelColumn)` at static init. One field added, the constant updates automatically. No guessing, no silent data loss.

4. **Errors need context to be useful**. "Invalid value: 'abc'" is useless in a 500-row file. Wrap low-level exceptions with field/row context before propagating. Users (and debuggers) will thank you.

5. **Weak test assertions hide bugs**. Testing `isNotBlank()` instead of message content catches "error occurred" but not "wrong error message". Check the semantic meaning, not just presence.

6. **Mutable DTOs are pragmatic for mapping**. Records are cleaner for immutable domain entities, but mapping code needs no-arg constructors and field mutability. Use Lombok mutable classes as transport layer, convert to domain entities after validation.

7. **Thread safety boundaries are self-enforcing in code structure**. Keeping POI reads on calling thread and worker threads operating only on String[] is not just a rule — it's a structural constraint that the code enforces. Comments explaining the boundary are a gift to the next reviewer.

## Next Steps

1. **(Already done, post-review)** H1 — field name context added to parse error messages in `importRow` (one-liner fix).

2. **(Already done, post-review)** M2 — COLUMN_COUNT now reflection-derived via `Arrays.stream(TourExcelRow.class.getDeclaredFields()).filter(@ExcelColumn).count()`.

3. **(Already done, post-review)** L1 — test assertions strengthened. Tests 6 and 7 now assert message `containsIgnoringCase("Departure Date")` and `containsIgnoringCase("Price")` respectively.

4. **(Defer to Day 5+)** M1 — `orderedFields()` called twice per row in importSheet. Cache the result or compute once before the loop. Low priority (10 000-row import = 20 000 reflection scans, unlikely to be wall-clock bottleneck on modern JVM).

5. **(Defer to Day 5+)** M3 — narrow `throws Exception` to `throws ReflectiveOperationException` for better caller safety. Safe refactor, low priority.

6. **(Defer to Day 5+)** L2, L3 — add empty-list export test and NUMERIC cell round-trip test. Behaviors are correct; test gaps only. Defer until test suite refresh.

7. **(Document)** Verify that BookingExcelExporter and TourExcelImporter parse rows maintain all domain validation rules (price > 0, future dates, category lookups). Post review: confirmed — all rules preserved after mapper delegates.

---

**Commit:**
- `e35484f` feat(excel): implement @ExcelColumn annotation and generic Reflection-based ExcelMapper

**Files touched:**
- `src/main/java/com/sunasterisk/bookingtours/excel/annotation/ExcelColumn.java` (created)
- `src/main/java/com/sunasterisk/bookingtours/excel/ExcelMapper.java` (created, 178 LOC)
- `src/main/java/com/sunasterisk/bookingtours/excel/ExcelValueCodec.java` (created, 48 LOC)
- `src/main/java/com/sunasterisk/bookingtours/excel/dto/BookingExcelRow.java` (created)
- `src/main/java/com/sunasterisk/bookingtours/excel/dto/TourExcelRow.java` (created)
- `src/main/java/com/sunasterisk/bookingtours/excel/BookingExcelExporter.java` (refactored: 107 → 53 lines)
- `src/main/java/com/sunasterisk/bookingtours/excel/TourExcelImporter.java` (refactored: COLUMN_COUNT reflection-derived, parseRow delegates)
- `src/test/java/com/sunasterisk/bookingtours/excel/ExcelMapperTest.java` (created, 7 tests)

**Evidence Gate:** Score 7.5 / 10. Findings: H1 (parse error message), M2 (magic COLUMN_COUNT), M3 (wide throws), L1 (weak test assertions), L2 (empty-list export test), L3 (numeric cell test). All H1, M2, L1 remedied post-review. M1, M3, L2, L3 deferred.

---

**Quality Metrics:**
- Tests: 8/8 passing (ExcelMapperTest + integration coverage)
- Build: SUCCESS
- Code coverage: reflection paths all exercised (export, import, required checks, type conversion)
- File sizes: ExcelMapper 178 lines (under 200 limit), ExcelValueCodec 48 lines, all others under 60 lines
- Refactoring impact: BookingExcelExporter shrank 54%; TourExcelImporter simplified row parsing
