---
phase: 02
title: MdcLoggingFilter.java — OncePerRequestFilter
status: pending
---

## File: src/main/java/com/sunasterisk/bookingtours/filter/MdcLoggingFilter.java

```
package: com.sunasterisk.bookingtours.filter
class: MdcLoggingFilter extends OncePerRequestFilter
NOT @Component — đăng ký qua LoggingConfig
```

## Logic

```
doFilterInternal(request, response, chain):
  try:
    MDC.put("requestId", UUID.randomUUID().toString())
    auth = SecurityContextHolder.getContext().getAuthentication()
    if auth != null && auth.isAuthenticated() && principal != "anonymousUser":
      MDC.put("userEmail", auth.getName())
    else:
      MDC.put("userEmail", "anonymous")
    chain.doFilter(request, response)
  finally:
    MDC.clear()
```

## Todo
- [ ] Tạo package `filter/`
- [ ] Implement MdcLoggingFilter (không @Component)
- [ ] MDC.put requestId (UUID)
- [ ] MDC.put userEmail (SecurityContext hoặc "anonymous")
- [ ] MDC.clear() trong finally block
