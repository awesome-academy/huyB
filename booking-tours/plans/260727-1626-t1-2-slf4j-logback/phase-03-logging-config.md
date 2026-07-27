---
phase: 03
title: LoggingConfig.java — FilterRegistrationBean
status: pending
---

## File: src/main/java/com/sunasterisk/bookingtours/config/LoggingConfig.java

```
@Configuration
public class LoggingConfig {
    @Bean
    public MdcLoggingFilter mdcLoggingFilter() { return new MdcLoggingFilter(); }

    @Bean
    public FilterRegistrationBean<MdcLoggingFilter> mdcLoggingFilterRegistration(MdcLoggingFilter filter) {
        FilterRegistrationBean<MdcLoggingFilter> reg = new FilterRegistrationBean<>(filter);
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE);
        reg.addUrlPatterns("/*");
        return reg;
    }
}
```

## Tại sao không @Component?

Nếu `MdcLoggingFilter` là `@Component`, Spring Boot tự đăng ký với order mặc định (Integer.MAX_VALUE) — chạy SAU Spring Security. Kết quả: requestId không có trong security log lines. Dùng `FilterRegistrationBean` để kiểm soát order chính xác.

## Todo
- [ ] Tạo LoggingConfig.java trong config/
- [ ] Bean MdcLoggingFilter (factory method)
- [ ] Bean FilterRegistrationBean với order HIGHEST_PRECEDENCE
- [ ] addUrlPatterns("/*")
