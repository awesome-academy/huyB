---
feature: T5 — Reflection-based Excel Mapper
slug: t5-reflection-excel-mapper
lang: vi
status: draft
spec_draft: true
---

# T5 — Áp dụng Reflection để map dữ liệu Excel ↔ Entity

## Mục tiêu

Thay thế logic cột hardcoded trong `BookingExcelExporter` và `TourExcelImporter` bằng
cơ chế **annotation-driven + Reflection**:

- Khai báo mapping bằng `@ExcelColumn` trực tiếp trên fields của DTO
- `ExcelMapper<T>` generic đọc annotation qua Reflection để export và import

## Acceptance Criteria

| ID   | Tiêu chí |
|------|---------|
| T5.1 | `@ExcelColumn(header, order, required, dateFormat)` annotation định nghĩa trong package `excel.annotation` |
| T5.2 | `ExcelMapper<T>.export(List<T>, Class<T>)` trả về `XSSFWorkbook` có header + styling; thứ tự cột theo `order`; giá trị field lấy qua Reflection |
| T5.3 | `ExcelMapper<T>.importSheet(Sheet, Class<T>)` trả về `List<ImportResult<T>>`; mỗi row tạo instance T qua no-arg constructor + field injection qua Reflection; lỗi parse thu thập per-row |
| T5.4 | `BookingExcelRow` record (8 fields) annotated `@ExcelColumn`; `BookingExcelExporter` dùng `ExcelMapper<BookingExcelRow>` thay vì hardcoded setCell |
| T5.5 | `TourExcelRow` record (9 fields) annotated `@ExcelColumn`; `TourExcelImporter.parseRow()` dùng `ExcelMapper<TourExcelRow>` thay hằng số `COL_*` |
| T5.6 | Unit tests `ExcelMapperTest`: export round-trip (write → read → assert values), import valid rows, import invalid rows (required field missing, bad date format) |

## Thiết kế

### `@ExcelColumn`

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ExcelColumn {
    String header();                     // tên cột hiển thị
    int    order();                      // vị trí cột (0-based)
    boolean required() default false;   // bắt buộc khi import
    String  dateFormat() default "yyyy-MM-dd";
}
```

### `ExcelMapper<T>`

- **Export pipeline:**
  1. `getDeclaredFields()` → filter `@ExcelColumn` → sort by `order`
  2. Tạo header row với header style `#BDD7EE`
  3. Mỗi instance: `field.setAccessible(true)` → `field.get(obj)` → format → `cell.setCellValue`
  4. Supported types: `String`, `BigDecimal`, `Integer`, `Long`, `LocalDate`, `LocalDateTime`, `Enum`

- **Import pipeline:**
  1. Row 0 bỏ qua (header); từ row 1 đọc cells
  2. Map field theo `order` index
  3. Parse String → field type (BigDecimal, LocalDate, Integer, Enum…)
  4. Required check: field == null hoặc blank → error
  5. Trả về `ImportResult<T>` (rowNum, success, instance|errorMsg)

### DTOs

**`BookingExcelRow`** (record, 8 fields):
- bookingCode (order=0), userEmail (1), tourName (2), participants (3),
  totalPrice (4), status (5), departureDate (6), createdAt (7)

**`TourExcelRow`** (record, 9 fields — thay thế `COL_*` constants):
- title (order=0), description (1), price (2), durationDays (3),
  maxParticipants (4), departureLocation (5), destination (6),
  departureDate (7), categoryName (8)

### Refactoring scope

| File | Thay đổi |
|------|---------|
| `BookingExcelExporter` | Xóa manual cell logic → gọi `excelMapper.export(rows, BookingExcelRow.class)` |
| `TourExcelImporter` | `parseRow()` → dùng `ExcelMapper.importSheet()` trả về `ImportResult<TourExcelRow>` → convert sang `ImportRowResult` |
| `ExcelExportServiceImpl` | Convert `Booking` → `BookingExcelRow` trước khi gọi mapper |
| `ExcelImportServiceImpl` | Nhận `List<ImportResult<TourExcelRow>>` từ importer, convert sang logic hiện tại |

## Ràng buộc

- Không đổi public API của `ExcelExportService` / `ExcelImportService`
- `ExcelMapper` chỉ dùng Java standard Reflection — không dùng external libs (MapStruct, ModelMapper)
- Field injection qua Reflection (không phải setter) — DTOs là records, không có setters
- Regression: output Excel cuối cùng giống hệt với Day 4 (header, style, cột)

## Unresolved Questions

_(none)_
