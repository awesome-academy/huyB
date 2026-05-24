package com.sunasterisk.bookingtours.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Arrays;
import java.util.UUID;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final Environment environment;

    public GlobalExceptionHandler(Environment environment) {
        this.environment = environment;
    }

    /**
     * Trả về true khi ứng dụng đang chạy dưới profile "dev".
     */
    private boolean isDevProfile() {
        return Arrays.asList(environment.getActiveProfiles()).contains("dev");
    }

    /**
     * 403 — Không có quyền truy cập.
     * Spring Security ném AccessDeniedException trước khi vào đây nếu dùng
     * exceptionHandling().accessDeniedPage("/error/403"), nhưng handler này
     * xử lý các trường hợp throw thủ công trong code.
     */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String handleAccessDenied(AccessDeniedException ex, Model model) {
        model.addAttribute("message", "You do not have permission to access this page.");
        return "error/403";
    }

    /**
     * 404 — Không tìm thấy trang / resource.
     */
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNotFound(Exception ex, Model model) {
        model.addAttribute("message", "The page you are looking for does not exist.");
        return "error/404";
    }

    /**
     * ResourceNotFoundException — dùng khi entity không tìm thấy trong DB.
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleResourceNotFound(ResourceNotFoundException ex, Model model) {
        model.addAttribute("message", ex.getMessage());
        return "error/404";
    }

    /**
     * Fallback — mọi exception chưa được xử lý.
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleGeneral(Exception ex, HttpServletRequest request, Model model) {
        String correlationId = UUID.randomUUID().toString();

        log.error("[correlationId={}] Unhandled exception on {} {}: {}",
                correlationId,
                request.getMethod(),
                request.getRequestURI(),
                ex.getMessage(),
                ex);

        model.addAttribute("message", "An unexpected error occurred. Please try again later.");
        model.addAttribute("correlationId", correlationId);

        // Expose detail only in the dev profile — never in production.
        if (isDevProfile()) {
            model.addAttribute("error", ex.getMessage());
        }

        return "error/500";
    }
}
