# Day 4: File Handling, Multithreading & SOAP (T4.1–T4.8)

**Date**: 2026-08-03 09:30  
**Severity**: Medium  
**Component**: Excel export/import, SOAP currency conversion  
**Status**: Resolved  

## What Happened

Three feature groups shipped (T4.1–T4.8) in a single sprint: booking Excel export pipeline, multithreaded tour import with job tracking, and contract-first SOAP currency conversion. Integrated into existing admin UI. All tests green. Five logical commits to master. Evidence gate sealed at score 8.5 (0 critical findings).

## The Brutal Truth

This was a day of surprises and corrections. File handling in Spring Boot *looks* simple until you ship it — then the devil arrives: thread safety in POI parsing, transactional boundary gotchas with job tracking, SOAP endpoint collision with existing WebSocket infrastructure, and Java 21 / Spring Boot 4 compatibility land mines with JAXB. We didn't break anything, but we backtracked twice and caught ourselves only because review was meticulous.

The sting: `createdBy` stayed null across all import jobs because we never wired `UserRepository` to resolve the auth name. Simple fix, humbling reminder that injected dependencies have to be *used*. The second one still stings — transactional rollback on validation error was rolling back the job record itself (we wanted only the rows to fail gracefully). Moving the validation gate outside `@Transactional` was surgical, but should have been the first instinct.

SOAP endpoint collision was the wake-up call. Moving from `/ws/*` to `/soap/*` was two characters, but it meant understanding the exact servlet registration that SockJS / STOMP had already claimed. No amount of planning on paper catches servlet path conflicts — you have to boot it and watch the logs pile up.

## Technical Details

### Excel Export Pipeline (T4.1–T4.2)

**Implementation:**
- `BookingExcelExporter` — POI XSSFWorkbook with styled header (#BDD7EE) and alternating row colors (white / light gray)
- `ExcelExportService` + `ExcelExportServiceImpl` — wrapper layer calling exporter, passes `Pageable.unpaged()` to retrieve all matching records
- `AdminBookingController.exportBookings(...)` → `GET /admin/bookings/export` with filter passthrough (status, tourId, userId)
- Stream response via HttpServletResponse: `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` Content-Type
- UI: export button in `admin/bookings/list.html` wires to form POST to export endpoint

**Design choices:**
- No pagination in export (spec says "all matching") → `Pageable.unpaged()` loads complete result set
- POI XSSFWorkbook (Excel 2007+) chosen over HSSFWorkbook (older, less feature-rich)
- Styling at header only (less rendering weight than per-row, still readable)

**Error path:** missing TourController method early → caught in integration test

### Multithreaded Excel Import (T4.3–T4.6)

**The big architecture:**

1. **ThreadPoolTaskExecutor in AsyncConfig**
   ```
   importExecutor: core=5, max=10, queue=50, CallerRunsPolicy
   ```
   - Core threads (5) handle typical load; max threads (10) for spikes
   - Queue capacity (50) buffers jobs; when full, calling thread executes (graceful degradation)
   - Preferable to ThreadPoolExecutor for Spring lifecycle integration

2. **TourExcelImporter — Thread-Safe Parsing**
   - Reads all POI rows into `String[][]` on calling thread (single-threaded, no synchronization needed)
   - Fans out parsing/validation to worker threads via `CompletableFuture.supplyAsync(..., importExecutor)`
   - **Critical:** workers do NOT touch JPA, HTTP, or DB — pure compute (parse string, validate range, map lookups in preloaded maps)
   - Collects futures and joins: `CompletableFuture.allOf(...).join()` → wait for all parsing to complete before persist

3. **Job Lifecycle — Transactional Boundary Fix**
   - **Problem discovered in review (H2):** createTourImportJob was @Transactional, validation inside it; validation error → IllegalArgumentException → rollback of job record creation
   - **Solution:** Pre-validate row count (quick, no parsing) BEFORE creating job record
   - Validation errors now written to job's status field; job record persists regardless of row success/failure
   - Only the row parse/persist loop is @Transactional

4. **ExcelImportService + ExcelImportServiceImpl**
   - Orchestrates: file upload → validate → create job record → import → update job status
   - Pre-loads category lookups into Map<Long, Category> (eliminates N+1 in persist loop)
   - Passes map to importer, importer passes map to workers (immutable reference, thread-safe lookup)

5. **TourImportJob Entity**
   - Does NOT extend BaseEntity (no updated_at column) — import jobs are write-once, status changes don't need edit timestamp
   - Fields: id, fileName, status, totalRows, successRows, failureRows, errorDetails, createdAt
   - Flyway V9 migration creates table with NOT NULL constraints on timestamp/status/counts

6. **Admin UI**
   - `GET /admin/tours/import` → form + recent jobs list
   - `POST /admin/tours/import` → multipart file upload
   - `GET /admin/tours/import/template` → streams template Excel (empty headers)

**Decisions logged:**
- Async parse but sync persist (all rows batched in single transaction after parsing completes) — simpler, avoids per-row tx overhead
- Category preload required to avoid N+1; worth the memory footprint (categories << tours in count)

### Spring WS SOAP Currency Conversion (T4.7–T4.8)

**Contract-First Design:**

1. **XSD Schema (currency.xsd)**
   ```xml
   <xs:element name="CurrencyConversionRequest">
     <xs:complexType>
       <xs:sequence>
         <xs:element name="amount" type="xs:decimal"/>
         <xs:element name="fromCurrency" type="xs:string"/>
         <xs:element name="toCurrency" type="xs:string"/>
       </xs:sequence>
     </xs:complexType>
   </xs:element>
   <!-- CurrencyConversionResponse: amount (decimal), rate (decimal), targetCurrencyAmount (decimal) -->
   ```
   - JAXB classes written manually (NOT generated from XSD) for Java 21 compatibility
   - Reason: JAXB code-gen tools struggle with jakarta namespace on Boot 4 + Java 21
   - Classes: `CurrencyConversionRequest`, `CurrencyConversionResponse` (@XmlRootElement, @XmlElement on fields)

2. **CurrencyRateProvider**
   - VND pivot approach: all rates defined relative to VND, conversion via chain (VND→USD, VND→EUR, etc.)
   - Hardcoded demo rates (1 USD = 24,500 VND, 1 EUR = 26,500 VND, etc.)
   - Stateless, no caching (acceptable for low-traffic demo)

3. **CurrencyConversionEndpoint**
   - Spring WS @Endpoint (contract-first SOAP handler)
   - @PayloadRoot annotation maps namespace + localName to method
   - Returns `CurrencyConversionResponse` marshalled to XML by Spring WS
   - Null-safe: if rate lookup fails, returns response with null targetCurrencyAmount (no exception thrown)

4. **CurrencyConversionClient**
   - Extends WebServiceGatewaySupport (Spring WS client boilerplate)
   - Calls `localhost:8080/soap/currency` (self-call pattern)
   - Gracefully returns null on connection failure (wrapped in try/catch, logged)

5. **WebServiceConfig**
   - MessageDispatcherServlet on `/soap/*` (not `/ws/*`)
   - **Discovery (C1 in review):** original path was `/ws/*`; collision with SockJS websocket handler (also registered on `/ws`) caused routing chaos
   - Moved to `/soap/*`, verified no other servlet claims it, tests green
   - Endpoint scanning: `@EnableWs` auto-scans for @Endpoint beans

6. **TourController Integration**
   - `tourDetail(tourId)` calls CurrencyConversionClient twice: USD price, EUR price
   - Wires prices to model; template renders all three (VND + USD + EUR)
   - If SOAP call fails, prices remain null; template shows "N/A" gracefully

**Dependencies Added:**
- `org.springframework.ws:spring-ws-core` — SOAP framework
- `org.apache.cxf:cxf-rt-wsdl` — WSDL support
- `org.glassfish.jaxb:jaxb-runtime` — Jakarta namespace JAXB (java 21 compatible)
- Note: NOT `com.sun.xml.bind:jaxb-impl` (javax namespace, breaks on Boot 4 + Java 21)

**Error Trace (C1 during review):**
```
WARNING o.s.w.s.s.DispatcherServlet - No mapping found for HTTP request
Cause: MessageDispatcherServlet was on /ws/*, and SockJS already claimed /ws/* for WebSocket upgrade
```

## What We Tried

### Trial 1: Synchronous Parsing in Import Loop
```java
for (Row row : sheet) {
  String[] data = extractRowData(row);
  Tour tour = parseAndValidate(data);
  tourRepository.save(tour);
}
```
**Outcome:** Works but single-threaded, blocks on every row. Upgraded to async (T4.4).

### Trial 2: JPA Operations in Worker Threads
```java
supplyAsync(() -> {
  Tour tour = parseAndValidate(data);
  tourRepository.save(tour);  // <-- in worker thread
}, importExecutor)
```
**Outcome:** REJECTED — Hibernate sessions are thread-bound. Moved JPA to main thread post-parsing.

### Trial 3: No Job Record Pre-validation
```java
@Transactional
createTourImportJob(file) {
  TourImportJob job = new TourImportJob(file.getName(), ...);
  int rowCount = validateRows(file);  // <-- exception here rolls back job
  tourRepository.saveAll(parsed);
}
```
**Outcome:** FAILED in review (H2) — validation failure rolled back job record. Restructured: validate BEFORE @Transactional, create job only if valid.

### Trial 4: SOAP on /ws/* Servlet Path
```java
@Configuration
public class WebServiceConfig {
  @Bean
  MessageDispatcherServlet dispatcherServlet() {
    // registered on /ws/* by default
  }
}
```
**Outcome:** COLLISION with SockJS — both tried to claim `/ws/*`. Moved to `/soap/*` (C1).

### Trial 5: JAXB Code Generation from XSD
```bash
xjc -d src/main/java currency.xsd
```
**Outcome:** Generated classes use javax namespace, incompatible with Boot 4 + Java 21. Rewrote manually with jakarta namespace.

### Trial 6: No createdBy in Import Job
```java
TourImportJob job = TourImportJob.builder()
  .fileName(file.getName())
  .totalRows(rowCount)
  .build();  // <-- createdBy never set
```
**Outcome:** DISCOVERED in review (H1) — createdBy always null. Injected UserRepository, resolved from authentication.getName(), persisted to job.

### Trial 7: Stale SOAP Servlet Comments
```java
// MessageDispatcherServlet on /ws/* — WebSocket endpoint
@Bean
public ServletRegistrationBean messageDispatcherServlet() {
  // now actually on /soap/*, comment was wrong
}
```
**Outcome:** Post-review, comments corrected (both said `/ws/*` when code moved to `/soap/*`).

## Root Cause Analysis

### Why Thread Safety in POI Required Pre-buffering
POI's XSSFWorkbook is not thread-safe for cell reads. Early approach was to pass Row objects to workers; that exposed worker threads to POI's internal synchronization. Solution: read all rows to String arrays on calling thread (one-time cost), then pass immutable arrays to workers. Lesson: File format libraries have undocumented threading assumptions — test early.

### Why Job Record Validation Moved Outside @Transactional
The original code was:
```
@Transactional: try { validate → create job } catch (Exception) { rollback }
```
This means validation error → exception → rollback of job creation. We needed the job record to persist regardless of row success/failure (to track what was attempted). Lesson: transactional boundaries must wrap *what changes the data you care about*, not the whole flow.

### Why Scalar CategoryId Instead of @ManyToOne Category
Importer workers look up categories in a preloaded Map. Injecting JpaRepository into workers would require Hibernate sessions in worker threads (dangerous). Lookups happen in Map (memory), not DB. Lesson: pre-load reference data before fanning out to threads.

### Why SOAP Endpoint Collision Surfaced Late
Servlet path conflicts don't appear in code review — they appear at boot time, when multiple handlers try to claim the same path pattern. We caught it because integration tests spun up the full context. Lesson: concurrent servlet registration is a runtime property; tests must boot the full stack.

### Why JAXB Manual Classes Over Generation
Boot 4 + Java 21 requires jakarta.xml.bind namespace. The JAXB code generator (xjc) shipped in most tutorials produces javax namespace. Rather than fight the toolchain, we hand-wrote the three classes (~20 lines each). Lesson: code generation tools lag runtime evolution; sometimes a hand-written class is the pragmatic move.

### Why createdBy Was Null
The BaseEntity has @CreationTimestamp (auto-set by JPA) but `createdBy` is a manual field (String, requires assignment). We forgot to wire UserRepository and resolve `authentication.getName()`. Lesson: any field that is NOT @CreationTimestamp/@UpdateTimestamp requires explicit assignment in the calling code.

## Lessons Learned

1. **Thread-safe file parsing requires pre-buffering** — POI is not thread-safe for cell reads. Read entire row data to memory on calling thread, pass immutable data to workers. No shortcuts.

2. **Transactional boundaries must wrap the data you care about** — validation errors shouldn't rollback job creation if job creation is the artifact you want to keep. Restructure the call path: validate first, create job, then transact the persistence.

3. **Reference data pre-loading is non-negotiable in async** — Worker threads can't touch JPA without session madness. Preload Categories into a Map on calling thread, pass immutable reference to workers. Cost: one query + memory. Benefit: thread-safety + zero N+1.

4. **Servlet path collisions are runtime, not review** — Two MessageDispatcherServlets on the same path won't show up in static analysis. Boot context validation is essential. Tests must spin full context.

5. **JAXB namespace evolution requires pragmatism** — Code generators lag Java release cycles. Jakarta namespace (Java 21 default) is non-negotiable; generate-then-edit is messier than hand-writing 60 lines. Make the call quickly.

6. **Every field that isn't auto-set must be assigned explicitly** — BaseEntity's @CreationTimestamp is automatic; `createdBy` is not. Audit entity constructors: what's auto? What's manual? What will surprise the next dev?

7. **SOAP self-calls for demos are fine, but document the risk** — TourController calling localhost:8080/soap is acceptable for teaching contract-first SOAP. Production would route through load balancer or service mesh. Note it.

## Next Steps

1. **Thread pool tuning (optional, Day 5+):** Monitor importExecutor queue depth under realistic load (>1000 tours). If queue fills, may need to increase max threads or implement bounded queue rejection handler. Owner: perf review. Timeline: after metrics.

2. **SOAP currency rates hardening (optional, Day 5+):** Replace hardcoded rates with external currency API (OpenExchangeRates, Fixer) or cache with TTL. Current demo rates are fine for MVP. Owner: integration task. Timeline: pre-production.

3. **Import error detail expansion (optional, Day 6+):** Currently errorDetails is free-text. Schema a CSV or JSON error record per row (row number, field, error message). Owner: admin UX. Timeline: if error visibility becomes a blocker.

4. **Deferred:** N+1 audit on TourImportJob persistence loop (Category preload is done; audit other entity relationships). Owner: anyone touching importer. Timeline: next refactor.

5. **Code debt note:** SOAP endpoint is self-calling (localhost:8080/soap). Add comment in TourController.tourDetail() explaining this is for demo only; production uses service mesh or load balancer. Owner: anyone code-reviewing SOAP calls. Timeline: immediate.

---

**Commits:**
- `dba4eed` build(deps): add Apache POI, Spring WS, WSDL4J, JAXB-runtime (jakarta namespace)
- `ac11f47` feat(excel-export): booking export pipeline + UI (styled XLSX, filter passthrough)
- `e8c6ac4` feat(excel-import): multithreaded tour import + job tracking (thread pool, preloaded categories, transactional boundary fix)
- `91a7d8a` feat(soap-currency): contract-first SOAP endpoint + client + TourController integration (VND pivot, USD/EUR prices in tour detail)
- `b4fdbfc` docs: update roadmap, changelog, code standards, and add day 4 implementation plan

**Files touched:**
- `src/main/java/org/sun/booking/service/excel/BookingExcelExporter.java` (created)
- `src/main/java/org/sun/booking/service/excel/ExcelExportService.java` (created)
- `src/main/java/org/sun/booking/service/excel/impl/ExcelExportServiceImpl.java` (created)
- `src/main/java/org/sun/booking/service/excel/TourExcelImporter.java` (created)
- `src/main/java/org/sun/booking/service/excel/ExcelImportService.java` (created)
- `src/main/java/org/sun/booking/service/excel/impl/ExcelImportServiceImpl.java` (created)
- `src/main/java/org/sun/booking/entity/TourImportJob.java` (created)
- `src/main/java/org/sun/booking/repository/TourImportJobRepository.java` (created)
- `src/main/java/org/sun/booking/controller/AdminTourController.java` (modified: added import endpoints)
- `src/main/java/org/sun/booking/controller/AdminBookingController.java` (modified: added export endpoint)
- `src/main/java/org/sun/booking/config/AsyncConfig.java` (modified: added importExecutor ThreadPoolTaskExecutor)
- `src/main/java/org/sun/booking/soap/CurrencyConversionRequest.java` (created, JAXB manual)
- `src/main/java/org/sun/booking/soap/CurrencyConversionResponse.java` (created, JAXB manual)
- `src/main/java/org/sun/booking/soap/CurrencyRateProvider.java` (created)
- `src/main/java/org/sun/booking/soap/CurrencyConversionEndpoint.java` (created)
- `src/main/java/org/sun/booking/soap/CurrencyConversionClient.java` (created)
- `src/main/java/org/sun/booking/config/WebServiceConfig.java` (created)
- `src/main/java/org/sun/booking/controller/TourController.java` (modified: wired SOAP client, integrated prices into tour detail)
- `src/main/resources/xsd/currency.xsd` (created)
- `src/main/resources/db/migration/V9__Create_tour_import_jobs_table.sql` (created)
- `src/main/resources/templates/admin/tours/import.html` (created)
- `src/main/resources/templates/admin/bookings/list.html` (modified: added export button)
- `src/test/java/org/sun/booking/service/excel/ExcelExportServiceImplTest.java` (created)
- `src/test/java/org/sun/booking/service/excel/ExcelImportServiceImplTest.java` (created)
- `src/test/java/org/sun/booking/soap/CurrencyConversionEndpointTest.java` (created)

**Evidence Gate:** Score 8.5 / 10. Zero critical findings. Minor observations on SOAP self-call pattern and thread pool tuning (both logged for optional future work). Tests: 3/3 green. Build: SUCCESS.
