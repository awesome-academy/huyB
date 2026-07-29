---
phase: 01
title: logback-spring.xml — 4 appenders + spring profile gating
status: pending
---

## File: src/main/resources/logback-spring.xml

### Appenders

1. **CONSOLE** — stdout, dev profile only, PatternLayoutEncoder
2. **APP_FILE** — `logs/app.log`, INFO+, SizeAndTimeBasedRollingPolicy (daily, 30d, 100MB)
3. **ERROR_FILE** — `logs/error.log`, ERROR only (LevelFilter), daily rolling, 30d
4. **AUDIT_FILE** — `logs/audit.log`, INFO+, kết nối với logger name "AUDIT"

### Profile gating

```xml
<springProfile name="dev">
  root level="INFO", appLogLevel="DEBUG", CONSOLE appender included
</springProfile>
<springProfile name="prod">
  root level="WARN", appLogLevel="INFO", no CONSOLE
</springProfile>
```

### Log pattern

```
%d{yyyy-MM-dd HH:mm:ss.SSS} [%X{requestId}] [%X{userEmail}] %-5level %logger{36} - %msg%n
```

## Todo
- [ ] Khai báo property `LOG_PATH` = logs
- [ ] Tạo CONSOLE appender
- [ ] Tạo APP_FILE appender với SizeAndTimeBasedRollingPolicy
- [ ] Tạo ERROR_FILE appender với LevelFilter(ERROR)
- [ ] Tạo AUDIT_FILE appender cho logger "AUDIT"
- [ ] springProfile dev: root INFO, app.package DEBUG, CONSOLE + APP_FILE + ERROR_FILE
- [ ] springProfile prod: root WARN, app.package INFO, APP_FILE + ERROR_FILE + AUDIT_FILE only
