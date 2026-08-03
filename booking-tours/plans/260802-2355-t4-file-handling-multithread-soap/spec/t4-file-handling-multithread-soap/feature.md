---
id: F-T4
title: Day 4 — File Handling, Multithread & SOAP
lang: vi
status: draft
created: 2026-08-02
---

# Day 4: File Handling, Multithread & SOAP

## Tổng quan

Ba nhóm tính năng song song cho ngày thứ Năm:

1. **Excel Export/Import (Apache POI)** — Admin xuất danh sách booking ra .xlsx; Admin import danh sách tour từ .xlsx với xử lý song song.
2. **ThreadPoolTaskExecutor (importExecutor)** — Pool riêng cho xử lý từng dòng Excel song song.
3. **SOAP Currency Service (Spring WS)** — Web service quy đổi tiền tệ; tour detail page hiển thị giá theo VND/USD/EUR.

## Phạm vi

### Trong scope
- T4.1: `BookingExcelExporter` + `ExcelExportService`
- T4.2: `GET /admin/bookings/export` endpoint
- T4.3: `importExecutor` bean trong `AsyncConfig`
- T4.4: V9 migration, `TourImportJob`, `TourExcelImporter`, `ExcelImportService`
- T4.5: Import UI — `GET/POST /admin/tours/import`, template download
- T4.6: Spring WS setup, `currency.xsd`, `WebServiceConfig`
- T4.7: `CurrencyConversionEndpoint`, `CurrencyRateProvider`
- T4.8: `CurrencyConversionClient`, tour detail hiển thị 3 currencies

### Ngoài scope
- Caching tỷ giá thực (dùng mock hardcoded)
- Import batching > 500 rows
- Async import status websocket push

## Actors & Use Cases

| Actor | Use Case |
|---|---|
| Admin | Xuất danh sách booking ra Excel |
| Admin | Upload file Excel để import tour (batch) |
| Guest/User | Xem giá tour theo VND, USD, EUR |
| System | SOAP endpoint quy đổi tiền tệ |

## API Contracts

### Excel Export
```
GET /admin/bookings/export?keyword=&status=&fromDate=&toDate=
Response: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
Content-Disposition: attachment; filename=bookings-YYYY-MM-DD.xlsx
```

### Excel Import
```
GET  /admin/tours/import           → show upload form
GET  /admin/tours/import/template  → download .xlsx template
POST /admin/tours/import           multipart/form-data field: file
     → redirect with flash: "Imported X/Y rows successfully"
```

### SOAP
```
GET /ws/currency.wsdl  → WSDL XML
POST /ws/currency      → SOAP request CurrencyConversionRequest
                       → SOAP response CurrencyConversionResponse
```

## Data Models

### TourImportJob (V9 migration)
```sql
CREATE TABLE tour_import_jobs (
  id            BIGINT NOT NULL AUTO_INCREMENT,
  file_name     VARCHAR(255) NOT NULL,
  status        ENUM('PENDING','PROCESSING','COMPLETED','FAILED') NOT NULL DEFAULT 'PENDING',
  total_rows    INT DEFAULT 0,
  success_rows  INT DEFAULT 0,
  failed_rows   INT DEFAULT 0,
  error_details MEDIUMTEXT,
  created_by    BIGINT,
  created_at    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  completed_at  DATETIME(6),
  PRIMARY KEY (id),
  CONSTRAINT fk_import_jobs_user FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### SOAP XSD Types
- `CurrencyConversionRequest`: amount (decimal), fromCurrency, toCurrency
- `CurrencyConversionResponse`: convertedAmount, rate, fromCurrency, toCurrency

## Exchange Rates (mock hardcoded)
| Currency | Rate (1 unit = X VND) |
|---|---|
| VND | 1.0 |
| USD | 25,500 |
| EUR | 27,800 |
| JPY | 170 |
| KRW | 18.5 |

## Excel Columns

### Export (Bookings)
Booking Code · User Email · Tour Name · Participants · Total Price (VND) · Status · Departure Date · Created Date

### Import Template (Tours)
Title · Description · Price · Duration Days · Max Participants · Departure Location · Destination · Departure Date (yyyy-MM-dd) · Category Name

## Acceptance Criteria

- [ ] `GET /admin/bookings/export` tải về .xlsx với styled header (bold, blue bg)
- [ ] `POST /admin/tours/import` xử lý song song qua `importExecutor`, trả kết quả success/fail
- [ ] `GET /ws/currency.wsdl` trả WSDL hợp lệ
- [ ] `/tours/{id}` hiển thị giá theo VND, USD, EUR
- [ ] Import rows có lỗi không crash toàn bộ job
- [ ] V9 migration apply clean
