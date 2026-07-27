package com.sunasterisk.bookingtours.config;

import com.sunasterisk.bookingtours.filter.MdcLoggingFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LoggingConfig {

    // Spring Security's FilterChainProxy sits at -100 (SecurityProperties.DEFAULT_FILTER_ORDER).
    // Running at -50 ensures JWT authentication has already populated SecurityContextHolder,
    // so MDC picks up the real userEmail for authenticated requests.
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
