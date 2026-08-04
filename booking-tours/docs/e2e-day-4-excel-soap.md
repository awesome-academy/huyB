# E2E Testing — Day 4: Excel Export/Import + SOAP Currency

> Quay lại: [E2E Testing Guide](e2e-testing-guide.md)

## Prerequisites

```bash
# MySQL đang chạy, app đã qua Day 3
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

App sẵn sàng khi log xuất hiện: `Started BookingToursApplication in X.XXX seconds`

> **Scope:** T4.1 Excel Export · T4.2 Excel Import (multithreaded + job tracking) · T4.3 SOAP Currency

---

## Part 1: Excel Export Booking (T4.1)

### T4.1.1 — Download template không filter

1. Đăng nhập với tài khoản ADMIN
2. Vào `http://localhost:8080/admin/bookings`
3. Click nút **Export Excel** (không chọn filter nào)

**Expected:**
- Trình duyệt tải xuống file `bookings_<ngày-hôm-nay>.xlsx`
- File có 1 sheet tên `Bookings`
- Dòng header (row 1) màu xanh `#BDD7EE`, chữ đậm
- 8 cột đúng thứ tự: `Booking Code`, `User Email`, `Tour Name`, `Participants`, `Total Price (VND)`, `Status`, `Departure Date`, `Created Date`
- Số dòng dữ liệu = số booking trong DB

```bash
# Kiểm tra số booking trong DB
mysql -u root -p'Aa@123456' booking_tours -e "SELECT COUNT(*) FROM bookings;"
```

---

### T4.1.2 — Export với filter status

1. Vào `/admin/bookings?status=CONFIRMED`
2. Click **Export Excel**

**Expected:**
- File chỉ chứa booking có `status = CONFIRMED`
- Cột `Status` của mọi dòng = `CONFIRMED`

---

### T4.1.3 — Export với filter keyword + ngày

1. Vào `/admin/bookings?keyword=<email-user>&fromDate=2024-01-01&toDate=2026-12-31`
2. Click **Export Excel**

**Expected:**
- File chỉ chứa booking khớp filter
- Cột `User Email` chứa keyword tìm kiếm

---

### T4.1.4 — Export khi không có booking nào khớp

1. Filter với keyword không tồn tại (vd: `?keyword=xxxxxxxxxx`)
2. Click **Export Excel**

**Expected:**
- File tải về hợp lệ (không lỗi 500)
- File chỉ có dòng header, không có dòng data

---

### T4.1.5 — Verify format cell

Mở file Excel vừa tải:

| Cột | Kiểm tra |
|-----|----------|
| `Total Price (VND)` | Số, không có dấu `.0` thừa (stripTrailingZeros) |
| `Departure Date` | Format `dd/MM/yyyy` (vd: `25/12/2025`) |
| `Created Date` | Format `dd/MM/yyyy HH:mm` (vd: `01/08/2026 09:30`) |
| `User Email` | Chuỗi, không null (booking không có user → hiện chuỗi rỗng `""`) |
| Cột auto-size | Không bị tràn chữ, các cột vừa nội dung |

---

## Part 2: Excel Import Tour (T4.2)

### T4.2.1 — Download template

1. Vào `http://localhost:8080/admin/tours/import`
2. Click **Download Template**

**Expected:**
- Tải file `tour_import_template.xlsx`
- 1 sheet tên `Tours`, 1 dòng header với 9 cột:
  `Title`, `Description`, `Price`, `Duration Days`, `Max Participants`, `Departure Location`, `Destination`, `Departure Date (yyyy-MM-dd)`, `Category Name`

---

### T4.2.2 — Import file hợp lệ

Tạo file `.xlsx` từ template với 3 dòng dữ liệu hợp lệ (dùng category seed sẵn có):

```
Title         | Description | Price    | Duration Days | Max Participants | Departure Location | Destination | Departure Date | Category Name
Tour Test E2E | Mô tả test  | 5000000  | 3             | 20               | Hà Nội             | Đà Nẵng     | 2027-01-15     | Du lịch biển
Tour Test E2E2| Mô tả test2 | 8000000  | 5             | 15               | TP.HCM             | Phú Quốc    | 2027-02-20     | Du lịch nghỉ dưỡng
Tour Test E2E3| Mô tả test3 | 12000000 | 7             | 10               | Đà Nẵng            | Hội An      | 2027-03-10     | Du lịch văn hóa
```

> Category được tra cứu **case-insensitive** và **trim** — `Du lịch biển`, `du lịch biển`, `  Du Lịch Biển  ` đều hợp lệ.

1. Vào `/admin/tours/import`
2. Chọn file → Click **Import**

**Expected:**
- Flash message: `Import completed: 3 success, 0 failed`
- Bảng **Recent Jobs** hiển thị job mới nhất với `status = COMPLETED`, `success_rows = 3`, `failed_rows = 0`

Verify DB:
```sql
SELECT t.id, t.title, t.status, c.name AS category
FROM tours t LEFT JOIN categories c ON t.category_id = c.id
WHERE t.title LIKE 'Tour Test E2E%'
ORDER BY t.id DESC LIMIT 3;
-- 3 dòng, status = 'INACTIVE', category đúng với Category Name đã nhập

SELECT * FROM tour_import_jobs ORDER BY created_at DESC LIMIT 1;
-- status = 'COMPLETED', total_rows = 3, success_rows = 3, failed_rows = 0
```

---

### T4.2.3 — Import file có lỗi validation

Tạo file với 1 dòng hợp lệ và 2 dòng lỗi:

```
Title             | Price  | Duration Days | Max Participants | Departure Location | Destination | Departure Date
Tour Valid        | 100000 | 3             | 10               | Hà Nội             | Đà Nẵng     | 2027-06-01
                  | 100000 | 3             | 10               | Hà Nội             | Đà Nẵng     | 2027-06-01   ← Title bỏ trống (required)
Tour Invalid Date | 100000 | 3             | 10               | Hà Nội             | Đà Nẵng     | 2020-01-01   ← Ngày quá khứ
```

**Expected:**
- Flash message: `Import completed: 1 success, 2 failed`
- Job DB: `success_rows = 1`, `failed_rows = 2`
- `error_details` JSON chứa 2 entry với `row` và `reason`:
  - Row 2: `Title is required`
  - Row 3: `Departure date must be in the future`

```sql
SELECT error_details FROM tour_import_jobs ORDER BY created_at DESC LIMIT 1;
-- [{"row":2,"reason":"Title is required"},{"row":3,"reason":"Departure date must be in the future"}]
```

---

### T4.2.4 — Import title trùng

Upload file có title giống tour đã tồn tại trong DB:

```sql
-- Lấy title của một tour đang có
SELECT title FROM tours LIMIT 1;
```

Tạo file với dòng có title đó → Upload.

**Expected:**
- `failed_rows = 1`, `success_rows = 0`
- `error_details`: `"reason":"Title '<tên>' already exists"`

---

### T4.2.5 — Import file không phải .xlsx

Upload file `.csv` hoặc `.txt`.

**Expected:**
- Flash message lỗi: `Chỉ chấp nhận file .xlsx`
- Không có job mới trong `tour_import_jobs`

---

### T4.2.6 — Import file vượt 5MB

**Expected:**
- Flash message lỗi: `File vượt quá giới hạn 5MB`
- Không có job mới

---

### T4.2.7 — Verify multithreading (parse song song)

1. Tạo file có 100 dòng hợp lệ
2. Bật DEBUG logging:

```properties
# application-dev.properties
logging.level.com.sunasterisk=DEBUG
```

3. Upload file và kiểm tra log:

```bash
grep "importExecutor\|import-worker" logs/app.log | head -20
```

**Expected:**
- Log xuất hiện nhiều thread `import-worker-X` (X = 1, 2, 3, ...) xử lý song song
- Các dòng parse không tuần tự theo rowNum (bằng chứng song song)
- Kết quả cuối vẫn đúng thứ tự dòng (collect in order)

---

### T4.2.8 — Verify job tracking page

1. Vào `/admin/tours/import`
2. Kiểm tra bảng **Recent Jobs**

**Expected:**
- Hiển thị tối đa 20 job gần nhất
- Mỗi job có: `ID`, `File Name`, `Status`, `Total/Success/Failed rows`, `Created At`, `Completed At`
- Job `COMPLETED` hiển thị màu xanh (hoặc badge success)
- Job `FAILED` hiển thị màu đỏ

---

## Part 3: SOAP Currency Conversion (T4.3)

### T4.3.1 — WSDL accessible

```bash
curl -s http://localhost:8080/soap/currency.wsdl | grep -o "<wsdl:portType.*>" | head -3
# Expected: <wsdl:portType name="CurrencyPort">
```

**Expected:** WSDL trả về XML hợp lệ, không có 404/500.

---

### T4.3.2 — Giá USD/EUR hiển thị trên trang chi tiết tour

1. Vào `http://localhost:8080/tours` → chọn một tour ACTIVE có giá
2. Xem trang chi tiết

**Expected:**
- Hiển thị giá VND (vd: `5,000,000 VND`)
- Hiển thị giá USD tương đương (vd: `≈ 200.0000 USD`)
- Hiển thị giá EUR tương đương (vd: `≈ 185.1852 EUR`)
- Nếu SOAP lỗi → không crash trang, giá USD/EUR ẩn hoặc hiển thị dấu `-`

---

### T4.3.3 — Verify tỉ giá đúng

Lấy giá một tour từ DB:

```sql
SELECT id, price FROM tours WHERE status = 'ACTIVE' LIMIT 1;
-- Giả sử price = 5000000
```

Tính thủ công:
- `5000000 VND ÷ 25000 = 200.0000 USD`
- `5000000 VND ÷ 27000 = 185.1852 EUR`

So sánh với giá hiển thị trên trang chi tiết tour đó.

---

### T4.3.4 — Test SOAP endpoint trực tiếp (SoapUI hoặc curl)

```bash
curl -s -X POST http://localhost:8080/soap \
  -H "Content-Type: text/xml" \
  -d '<?xml version="1.0"?>
<soapenv:Envelope
  xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
  xmlns:cur="http://bookingtours.sunasterisk.com/currency">
  <soapenv:Header/>
  <soapenv:Body>
    <cur:CurrencyConversionRequest>
      <cur:amount>1000000</cur:amount>
      <cur:fromCurrency>VND</cur:fromCurrency>
      <cur:toCurrency>USD</cur:toCurrency>
    </cur:CurrencyConversionRequest>
  </soapenv:Body>
</soapenv:Envelope>'
```

**Expected response:**
```xml
<SOAP-ENV:Envelope ...>
  <SOAP-ENV:Body>
    <ns2:CurrencyConversionResponse ...>
      <ns2:convertedAmount>40.0000</ns2:convertedAmount>
      <ns2:rate>0.00004000</ns2:rate>
      <ns2:fromCurrency>VND</ns2:fromCurrency>
      <ns2:toCurrency>USD</ns2:toCurrency>
    </ns2:CurrencyConversionResponse>
  </SOAP-ENV:Body>
</SOAP-ENV:Envelope>
```

---

### T4.3.5 — Currency không được hỗ trợ

```bash
curl -s -X POST http://localhost:8080/soap \
  -H "Content-Type: text/xml" \
  -d '<?xml version="1.0"?>
<soapenv:Envelope
  xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
  xmlns:cur="http://bookingtours.sunasterisk.com/currency">
  <soapenv:Header/>
  <soapenv:Body>
    <cur:CurrencyConversionRequest>
      <cur:amount>1000000</cur:amount>
      <cur:fromCurrency>VND</cur:fromCurrency>
      <cur:toCurrency>JPY</cur:toCurrency>
    </cur:CurrencyConversionRequest>
  </soapenv:Body>
</soapenv:Envelope>'
```

**Expected:**
- SOAP Fault response (HTTP 500 với body `<faultstring>Unsupported currency: JPY</faultstring>`)
- Trang chi tiết tour không crash (client bắt exception → trả về null → model không có giá JPY)

---

### T4.3.6 — Tour không có giá (price = null)

```sql
-- Tạo tour test không có giá (hoặc tìm tour có price null)
UPDATE tours SET price = NULL WHERE id = <id>;
```

Vào trang chi tiết tour đó.

**Expected:**
- Không gọi SOAP (block `if (tour.getPrice() != null)` → skip)
- Không có lỗi NPE hay 500
- Giá USD/EUR không hiển thị

Khôi phục:
```sql
UPDATE tours SET price = 1000000 WHERE id = <id>;
```

---

## Checklist tổng kết Day 4

| # | Test case | Pass |
|---|-----------|------|
| T4.1.1 | Export tất cả booking → file .xlsx hợp lệ, 8 cột đúng | ☐ |
| T4.1.2 | Export filter status → chỉ booking khớp | ☐ |
| T4.1.3 | Export filter keyword + ngày → chỉ booking khớp | ☐ |
| T4.1.4 | Export không có kết quả → file chỉ có header | ☐ |
| T4.1.5 | Format date `dd/MM/yyyy`, số không có `.0` thừa | ☐ |
| T4.2.1 | Download template → 9 cột đúng | ☐ |
| T4.2.2 | Import 3 dòng hợp lệ → `success=3`, `failed=0` | ☐ |
| T4.2.3 | Import có lỗi → `success=1`, `failed=2`, error JSON đúng | ☐ |
| T4.2.4 | Import title trùng → fail với thông báo `already exists` | ☐ |
| T4.2.5 | Upload file không phải .xlsx → lỗi validation | ☐ |
| T4.2.6 | Upload file > 5MB → lỗi validation | ☐ |
| T4.2.7 | 100 dòng → log xuất hiện nhiều `import-worker-X` song song | ☐ |
| T4.2.8 | Trang import hiển thị 20 job gần nhất đúng | ☐ |
| T4.3.1 | `GET /soap/currency.wsdl` → WSDL XML hợp lệ | ☐ |
| T4.3.2 | Trang chi tiết tour ACTIVE hiển thị giá USD + EUR | ☐ |
| T4.3.3 | Tỉ giá đúng: VND/25000 = USD, VND/27000 = EUR | ☐ |
| T4.3.4 | SOAP curl trực tiếp → response XML đúng | ☐ |
| T4.3.5 | Currency không hỗ trợ → SOAP Fault, trang không crash | ☐ |
| T4.3.6 | Tour price = null → không gọi SOAP, trang không crash | ☐ |
