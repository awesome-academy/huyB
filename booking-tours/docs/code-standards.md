# Code Standards — SUN Booking Tours

**Stack:** Spring Boot 4.0.6 · Java 21 · MySQL 8 · Thymeleaf  
**Last updated:** 2026-08-03

---

## General Conventions

- Java naming: PascalCase for classes, camelCase for methods/fields, UPPER_SNAKE_CASE for constants
- File size: keep each Java file under 200 lines; extract helpers or inner concerns into separate classes
- No unused imports, no raw types, no suppressed warnings without comment explaining why
- Every `catch` block must either rethrow, log, or handle — never silently swallow

---

## Package Layout

```
com.sunasterisk.bookingtours/
├── config/         Spring beans, security, async, messaging configs
├── controller/     MVC + REST controllers (admin/ sub-package for admin routes)
├── dto/            Request/response DTOs; no JPA annotations
├── entity/         JPA entities; no business logic
├── excel/          Apache POI exporters and importers
├── filter/         Servlet filters (MDC logging, etc.)
├── messaging/      activemq/ and rabbitmq/ sub-packages
├── repository/     Spring Data JPA repositories
├── scheduler/      @Scheduled jobs
├── service/        Interfaces + impl/ sub-package for implementations
└── soap/           Spring WS endpoint, client, JAXB classes, rate provider
```

---

## Controller Standards

- One controller per resource; admin controllers live under `controller/admin/`
- Controllers own HTTP concerns only (request binding, response writing, redirects)
- No business logic in controllers — delegate to a service
- Use `@Tag` + `@Operation` (springdoc) on every public endpoint
- Return `ResponseEntity` for REST endpoints; `String` (view name) or `RedirectView` for Thymeleaf routes
- File download endpoints: set `Content-Type` and `Content-Disposition` headers explicitly before writing to `HttpServletResponse.getOutputStream()`

---

## Service Standards

- Define an interface for every service; implementation lives in `impl/` with `Impl` suffix
- Services own business logic, validation, and transaction boundaries (`@Transactional`)
- Long-running or fire-and-forget operations use `@Async` with an explicitly named executor (`@Async("notificationExecutor")`)
- Never inject `HttpServletRequest` / `HttpServletResponse` into a service

---

## Entity Standards

- All entities use `BIGINT` PK with `@GeneratedValue(strategy = GenerationType.IDENTITY)`
- Enum columns mapped with `@Enumerated(EnumType.STRING)` in JPA; MySQL column type is `ENUM(...)` inline
- `created_at` / `updated_at` fields: `@Column(updatable = false)` / `@Column` respectively; auto-set via `@PrePersist` / `@PreUpdate` or Flyway default
- No bidirectional `@OneToMany` without `mappedBy`; prefer `@ManyToOne` on the owning side

---

## Repository Standards

- Extend `JpaRepository<Entity, Long>`
- Custom JPQL queries use `@Query`; native queries only when JPQL cannot express the logic
- Projection interfaces preferred over `List<Object[]>` for chart/aggregate queries where feasible
- Repository method names follow Spring Data naming conventions (`findBy`, `countBy`, `deleteBy`)

---

## Excel Standards (Apache POI)

- Use `XSSFWorkbook` for standard files; switch to `SXSSFWorkbook` (streaming) only when row count > 5 000
- Export: `BookingExcelExporter` owns all cell/style creation; service layer provides only the data list
- Import: `TourExcelImporter` reads rows into `String[][]` on the calling thread before any parallel work; each row processed independently via `CompletableFuture.supplyAsync(..., importExecutor)`
- Validate each row independently — do not stop on first error; collect all errors and return in summary
- Accepted file types for import: `.xlsx` only; max 5 MB; max 500 data rows

---

## SOAP Standards (Spring WS)

- JAXB classes written by hand (no codegen); live in `soap/` package
- Namespace and local part constants defined in `CurrencyConversionEndpoint` as `static final String`
- `CurrencyConversionClient` extends `WebServiceGatewaySupport`; URI injected via constructor or `@Value`
- SOAP endpoint does not require authentication; do not log request amounts at DEBUG in production

---

## Messaging Standards

**ActiveMQ (JMS)**
- Message DTOs implement `Serializable`; include `serialVersionUID`
- Producer uses `JmsTemplate.convertAndSend()`; consumer uses `@JmsListener(destination = "...")`
- One queue = one consumer class

**RabbitMQ (AMQP)**
- Message DTOs serialized via `Jackson2JsonMessageConverter`
- Fanout exchange bindings declared as `@Bean`s in `RabbitMQConfig`
- Each listener class handles exactly one queue

---

## Async / Thread Pool Standards

- Never use `new Thread()`; always submit work to a named `ThreadPoolTaskExecutor`
- `importExecutor`: for parallel row processing in Excel import (CallerRunsPolicy as backpressure)
- `notificationExecutor`: for fire-and-forget notification saves and WebSocket pushes
- `@Async` methods must return `void` or `Future<T>` / `CompletableFuture<T>`; never return a domain object directly from an async method and discard the future

---

## Scheduler Standards

- Cron expressions documented inline above the `@Scheduled` annotation
- Every job writes a `ScheduledJobLog` row (job name, status, records processed, duration ms)
- Jobs are `@Async` to avoid blocking the scheduler thread pool
- Disable via `scheduler.enabled=false` in `application.properties` when needed for tests

---

## Logging Standards

- Use `@Slf4j` (Lombok) or `LoggerFactory.getLogger(getClass())`; never `System.out.println`
- Log level guidance: DEBUG for low-level trace (SQL, queue payload), INFO for business events (booking status change, import summary), WARN for recoverable issues, ERROR for exceptions with stack traces
- Never log passwords, JWT tokens, or PII beyond user email (which is already in MDC)
- Scheduler jobs log: `[SCHEDULER] JobName: X records processed in Yms`
- Excel operations log: filename, row count, duration ms at INFO

---

## Flyway Migration Standards

- File naming: `V{n}__{description}.sql` (double underscore), description in snake_case
- All DDL: `ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci`
- PK: `BIGINT NOT NULL AUTO_INCREMENT`
- Boolean columns: `TINYINT(1) NOT NULL DEFAULT 0`
- Enum columns: inline MySQL `ENUM('A','B',...)`
- Timestamps: `DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)`
- Every migration is irreversible by default; no `DROP` in forward migrations without team approval

---

## Testing Standards

- Unit tests: JUnit 5 + Mockito; test class name = `{Subject}Test`
- Integration tests: `@SpringBootTest` + MockMvc; annotate with `@Transactional` + `@Rollback`
- No mocking the database in integration tests
- Security tests: use `@WithMockUser(roles = "ADMIN")` / `@WithMockUser(roles = "USER")`
- Coverage target: ≥ 60% line coverage on service layer
- Test properties: `src/test/resources/application-test.properties` overrides dev settings (stub OAuth2 credentials, H2 or test MySQL DB)

---

## Security Standards

- All admin routes protected by `hasRole("ADMIN")` in `SecurityConfig`
- CSRF token required on all POST/PUT/DELETE forms; `CookieCsrfTokenRepository` in use
- JWT stored in HttpOnly cookie only — never in `localStorage`
- Swagger UI (`/swagger-ui/**`, `/v3/api-docs/**`) permitted only in `dev` profile
- SOAP endpoint (`/soap/**`) permitted without auth (mock data only; no sensitive operations)
- WebSocket STOMP CONNECT validated by `WebSocketSecurityConfig`
