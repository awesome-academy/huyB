package com.sunasterisk.bookingtours.config;

import com.sunasterisk.bookingtours.filter.MdcLoggingFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LoggingConfig {

    // FilterChainProxy của Spring Security chạy ở thứ tự -100 (SecurityProperties.DEFAULT_FILTER_ORDER).
    // Chạy ở -50 đảm bảo JWT authentication đã ghi vào SecurityContextHolder trước,
    // để MDC lấy được userEmail thực của các request đã xác thực.
    private static final int MDC_FILTER_ORDER = -50;

    @Bean
    public MdcLoggingFilter mdcLoggingFilter() {
        return new MdcLoggingFilter();
    }

    @Bean
    public FilterRegistrationBean<MdcLoggingFilter> mdcLoggingFilterRegistration(MdcLoggingFilter filter) {
        FilterRegistrationBean<MdcLoggingFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(MDC_FILTER_ORDER);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
