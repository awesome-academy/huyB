# Phase 02 — DTOs + Refactor Exporter/Importer

## Overview
- Priority: P1
- Status: completed
- Depends on: Phase 1 (ExcelMapper API)
- Introduce annotated DTOs and rewire the two Day-4 classes to use the mapper. Public service
  API stays unchanged. Output/behavior identical.

## Files to create
- `src/main/java/com/sunasterisk/bookingtours/excel/dto/BookingExcelRow.java`
- `src/main/java/com/sunasterisk/bookingtours/excel/dto/TourExcelRow.java`

## Files to modify
- `excel/BookingExcelExporter.java` — drop hardcoded cell logic, delegate to mapper
- `excel/TourExcelImporter.java` — drop `COL_*`, use `mapper.importRow` inside worker
- `service/impl/ExcelExportServiceImpl.java` — map `Booking` → `BookingExcelRow`
- `service/impl/ExcelImportServiceImpl.java` — no signature change; still receives `ImportRowResult`

## Key insight — DTOs are classes, not records
T5.3 mandates no-arg constructor + `field.set`. Records have neither. Use Lombok
`@Getter @Setter @NoArgsConstructor @AllArgsConstructor`. Fields carry `@ExcelColumn`.

## Implementation steps
1. `BookingExcelRow` (8 fields, mutable class): `bookingCode`(0), `userEmail`(1), `tourName`(2),
   `participants:Integer`(3), `totalPrice:BigDecimal`(4), `status:String`(5),
   `departureDate:LocalDate`(6, `dateFormat="dd/MM/yyyy"`), `createdAt:LocalDateTime`(7,
   `dateFormat="dd/MM/yyyy HH:mm"`). Headers match Day-4 `HEADERS[]` exactly.
2. `TourExcelRow` (9 fields, mutable class): `title`(0), `description`(1), `price:BigDecimal`(2),
   `durationDays:Integer`(3), `maxParticipants:Integer`(4), `departureLocation`(5),
   `destination`(6), `departureDate:LocalDate`(7, `dateFormat="yyyy-MM-dd"`), `categoryName`(8).
   Mark `required=true` on title/price/durationDays/maxParticipants/departureLocation/
   destination/departureDate (matches Day-4 required checks). Headers match `TEMPLATE_HEADERS`.
3. `BookingExcelExporter`: inject `ExcelMapper`. Add private `toRow(Booking)` that null-safely
   builds a `BookingExcelRow` (reuse Day-4 null guards: user/tour/status/date). `export(List<Booking>)`
   → map to rows → `mapper.export(rows, BookingExcelRow.class, "Bookings")`. Delete
   `HEADERS`, `setCell`, style builders (now in mapper). Keep public signature `export(List<Booking>)`.
4. `TourExcelImporter`: keep `parseRows(Sheet, Map)` structure + threading + `ImportRowResult`
   (unchanged public contract used by service). Inside `parseRow(rowNum, cells, categoryByName)`:
   replace manual per-field parse with `mapper.importRow(cells, TourExcelRow.class)` wrapped in
   try/catch → on `IllegalArgumentException` return `ImportRowResult.error(rowNum, e.getMessage())`.
   Then apply DOMAIN rules on the populated `TourExcelRow`: len≤255, price>0, duration>0,
   maxParticipants>0, departureDate.isAfter(now), category lookup via `categoryByName`. Map to
   `ImportRowResult.ok(...)`. Keep `readCells`/`getCellString` on calling thread (POI-safe).
5. `ExcelExportServiceImpl`: no change needed if exporter keeps `export(List<Booking>)` — verify
   only. (Booking→Row mapping lives in the exporter, step 3.)
6. `ExcelImportServiceImpl`: no change — still calls `parseRows` and reads `ImportRowResult`.
7. `./mvnw compile` then hand to reviewer.

## Todo
- [x] BookingExcelRow class (8 @ExcelColumn, correct dateFormats, headers match Day 4)
- [x] TourExcelRow class (9 @ExcelColumn, required flags, headers match template)
- [x] BookingExcelExporter delegates to mapper; old cell/style code removed
- [x] TourExcelImporter.parseRow uses mapper.importRow + keeps domain validation + threading
- [x] Service impls verified unchanged (public API intact)
- [x] `./mvnw compile` clean

## Success criteria
- Booking export byte-identical (headers, `#BDD7EE`, alt rows, borders, `dd/MM/yyyy[ HH:mm]`).
- Tour import produces same success/failed counts + same error messages for a given file.
- No changes to `ExcelExportService`/`ExcelImportService` interfaces.

## Risks
| Risk | L×I | Countermove |
|------|-----|-------------|
| Error-message text drift breaks import UX | Med×Med | Keep domain-rule messages verbatim in importer |
| Sheet name / column order change | Low×High | Pass "Bookings"; order() mirrors Day-4 indices |
| Threading regression (mapper on worker) | Low×High | importRow is POI-free; POI read stays pre-fanout |

## File ownership
Phases run sequentially — no parallel file contention. Phase 2 owns all 6 files above.

## Rollback
Revert the 4 modified files to Day-4 versions; delete 2 DTOs. Mapper (Phase 1) can remain
unused without harm.
