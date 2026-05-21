package com.sunasterisk.bookingtours.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@ControllerAdvice
public class GlobalExceptionHandler {

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
        model.addAttribute("message", "An error occurred. Please try again later.");
        model.addAttribute("error", ex.getMessage());
        return "error/500";
    }
}
