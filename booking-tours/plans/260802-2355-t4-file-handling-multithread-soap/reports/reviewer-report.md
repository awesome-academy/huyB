# Day 4 Implementation Review

**Branch:** task_98831  
**Files reviewed:** 17 new + 7 modified  
**Reviewer:** Staff Engineer — production-readiness pass

---

## Overall Assessment

Solid implementation of three independent features (Excel export/import, multithreaded row parsing, SOAP currency). The concurrency design is correct and well-commented. Core findings are one critical path-collision bug, one broken audit trail, and two medium-priority issues around N+1 queries and incomplete JSON escaping.

---

## Critical

### C1 — `/ws/*` SOAP servlet intercepts SockJS sub-requests (URL collision)

**Location:** `WebServiceConfig.java:28` + `WebSocketConfig.java:21`

`MessageDispatcherServlet` is registered on `/ws/*` (path-prefix). SockJS makes sub-requests to `/ws/info`, `/ws/{server}/{session}/websocket`, etc. — all of which match `/ws/*` and land in the SOAP dispatcher instead of the Spring MVC dispatcher. This silently breaks WebSocket (SockJS falls back to XHR polling, which also hits SOAP). Both features existed before Day 4, but the SOAP servlet registration is new and the collision was not present before.

**Fix:** Move SOAP to an unambiguous prefix, e.g. `/soap/*`:

```java
// WebServiceConfig.java
return new ServletRegistrationBean<>(servlet, "/soap/*");

// WebServiceConfig.java — WSDL definition locationUri
def.setLocationUri("/soap");

// AsyncConfig default URL property
@Value("${soap.currency.url:http://localhost:8080/soap}")

// SecurityConfig — CSRF ignore, permitAll
.ignoringRequestMatchers("/soap/**")
.requestMatchers("/soap/**").permitAll()
```

Update `CurrencyConversionClient` default value accordingly. This is a **runtime regression** — notifications stop working the moment the SOAP servlet is deployed.

---

## High

### H1 — `createdBy` always NULL — audit trail broken

**Location:** `AdminTourController.java:321`

```java
TourImportJob job = excelImportService.importTours(file, null);  // <-- null
```

`Authentication authentication` is a method parameter (already injected) but never used. The FK `tour_import_jobs.created_by` is always NULL, defeating the per-admin audit trail the schema was designed for.

**Fix:**
```java
Long adminId = userService.findByEmail(authentication.getName()).getId();
TourImportJob job = excelImportService.importTours(file, adminId);
```
Or resolve the ID inside `ExcelImportServiceImpl` using `SecurityContextHolder` if you want to keep the service interface clean.

### H2 — `IllegalArgumentException` thrown inside `@Transactional` rolls back the `TourImportJob` record

**Location:** `ExcelImportServiceImpl.java:47,66,75`

The job is `save()`-d on line 66 with status `PROCESSING`, then line 75 throws `IllegalArgumentException` (unchecked). Spring's default `@Transactional` rolls back on unchecked exceptions, so the job record is **never committed** — the controller gets an exception and the admin has no record of the failed attempt.

Either:
- Validate MAX_ROWS **before** saving the job, or
- Use `@Transactional(noRollbackFor = IllegalArgumentException.class)` + set job status to FAILED before rethrowing, or
- Save the job in a separate transaction (`REQUIRES_NEW`) before entering the main one.

---

## Medium

### M1 — N+1 DB queries in import loop (up to 500 × 2 queries)

**Location:** `ExcelImportServiceImpl.java:100,107`

For every successful row:
- `tourRepository.existsByTitleIgnoreCase(r.title())` — one SELECT per row
- `categoryRepository.findById(r.categoryId())` — one SELECT per row

With 500 rows that is up to 1 000 additional queries inside a single transaction. `categoryByName` is already preloaded as `Map<String, Long>` (good). The category entity itself is fetched again unnecessarily because the mapper only stored the ID.

**Fix:** Preload a `Map<Long, Category>` on the calling thread alongside `categoryByName`, then use `categoryById.get(r.categoryId())` in the loop without any extra DB call. For title deduplication, collect all incoming titles and call one `findAllByTitleIn` query, building a `Set<String>` before the loop.

### M2 — `escape()` does not handle control characters in JSON strings

**Location:** `ExcelImportServiceImpl.java:167-170`

`escape()` escapes `\` and `"` but not `\n`, `\r`, `\t`. DB constraint violations and other `Exception` messages routinely contain newlines. The resulting `errorDetails` JSON is syntactically invalid for those rows, breaking any downstream parser (e.g. if the error JSON is later parsed for analytics or a frontend reads it as JSON).

**Fix:**
```java
private String escape(String s) {
    if (s == null) return "";
    return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
}
```

---

## Low / Observations

### L1 — SOAP default URL hardcoded to port 8080

**Location:** `CurrencyConversionClient.java:22`

`@Value("${soap.currency.url:http://localhost:8080/ws}")` — no entry in `application.properties` overrides this. If the port changes (e.g. for testing or behind a proxy), currency conversion silently returns `null`. Add `soap.currency.url=http://localhost:${server.port}/ws` (or `/soap` after C1 fix) to `application.properties`.

### L2 — `(long)` cast on numeric cell values truncates decimals

**Location:** `TourExcelImporter.java:214,217`

For `NUMERIC` and formula-result-NUMERIC cells, the code casts to `long` before converting to String. Price in Excel is often a decimal (e.g., `1 500 000.50`). The cast silently drops the fractional part. Since the price field ultimately becomes `BigDecimal`, consider using `String.valueOf(cell.getNumericCellValue())` (or `BigDecimal.valueOf(cell.getNumericCellValue()).toPlainString()`) to preserve the full value; the `.replace(",", "")` in the price parser already handles thousands separators.

### L3 — Booking export has no row-count guard

**Location:** `ExcelExportServiceImpl.java:31`

`Pageable.unpaged()` with a broad filter could return tens of thousands of rows, materializing them all into a single `XSSFWorkbook` in heap. Export is ADMIN-only (mitigates external abuse), but a large dataset will OOM the JVM. Acceptable for a demo project; in production add a `MAX_EXPORT_ROWS` guard and return HTTP 413 or a streaming response.

### L4 — `createAt` initialized via `LocalDateTime.now()` at field-default time

**Location:** `TourImportJob.java:55`

`@Builder.Default private LocalDateTime createdAt = LocalDateTime.now()` evaluates at object construction time, not at `INSERT` time. This is fine for this use-case since jobs are constructed and saved immediately, but differs from `@CreationTimestamp` / `DEFAULT CURRENT_TIMESTAMP` semantics and could silently drift if the object is cached before saving.

---

## Design Decisions — Verified

| Decision | Status |
|---|---|
| POI cells read to `String[][]` on calling thread before fan-out | Correct — lines 87-101 of `TourExcelImporter` |
| Workers only parse/validate, no JPA | Correct — `parseRow()` has no repository access |
| `CompletableFuture.supplyAsync` with `Executor` (not `ExecutorService`) | Correct — `@Qualifier("importExecutor") private Executor importExecutor` |
| SOAP client calls back into same app at `/ws` | Intentional demo — documented in code; see C1 for path collision side-effect |
| `org.glassfish.jaxb:jaxb-runtime` (jakarta namespace) | Confirmed in `pom.xml:171-172` |
| `TourImportJob` does NOT extend `BaseEntity` | Correct — separate `createdAt`/`completedAt`, no `updatedAt` |

---

## Summary

| Severity | Count | Items |
|---|---|---|
| Critical | 1 | C1 (`/ws/*` collision) |
| High | 2 | H1 (null createdBy), H2 (IAE rolls back job record) |
| Medium | 2 | M1 (N+1 import loop), M2 (JSON escape incomplete) |
| Low | 4 | L1–L4 |

**Deploy recommendation:** Fix C1 before deploying. H1 and H2 break observable behavior (audit trail, error UX) but are not data-loss risks. The rest are improvements.

---

**Status:** DONE_WITH_CONCERNS  
**Summary:** One critical runtime regression (SOAP /ws/* intercepts SockJS traffic) and two high-priority correctness bugs found. All concurrency design decisions verified correct.
