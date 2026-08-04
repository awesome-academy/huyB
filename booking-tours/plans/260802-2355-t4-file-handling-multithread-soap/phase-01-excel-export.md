# Phase 01 — Apache POI + Booking Excel Export

## Overview
- **Priority:** High
- **Status:** COMPLETED
- **Tasks:** T4.1, T4.2
- **Completed:** 2026-08-03

Add Apache POI dependency, build `BookingExcelExporter`, wire `ExcelExportService`, expose `GET /admin/bookings/export`.

## Requirements
- Styled header: bold, light blue fill (`#BDD7EE`), border
- Alternating row colors: white / `#F5F5F5`
- Auto-size all columns
- Filename: `bookings-YYYY-MM-DD.xlsx`
- Same filters as list page: keyword, status, fromDate, toDate

## Related Code Files

**Modify:**
- `pom.xml` — add `poi-ooxml:5.3.0`
- `src/main/java/com/sunasterisk/bookingtours/controller/admin/AdminBookingController.java`

**Create:**
- `src/main/java/com/sunasterisk/bookingtours/excel/BookingExcelExporter.java`
- `src/main/java/com/sunasterisk/bookingtours/service/ExcelExportService.java`
- `src/main/java/com/sunasterisk/bookingtours/service/impl/ExcelExportServiceImpl.java`

## Implementation Steps

1. **pom.xml** — add inside `<dependencies>`:
   ```xml
   <dependency>
       <groupId>org.apache.poi</groupId>
       <artifactId>poi-ooxml</artifactId>
       <version>5.3.0</version>
   </dependency>
   ```

2. **BookingExcelExporter** — `@Component`:
   - Method: `XSSFWorkbook export(List<Booking> bookings)`
   - Sheet name: `"Bookings"`
   - Header row: `CellStyle` with `FillPatternType.SOLID_FOREGROUND`, IndexedColors.LIGHT_BLUE, `BorderStyle.THIN`, bold font
   - Columns: Booking Code | User Email | Tour Name | Participants | Total Price (VND) | Status | Departure Date | Created Date
   - Data rows: alternating white / `#F5F5F5` (custom color via `XSSFColor`)
   - `sheet.autoSizeColumn(i)` for each column after all rows written

3. **ExcelExportService** interface:
   ```java
   XSSFWorkbook exportBookings(String keyword, BookingStatus status, LocalDate fromDate, LocalDate toDate);
   ```

4. **ExcelExportServiceImpl**:
   - Inject `BookingService` + `BookingExcelExporter`
   - Call `bookingService.search(keyword, status, fromDate, toDate, Pageable.unpaged())` (existing method — `BookingService.java:67`) and take `.getContent()` to fetch all matching bookings
   - NOTE: `Pageable.unpaged()` must not break the existing `search` query's `JOIN FETCH`; if the repo query uses count-query pagination that rejects unpaged, add a dedicated `List<Booking> searchAll(...)` overload instead. Verify at implementation time.
   - Pass to `BookingExcelExporter.export()`

5. **AdminBookingController** — add endpoint:
   ```java
   @GetMapping("/export")
   public void exportBookings(/* same params as list */, HttpServletResponse response) throws IOException {
       String filename = "bookings-" + LocalDate.now() + ".xlsx";
       response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
       response.setHeader("Content-Disposition", "attachment; filename=" + filename);
       XSSFWorkbook wb = excelExportService.exportBookings(keyword, status, fromDate, toDate);
       wb.write(response.getOutputStream());
       wb.close();
   }
   ```
   - Inject `ExcelExportService`
   - Add Swagger `@Operation(summary = "Export bookings to Excel")`

6. Add "Export Excel" button to `templates/admin/bookings/list.html` (if it exists) — `<a href="/admin/bookings/export?...">Export Excel</a>`

## Todo
- [x] Add poi-ooxml to pom.xml
- [x] Create BookingExcelExporter
- [x] Create ExcelExportService interface
- [x] Create ExcelExportServiceImpl
- [x] Add export endpoint to AdminBookingController
- [x] Add Export button to booking list template

## Success Criteria
- `GET /admin/bookings/export` returns 200 with Content-Type `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`
- Downloaded file opens in Excel with correct columns and styled header
- Filters (status, date range) applied correctly to exported data
