---
title: "Day 4 — File Handling, Multithread & SOAP"
description: "Excel export/import (Apache POI) with parallel import via importExecutor, plus a SOAP currency-conversion service consumed by tour detail."
status: completed
priority: P2
effort: 8h
branch: task_98831
tags: [excel, poi, multithread, soap, spring-ws, day4]
created: 2026-08-03
completed: 2026-08-03
spec_draft: plans/260802-2355-t4-file-handling-multithread-soap/spec/t4-file-handling-multithread-soap/
spec_lang: vi
---

# Plan: Day 4 — File Handling, Multithread & SOAP

## Overview

Sprint Day 4 thêm 3 nhóm tính năng vào hệ thống SUN Booking Tours:
1. Excel Export/Import với Apache POI
2. ThreadPoolTaskExecutor cho xử lý import song song
3. SOAP Web Service quy đổi tiền tệ

## Phases

| Phase | Description | Status | Depends on |
|---|---|---|---|
| [Phase 01](phase-01-excel-export.md) | Apache POI + Booking Excel Export (T4.1, T4.2) | COMPLETED | — |
| [Phase 02](phase-02-excel-import.md) | importExecutor + Tour Excel Import + Admin UI (T4.3–T4.5) | COMPLETED | 01 (POI dep only) |
| [Phase 03](phase-03-soap-service.md) | Spring WS SOAP Currency Service + Tour Detail (T4.6–T4.8) | COMPLETED | — |

Phase 03 is fully independent (different packages/files) — parallel-runnable with 01/02.
Phase 02 leans on Phase 01 only for the `poi-ooxml` dep already in `pom.xml`; no source-file overlap.

## Key Dependencies

- `poi-ooxml:5.3.0` — Phase 01 & 02
- `spring-boot-starter-web-services` (Spring WS) + `wsdl4j` + JAXB runtime — Phase 03
- V9 migration — Phase 02 (next free version; V6 skipped historically)
- `AsyncConfig.importExecutor` — Phase 02

## File Ownership (no overlap between phases)

- **01:** `excel/BookingExcelExporter`, `service(.impl)/ExcelExport*`, `AdminBookingController`, `admin/bookings/list.html`
- **02:** `excel/TourExcelImporter`, `service(.impl)/ExcelImport*`, `AsyncConfig`, `AdminTourController`, `entity/TourImportJob`, `repository/TourImportJobRepository`, `V9`, `admin/tours/import.html`
- **03:** `soap/*`, `config/WebServiceConfig`, `wsdl/currency.xsd`, `TourController`, `tours/detail.html`
- **`pom.xml`** touched by all three — add all deps in one upfront edit to avoid conflicts.

## Security (no change required)

`SecurityConfig` already `permitAll` + CSRF-ignores `/ws/**`; `/admin/**` already `hasRole("ADMIN")`. New admin export/import endpoints and the SOAP endpoint are covered by existing rules.

## High Risks (see phases for mitigations)

- **Parallel import + JPA `EntityManager`** — the shared request-thread persistence context is not thread-safe. Worker `Callable`s must only parse/validate rows (build detached `TourRequest`/`Tour`), never touch JPA; persistence happens back on the aggregating thread. (Phase 02)
- **JAXB codegen on Spring Boot 4 / Java 21** — jakarta binding + plugin compatibility. Verify generated package compiles. (Phase 03)
- **POI heap on large files** — export unbounded, import capped at 500 rows (spec out-of-scope beyond). (Phase 01/02)

## Definition of Done (measurable)

- `GET /admin/bookings/export` → valid `.xlsx`, styled header, same filters as list.
- `POST /admin/tours/import` → rows processed via `importExecutor`; one bad row does not abort; flash `X/Y`.
- `GET /ws/currency.wsdl` → valid WSDL; `/tours/{id}` shows VND/USD/EUR.
- `V9` applies clean; `mvn compile` passes after each phase.

## New Files Summary

### Phase 01
- `excel/BookingExcelExporter.java`
- `service/ExcelExportService.java` + `impl/ExcelExportServiceImpl.java`
- Modify: `controller/admin/AdminBookingController.java` (add export endpoint)

### Phase 02
- `db/migration/V9__create_tour_import_jobs_table.sql`
- `entity/TourImportJob.java`
- `repository/TourImportJobRepository.java`
- `excel/TourExcelImporter.java`
- `service/ExcelImportService.java` + `impl/ExcelImportServiceImpl.java`
- Modify: `config/AsyncConfig.java` (add importExecutor)
- Modify: `controller/admin/AdminTourController.java` (add import endpoints)
- `templates/admin/tours/import.html`

### Phase 03
- `resources/wsdl/currency.xsd`
- `config/WebServiceConfig.java`
- `soap/CurrencyConversionEndpoint.java`
- `soap/CurrencyRateProvider.java`
- `soap/CurrencyConversionClient.java`
- Modify: `controller/TourController.java` (inject client, add prices to model)
- Modify: `templates/tours/detail.html` (show VND/USD/EUR)
