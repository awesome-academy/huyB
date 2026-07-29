---
plan: T1.2 — SLF4J + Logback Configuration
spec_draft: plans/260727-1626-t1-2-slf4j-logback/spec/t1-2-slf4j-logback/
status: in_progress
created: 2026-07-27
depends_on: T1.1
---

# T1.2 — SLF4J + Logback Configuration

## Tổng quan

Cấu hình Logback với MDC filter để ghi log có cấu trúc (requestId + userEmail) ra 3 file log phân lớp.

## Phases

| Phase | Mô tả | Trạng thái |
|---|---|---|
| [01 — logback-spring.xml](phase-01-logback-xml.md) | 4 appenders, spring profile gating | ⏳ |
| [02 — MdcLoggingFilter](phase-02-mdc-filter.md) | OncePerRequestFilter, MDC set/clear | ⏳ |
| [03 — LoggingConfig](phase-03-logging-config.md) | FilterRegistrationBean, HIGHEST_PRECEDENCE | ⏳ |

## Success Criteria

- `logs/app.log` tồn tại sau startup
- Mỗi log line có `requestId` và `userEmail`
- ERROR → `logs/error.log`
- DEBUG ở dev, INFO ở prod
