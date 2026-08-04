# Phase 01 — @ExcelColumn Annotation + ExcelMapper<T>

## Overview
- Priority: P1 (foundation — phases 2 & 3 depend on this API)
- Status: completed
- Build the annotation and the generic Reflection mapper. No refactor of callers yet.

## Files to create
- `src/main/java/com/sunasterisk/bookingtours/excel/annotation/ExcelColumn.java`
- `src/main/java/com/sunasterisk/bookingtours/excel/ExcelMapper.java`

## Key insights
- Export uses `dd/MM/yyyy` for `LocalDate`, `dd/MM/yyyy HH:mm` for `LocalDateTime` in Day 4.
  Per-field `dateFormat()` must carry these exact patterns to keep output identical.
- Header style `#BDD7EE` bold + THIN borders; alternating row fill `#FFFFFF` / `#F5F5F5`;
  `autoSizeColumn` per column. Port these verbatim from Day 4 `BookingExcelExporter`.
- Mapper must not touch POI on worker threads → import splits into POI-read (single thread)
  vs `String[]→T` (worker-safe, no POI).

## Implementation steps
1. `ExcelColumn.java`: `@Retention(RUNTIME) @Target(FIELD)` with `String header()`,
   `int order()`, `boolean required() default false`, `String dateFormat() default "yyyy-MM-dd"`.
2. `ExcelMapper<T>` `@Component` (Spring bean, no generic type on bean — methods take `Class<T>`).
   Add private helper `orderedFields(Class<?>)`: `getDeclaredFields()` → filter has `@ExcelColumn`
   → sort by `order()` → `setAccessible(true)`. Cache not required (YAGNI).
3. Public `ImportResult<T>` record: `(int rowNum, boolean success, T value, String error)` with
   static `ok(rowNum, value)` / `error(rowNum, msg)` factories.
4. `export(List<T> rows, Class<T> type)` → `XSSFWorkbook`:
   - Build header/even/odd styles (port `buildHeaderStyle`/`buildRowStyle`/`setBorders`).
   - Sheet name = `type.getSimpleName()` stripped of `ExcelRow` suffix, else caller-provided —
     KEEP configurable: add overload `export(rows, type, sheetName)`; exporter passes "Bookings".
   - Header row from `field.getAnnotation(ExcelColumn.class).header()`.
   - Data rows: `field.get(obj)` → `formatValue(value, dateFormat)` → `cell.setCellValue(String)`.
     Null → "". Alternating style by `rowIdx % 2`.
   - `autoSizeColumn` for each column.
5. `formatValue(Object, String dateFormat)`: switch on type — `String` as-is;
   `BigDecimal.toPlainString()`; `Integer`/`Long` `String.valueOf`; `LocalDate`/`LocalDateTime`
   via `DateTimeFormatter.ofPattern(dateFormat)`; `Enum` → `.name()`; else `String.valueOf`.
6. `importRow(String[] cells, Class<T> type)` → `T` (throws on parse/required failure):
   - Instantiate via `type.getDeclaredConstructor().newInstance()`.
   - For each ordered field at index `i`: read `cells[i]`; if required && blank → throw
     `IllegalArgumentException("<header> is required")`; else `parseValue(raw, fieldType, dateFormat)`
     → `field.set(instance, parsed)`. Blank optional → leave null.
7. `parseValue(String, Class<?> fieldType, String dateFormat)`: inverse of `formatValue`.
   `String`→trim; `BigDecimal`→`new BigDecimal(raw.replace(",",""))`; `Integer`/`Long`→parse;
   `LocalDate`/`LocalDateTime`→`parse` with formatter; `Enum`→`Enum.valueOf`. Wrap failures in
   `IllegalArgumentException("Invalid <header>: '<raw>'")`.
8. `importSheet(Sheet, Class<T>)` → `List<ImportResult<T>>` (single-thread convenience, tests use it):
   read cells row 1..lastRowNum into `String[]` (reuse a `readCells` helper mirroring Day 4
   `getCellString`), call `importRow`, wrap ok/error per row with `rowNum = r+1`.
9. Keep file < 200 lines. If it grows, extract `formatValue`/`parseValue` into a package-private
   `ExcelValueCodec` helper class.

## Todo
- [x] ExcelColumn.java created (4 attributes, correct meta-annotations)
- [x] ExcelMapper.export(List,Class) + overload with sheetName
- [x] ExcelMapper.importRow(String[],Class) worker-safe, no POI
- [x] ExcelMapper.importSheet(Sheet,Class) returns List<ImportResult<T>>
- [x] ImportResult<T> record with ok/error factories
- [x] formatValue/parseValue cover String,BigDecimal,Integer,Long,LocalDate,LocalDateTime,Enum
- [x] `./mvnw compile` clean

## Success criteria
- Compiles clean; no external mapping lib imports (only `java.lang.reflect`, POI, JDK time).
- `export` reproduces Day-4 header text, `#BDD7EE` header, alt-row fill, borders, autosize.
- `importRow` on a valid `String[]` returns a populated instance; required-blank throws.

## Risks
| Risk | L×I | Countermove |
|------|-----|-------------|
| Record final-field injection fails | Med×High | DTOs are mutable classes (Phase 2), not records |
| Date format drift vs Day 4 | Med×High | Per-field `dateFormat`; assert in Phase 3 round-trip |
| POI read on worker thread | Low×High | `importRow` takes `String[]`, never `Cell` |

## Rollback
Delete both new files — no caller references them yet. Zero blast radius.
