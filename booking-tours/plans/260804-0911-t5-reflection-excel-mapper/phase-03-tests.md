# Phase 03 — ExcelMapperTest Unit Tests

## Overview
- Priority: P2
- Status: completed
- Depends on: Phase 1 (mapper API), Phase 2 (DTOs)
- Prove the mapper via export round-trip + import valid/invalid rows. Pure unit tests — no
  Spring context, no DB, no POI files on disk (in-memory workbook).

## Files to create
- `src/test/java/com/sunasterisk/bookingtours/excel/ExcelMapperTest.java`

## Key insight
Round-trip must reuse the same DTO type both directions to prove annotation-driven symmetry.
Use `BookingExcelRow` for export round-trip (has all supported types incl. two date formats)
and `TourExcelRow` for import valid/invalid (has required flags).

## Implementation steps
1. Instantiate `ExcelMapper` directly (`new ExcelMapper()` — it's a plain `@Component` with no
   injected deps). JUnit 5 (`org.junit.jupiter`), AssertJ if present else plain asserts.
2. **Test: export writes header + values.** Build 2 `BookingExcelRow`; `export(rows, .class,
   "Bookings")`; assert sheet name, row 0 headers equal Day-4 `HEADERS`, and cell strings match
   formatted values (esp. `departureDate` = `dd/MM/yyyy`, `createdAt` = `dd/MM/yyyy HH:mm`,
   `totalPrice` = plain string, `status` = enum name).
3. **Test: export→import round-trip.** From the exported sheet call `importSheet(sheet,
   BookingExcelRow.class)`; assert each `ImportResult.success()` and field values equal the
   originals (dates parsed back through the same `dateFormat`). Confirms format symmetry.
4. **Test: import valid TourExcelRow rows.** Craft a `Sheet` (header + 2 valid rows) via an
   in-memory `XSSFWorkbook`; `importSheet(sheet, TourExcelRow.class)`; assert both succeed and
   fields populated (price BigDecimal, durationDays Integer, departureDate LocalDate).
5. **Test: import missing required field.** Row with blank `title`; assert `!success()` and
   error mentions the header ("Title is required" / "Title").
6. **Test: import bad date format.** Row with `departureDate = "31-02-2026"`; assert `!success()`
   and error mentions invalid date / the raw value.
7. **Test: import bad number.** Row with `price = "abc"`; assert `!success()`.
8. Add small helper `sheetWith(String[]... rows)` to build header+data sheet in memory (DRY).
9. `./mvnw test -Dtest=ExcelMapperTest` green, then full `./mvnw test` for regression.

## Todo
- [x] ExcelMapperTest created, JUnit 5, no Spring context
- [x] export header+value assertions (both date formats, BigDecimal, enum)
- [x] export→import round-trip equality
- [x] import valid rows populate fields
- [x] import missing-required → error with header
- [x] import bad date → error
- [x] import bad number → error
- [x] `-Dtest=ExcelMapperTest` green + full `./mvnw test` green

## Success criteria
- All ExcelMapperTest cases pass; no `@SpringBootTest` (fast, isolated).
- Full test suite still green (no regression from Phase 2 refactor).

## Test matrix
| Concern | Level | Case |
|---------|-------|------|
| Field ordering by `order()` | unit | header sequence assertion |
| Type formatting | unit | BigDecimal/date/enum export cells |
| Format symmetry | unit | export→import round-trip |
| Required validation | unit | blank title error |
| Parse failure | unit | bad date, bad number |
| Refactor regression | integration | existing import/export tests via full `./mvnw test` |

## Risks
| Risk | L×I | Countermove |
|------|-----|-------------|
| Locale-dependent date/number parse | Low×Med | Use fixed `ofPattern`; no locale-sensitive default |
| In-memory sheet cells typed NUMERIC vs STRING | Med×Med | `readCells` mirrors Day-4 `getCellString` type-switch |

## Rollback
Delete the test file — no production impact.
