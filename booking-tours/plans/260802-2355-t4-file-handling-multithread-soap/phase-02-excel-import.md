# Phase 02 — importExecutor + Tour Excel Import + Admin UI

## Overview
- **Priority:** High
- **Status:** COMPLETED
- **Tasks:** T4.3, T4.4, T4.5
- **Completed:** 2026-08-03
- **Depends on:** Phase 01 (poi-ooxml already in pom.xml)

Add `importExecutor` thread pool, V9 migration, tour import job tracking, parallel row processing, admin upload UI.

## Requirements
- `importExecutor`: corePool=5, maxPool=10, queue=50, prefix=tour-import-, CallerRunsPolicy
- Each Excel row processed as `Callable<ImportRowResult>` submitted to `importExecutor`
- Failed rows do NOT stop the job — collected and reported
- Max 500 rows, max 5MB file
- Valid rows inserted via existing `tourService.create(TourRequest)` (reuse validation — DRY). Default status per `TourRequest`/`tourService` (do NOT bypass service to force INACTIVE unless a later requirement demands it; spec is silent).
- Flash message after redirect: "Imported X/Y rows successfully"

## Concurrency Safety (CRITICAL — High risk)
The request-thread JPA `EntityManager`/persistence context is NOT thread-safe and MUST NOT be shared across worker threads. Therefore:
- Worker `Callable`s do PARSING + FIELD VALIDATION ONLY (number/date parsing, blank/length checks, category-name→id lookup against a `Map<String,Long>` snapshot preloaded on the request thread). They return a parsed `TourRequest` OR a row error. NO repository / `tourService` / `EntityManager` access inside a worker.
- PERSISTENCE happens back on the request thread after `Future.get()`: iterate valid `TourRequest`s and call `tourService.create(req)` sequentially inside try/catch, accumulating success/fail.
- `ImportRowResult` therefore carries a `TourRequest` (or error), NOT a managed `Tour` entity.
Reviewer gate: confirm no JPA call exists inside any `Callable`.

## Template Columns
Title | Description | Price | Duration Days | Max Participants | Departure Location | Destination | Departure Date (yyyy-MM-dd) | Category Name

## Validation Per Row
- Title: required, max 255, not duplicate
- Price: required, > 0
- Duration Days: required, integer > 0
- Max Participants: required, integer > 0
- Departure Date: required, future date, format yyyy-MM-dd
- Category Name: must exist in `categories` table (null = uncategorized allowed)

## Related Code Files

**Modify:**
- `src/main/java/com/sunasterisk/bookingtours/config/AsyncConfig.java`
- `src/main/java/com/sunasterisk/bookingtours/controller/admin/AdminTourController.java`

**Create:**
- `src/main/resources/db/migration/V9__create_tour_import_jobs_table.sql`
- `src/main/java/com/sunasterisk/bookingtours/entity/TourImportJob.java`
- `src/main/java/com/sunasterisk/bookingtours/repository/TourImportJobRepository.java`
- `src/main/java/com/sunasterisk/bookingtours/excel/TourExcelImporter.java`
- `src/main/java/com/sunasterisk/bookingtours/service/ExcelImportService.java`
- `src/main/java/com/sunasterisk/bookingtours/service/impl/ExcelImportServiceImpl.java`
- `src/main/resources/templates/admin/tours/import.html`

## Implementation Steps

1. **V9 migration** — `V9__create_tour_import_jobs_table.sql`:
   ```sql
   CREATE TABLE tour_import_jobs (
       id BIGINT NOT NULL AUTO_INCREMENT,
       file_name VARCHAR(255) NOT NULL,
       status ENUM('PENDING','PROCESSING','COMPLETED','FAILED') NOT NULL DEFAULT 'PENDING',
       total_rows INT DEFAULT 0,
       success_rows INT DEFAULT 0,
       failed_rows INT DEFAULT 0,
       error_details MEDIUMTEXT,
       created_by BIGINT,
       created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
       completed_at DATETIME(6),
       PRIMARY KEY (id),
       CONSTRAINT fk_import_jobs_user FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL
   ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
   ```

2. **TourImportJob entity** — standalone `@Entity` (do NOT extend `BaseEntity`: the V9 table has `created_at`/`completed_at` but no `updated_at`). Explicit `LocalDateTime createdAt` (insertable=false, DB default) + `completedAt`. `createdBy` = plain `Long` column (no `@ManyToOne` needed). `error_details` → `@Column(columnDefinition="MEDIUMTEXT")`. Add enum `ImportJobStatus {PENDING, PROCESSING, COMPLETED, FAILED}` as `@Enumerated(STRING)`. Vietnamese javadoc.

3. **TourImportJobRepository** — extends `JpaRepository<TourImportJob, Long>`

4. **AsyncConfig** — add second bean:
   ```java
   @Bean(name = "importExecutor")
   public Executor importExecutor() {
       ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
       executor.setCorePoolSize(5);
       executor.setMaxPoolSize(10);
       executor.setQueueCapacity(50);
       executor.setThreadNamePrefix("tour-import-");
       executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
       executor.setWaitForTasksToCompleteOnShutdown(true);
       executor.setAwaitTerminationSeconds(30);
       executor.initialize();
       return executor;
   }
   ```

5. **TourExcelImporter** — `@Component`, inject `importExecutor` (`ThreadPoolTaskExecutor`) via `@Qualifier`:
   - Inner record: `ImportRowResult(int rowNum, boolean success, String error, TourRequest request)` — carries a DETACHED `TourRequest`, never a managed `Tour` (see Concurrency Safety).
   - Method: `List<ImportRowResult> parseRows(Sheet sheet, Map<String,Long> categoryByName)`
   - Read all rows into plain string arrays on the calling thread FIRST (POI `Sheet`/`Row` are not thread-safe), then submit one `Callable<ImportRowResult>` per row to the executor.
   - Worker parses cells → `TourRequest` + resolves categoryId from the passed map; on any error returns `ImportRowResult.error(rowNum, msg)`. No JPA.
   - Collect all `Future<ImportRowResult>`, `.get()` with timeout; return aggregated list.

6. **ExcelImportService** interface:
   ```java
   TourImportJob importTours(MultipartFile file, Long createdBy);
   XSSFWorkbook generateTemplate();
   ```

7. **ExcelImportServiceImpl** (inject `TourExcelImporter`, `TourService`, `CategoryService`, `TourImportJobRepository`):
   - Validate file: `.xlsx` extension, size ≤ 5MB, ≤ 500 data rows (else FAILED job + flash error)
   - Create `TourImportJob` with status=PROCESSING, save
   - Preload `Map<String,Long>` category-name→id from `categoryService.getAll()` (on request thread)
   - Open workbook via `WorkbookFactory.create(file.getInputStream())`
   - Call `importer.parseRows(sheet, categoryByName)` → parsed results (parallel parse)
   - For each valid result on the request thread: `try { tourService.create(req); success++ } catch(Exception e){ failed++; collect error }` (sequential persistence — see Concurrency Safety)
   - Update job: status=COMPLETED (FAILED only if unreadable/all failed), success_rows, failed_rows, error_details (line-per-error or JSON array of {row, reason}), completedAt
   - Return updated job

8. **AdminTourController** — add 3 endpoints + inject `ExcelImportService`:
   - `GET /admin/tours/import` → return view `admin/tours/import`; add `model.addAttribute("importJobs", last 10 jobs)`
   - `GET /admin/tours/import/template` → call `excelImportService.generateTemplate()`, write to response as attachment `tour_import_template.xlsx`
   - `POST /admin/tours/import` → call `excelImportService.importTours(file, currentUserId)`, add flash msg, redirect `/admin/tours/import`
   - Add Swagger annotations

9. **import.html** — Bootstrap 5 template:
   - File upload form (`enctype="multipart/form-data"`, Thymeleaf CSRF)
   - Download template link → `/admin/tours/import/template`
   - Flash message display (success/error)
   - Table of recent import jobs (file name, status, success/fail counts, date)

## Todo
- [x] V9 migration SQL
- [x] TourImportJob entity + enum
- [x] TourImportJobRepository
- [x] Add importExecutor to AsyncConfig
- [x] TourExcelImporter (parallel row processing)
- [x] ExcelImportService interface + impl
- [x] Admin import endpoints in AdminTourController
- [x] import.html template

## Success Criteria
- V9 migration applies cleanly
- `GET /admin/tours/import/template` downloads valid xlsx
- Uploading 20-row Excel → valid rows created via `tourService.create`, failed rows listed; one bad row does NOT abort the job (status COMPLETED)
- Thread names in logs: `tour-import-X` (proves parallel parse)
- Job status visible in import history table
- File > 500 rows or wrong extension rejected with clear flash error

## Risk Assessment
- **JPA in worker threads (High):** mitigated by design — workers parse only, persistence single-threaded. Reviewer must confirm no repo/`tourService` call inside any `Callable`.
- **POI Sheet/Row not thread-safe (Med):** read cells into plain string arrays on the request thread before submitting work.
- **`currentUserId` resolution (Low):** resolve from `Authentication` via the existing user lookup pattern; pass `null` if unresolved (column nullable, FK ON DELETE SET NULL).
- **File/MIME spoofing (Low):** check extension + wrap POI read in try/catch → FAILED job, not HTTP 500.
