---
feature_id: F002
slug: t1-2-slf4j-logback
lang: vi
status: draft
spec_draft: true
---

# F002 — SLF4J + Logback Configuration

## Mục tiêu

Cấu hình hệ thống logging có cấu trúc với MDC (requestId, userEmail) để mỗi log line có thể truy vết theo request và user. Ba file log phân lớp theo mức độ quan trọng.

## Phạm vi

| File | Hành động |
|---|---|
| `src/main/resources/logback-spring.xml` | **Tạo mới** |
| `src/main/java/.../filter/MdcLoggingFilter.java` | **Tạo mới** |
| `src/main/java/.../config/LoggingConfig.java` | **Tạo mới** |

## Log Pattern

```
%d{yyyy-MM-dd HH:mm:ss.SSS} [%X{requestId}] [%X{userEmail}] %-5level %logger{36} - %msg%n
```

## Appenders

| Appender | File | Level | Rolling |
|---|---|---|---|
| CONSOLE | stdout | DEBUG (dev only) | — |
| APP_FILE | logs/app.log | INFO+ | Daily, 30 days, 100MB/file |
| ERROR_FILE | logs/error.log | ERROR only | Daily, 30 days |
| AUDIT_FILE | logs/audit.log | INFO+ (logger "AUDIT") | Daily, 30 days |

## Log Levels theo Profile

| Profile | Root level | App package |
|---|---|---|
| dev | INFO | DEBUG |
| prod | WARN | INFO |

## MdcLoggingFilter — Contract

- Extends `OncePerRequestFilter`
- `doFilterInternal()`:
  1. Set `MDC.put("requestId", UUID.randomUUID().toString())`
  2. Set `MDC.put("userEmail", <from SecurityContext> hoặc "anonymous")`
  3. `filterChain.doFilter()`
  4. `finally: MDC.clear()`
- **Không phải `@Component`** — đăng ký qua `LoggingConfig.FilterRegistrationBean`

## LoggingConfig — Contract

- `@Configuration`
- `@Bean FilterRegistrationBean<MdcLoggingFilter>` với `order = Ordered.HIGHEST_PRECEDENCE`
- Đảm bảo MDC được set trước tất cả filter khác (kể cả Spring Security chain)

## Tiêu chí chấp nhận

1. `logs/app.log` tồn tại sau khi khởi động
2. Mỗi log line chứa `requestId` và `userEmail`
3. ERROR logs xuất hiện trong `logs/error.log`
4. Log level DEBUG ở dev profile, INFO ở prod profile

## Nằm ngoài phạm vi

- Gọi `auditLogger.info(...)` trong các handler (sẽ làm ở task tích hợp sau)
- Thay đổi controller / service
- Cấu hình async logging (sẽ làm ở T3.7)
