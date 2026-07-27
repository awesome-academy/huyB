package com.sunasterisk.bookingtours.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

public class MdcLoggingFilter extends OncePerRequestFilter {

    private static final String REQUEST_ID = "requestId";
    private static final String USER_EMAIL = "userEmail";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            MDC.put(REQUEST_ID, UUID.randomUUID().toString());

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()
                    && !"anonymousUser".equals(auth.getPrincipal())) {
                // Strip newlines to prevent log injection
                MDC.put(USER_EMAIL, auth.getName().replaceAll("[\r\n]", "_"));
            } else {
                MDC.put(USER_EMAIL, "anonymous");
            }

            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
