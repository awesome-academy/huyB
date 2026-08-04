---
title: "T5 — Reflection-based Excel Mapper"
description: "Replace hardcoded Excel column logic with @ExcelColumn annotation + generic Reflection mapper"
status: completed
priority: P2
effort: 5h
branch: task_98831
tags: [excel, reflection, refactor, training]
created: 2026-08-04
---

# T5 — Reflection-based Excel Mapper

Replace hardcoded column logic in `BookingExcelExporter` + `TourExcelImporter` with an
annotation-driven (`@ExcelColumn`) generic `ExcelMapper<T>` using standard Java Reflection.
Output Excel and import behavior must stay byte/behavior-identical to Day 4.

## Phases

| # | Phase | Status | Depends on |
|---|-------|--------|-----------|
| 1 | [Annotation + ExcelMapper](phase-01-annotation-and-mapper.md) | completed | — |
| 2 | [DTOs + refactor exporter/importer](phase-02-dtos-and-refactor.md) | completed | 1 |
| 3 | [ExcelMapperTest unit tests](phase-03-tests.md) | completed | 1, 2 |

## Key dependencies

- Phase 2 & 3 both need the `ExcelMapper` API from Phase 1 → build Phase 1 first.
- Phase 3 tests exercise final refactored code → runs after Phase 2.

## Critical design decisions (see phases for detail)

1. **DTOs are mutable classes, NOT records.** T5.3 requires no-arg constructor + `field.set()`
   injection; records have no no-arg constructor and final fields. Use Lombok
   `@NoArgsConstructor @Getter @Setter`. (Spec text says "record" — resolved as class.)
2. **Mapper does not read POI on worker threads.** `TourExcelImporter` keeps its POI-read →
   `String[]` → fan-out design (POI not thread-safe). Mapper exposes `importRow(String[], Class<T>)`
   run on workers; `importSheet(Sheet, Class<T>)` is a single-thread convenience for tests.
3. **Domain validation stays in the importer.** `@ExcelColumn(required)` covers null/blank + type
   parse only. Business rules (price>0, future date, len≤255, category lookup) remain in
   `TourExcelImporter`.

## Success criteria

- `./mvnw test -Dtest=ExcelMapperTest` green; full `./mvnw test` regression green.
- Exported Bookings.xlsx identical headers/style/columns/date-format vs Day 4.
- Tour import success/error counts unchanged for the same input file.
- No public API change to `ExcelExportService` / `ExcelImportService`.
