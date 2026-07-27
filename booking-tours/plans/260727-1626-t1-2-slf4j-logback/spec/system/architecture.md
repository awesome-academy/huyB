---
doc_type: system-forward-draft
promotes_to: docs/system/architecture.md
status: draft
---

# Cross-Cutting Concerns — Logging Layer

## MDC Logging Filter

- **Filter:** `MdcLoggingFilter` tại order `Ordered.HIGHEST_PRECEDENCE`
- **MDC keys:** `requestId` (UUID), `userEmail` (từ SecurityContext)
- **Scope:** toàn bộ HTTP request lifecycle (trước Spring Security filter chain)
- **Registration:** `LoggingConfig.FilterRegistrationBean` (không dùng `@Component`)

## Log Files

| File | Mục đích | Retention |
|---|---|---|
| `logs/app.log` | Tất cả log INFO+ | 30 ngày / 100MB |
| `logs/error.log` | Chỉ ERROR | 30 ngày |
| `logs/audit.log` | Security events (logger "AUDIT") | 30 ngày |

## Log Pattern

```
%d{yyyy-MM-dd HH:mm:ss.SSS} [%X{requestId}] [%X{userEmail}] %-5level %logger{36} - %msg%n
```

## Profile Gating (logback-spring.xml)

- `<springProfile name="dev">` — console appender + DEBUG level cho app package
- `<springProfile name="prod">` — no console, WARN root, INFO app package
