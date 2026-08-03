---
doc_type: system-architecture
status: forward-draft
created: 2026-08-02
task: Day 4 — File Handling, Multithread & SOAP
---

# System Architecture — Day 4 Additions

## New Layers / Integrations

### 1. Excel Layer (`excel/` package)
- `BookingExcelExporter` — builds `XSSFWorkbook` from booking list; styled header, auto-size columns
- `TourExcelImporter` — reads rows via Apache POI; submits `Callable<ImportRowResult>` per row to `importExecutor`; aggregates results

### 2. SOAP Layer (`soap/` package)
- `CurrencyConversionEndpoint` — Spring WS `@Endpoint`; handles `CurrencyConversionRequest`; returns `CurrencyConversionResponse`
- `CurrencyRateProvider` — in-memory `Map<String, BigDecimal>` of mock exchange rates (VND base)
- `CurrencyConversionClient` — `WebServiceGatewaySupport`; calls local SOAP endpoint; used by `TourController`

### 3. Config Changes
- `AsyncConfig`: new `importExecutor` bean (corePool=5, maxPool=10, queue=50, threadPrefix=tour-import-)
- `WebServiceConfig`: Spring WS servlet registration, WSDL definition at `/ws/currency`

### 4. DB Layer
- `V9__create_tour_import_jobs_table.sql` — tracks async import job status per file upload

## Data Flow

### Excel Export
```
Admin → GET /admin/bookings/export
        ↓
AdminBookingController → ExcelExportService
        ↓
BookingExcelExporter.generateBookingReport(List<Booking>)
        ↓
HttpServletResponse (Content-Type: .xlsx, attachment)
```

### Excel Import
```
Admin → POST /admin/tours/import (MultipartFile)
        ↓
AdminTourController → ExcelImportService
        ↓
TourImportJob (status=PROCESSING, saved to DB)
        ↓
TourExcelImporter: per-row Callable → importExecutor
        ↓
Future<ImportRowResult> aggregation
        ↓
TourImportJob (status=COMPLETED, success/fail counts)
        ↓
Flash message redirect
```

### SOAP Currency Flow
```
TourController → CurrencyConversionClient.convertPrice(amount, "VND", "USD")
               → CurrencyConversionClient.convertPrice(amount, "VND", "EUR")
               ↓
WebServiceGatewaySupport → POST /ws/currency (local SOAP)
               ↓
CurrencyConversionEndpoint → CurrencyRateProvider.convert(...)
               ↓
CurrencyConversionResponse
               ↓
TourController → model: priceUsd, priceEur
               ↓
templates/tours/detail.html: 3-currency price display
```

## Package Structure (new)
```
com.sunasterisk.bookingtours/
├── excel/
│   ├── BookingExcelExporter.java
│   └── TourExcelImporter.java
├── soap/
│   ├── CurrencyConversionEndpoint.java
│   ├── CurrencyRateProvider.java
│   └── CurrencyConversionClient.java
├── entity/
│   └── TourImportJob.java            (new)
├── repository/
│   └── TourImportJobRepository.java  (new)
├── service/
│   ├── ExcelExportService.java       (new)
│   └── ExcelImportService.java       (new)
│   └── impl/
│       ├── ExcelExportServiceImpl.java
│       └── ExcelImportServiceImpl.java
└── config/
    ├── AsyncConfig.java              (add importExecutor)
    └── WebServiceConfig.java         (new)
```
