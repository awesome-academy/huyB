package com.sunasterisk.bookingtours.controller;

import com.sunasterisk.bookingtours.config.JwtUtils;
import com.sunasterisk.bookingtours.dto.LoginRequest;
import com.sunasterisk.bookingtours.dto.RegisterRequest;
import com.sunasterisk.bookingtours.exception.DuplicateEmailException;
import com.sunasterisk.bookingtours.service.UserService;
import com.sunasterisk.bookingtours.util.LoginAttemptService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Controller
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;
    private final LoginAttemptService loginAttemptService;

    // ----------------------------------------------------------------
    // Register
    // ----------------------------------------------------------------

    @GetMapping("/register")
    public String showRegisterForm(Model model) {
        model.addAttribute("registerRequest", new RegisterRequest());
        return "auth/register";
    }

    @PostMapping("/register")
    public String processRegister(
            @Valid @ModelAttribute("registerRequest") RegisterRequest request,
            BindingResult bindingResult,
            Model model) {

        // Bean Validation errors
        if (bindingResult.hasErrors()) {
            return "auth/register";
        }

        // Password confirm check
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            bindingResult.rejectValue("confirmPassword", "error.confirmPassword", "Passwords do not match");
            return "auth/register";
        }

        // Optimistic pre-read: catches the common (non-concurrent) duplicate-email
        // case early so we avoid hashing the password unnecessarily.
        if (userService.emailExists(request.getEmail())) {
            bindingResult.rejectValue("email", "error.email", "Email is already registered");
            return "auth/register";
        }

        try {
            userService.register(request);
            return "redirect:/auth/login?registered=true";
        } catch (DuplicateEmailException e) {
            // Handles the race-condition window: two concurrent registrations both
            // pass the pre-read check above, but only one can win the DB unique
            // constraint.  Surface the same friendly validation error.
            bindingResult.rejectValue("email", "error.email", "Email is already registered");
            return "auth/register";
        } catch (Exception e) {
            // Không đưa e.getMessage() ra UI — thông điệp exception nội bộ có thể
            // lộ chi tiết schema/persistence. Log để debug, hiển thị lỗi chung.
            log.error("Registration failed for email={}", request.getEmail(), e);
            model.addAttribute("errorMessage", "Registration failed. Please try again later.");
            return "auth/register";
        }
    }

    // ----------------------------------------------------------------
    // Login
    // ----------------------------------------------------------------

    @GetMapping("/login")
    public String showLoginForm(Model model) {
        model.addAttribute("loginRequest", new LoginRequest());
        return "auth/login";
    }

    /**
     * Xác thực thủ công thay vì dùng Spring Security formLogin filter.
     * Lý do: cần tạo JWT và đặt vào HttpOnly cookie trước khi redirect.
     */
    @PostMapping("/login")
    public String processLogin(
            @Valid @ModelAttribute("loginRequest") LoginRequest loginRequest,
            BindingResult bindingResult,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model) {

        // Bean Validation errors (blank email, invalid email format, short password)
        if (bindingResult.hasErrors()) {
            return "auth/login";
        }

        // Chống brute-force: chặn khi quá số lần thất bại cho phép trong cửa sổ 15'
        String attemptKey = LoginAttemptService.key(request.getRemoteAddr(), loginRequest.getEmail());
        if (loginAttemptService.isBlocked(attemptKey)) {
            model.addAttribute("loginBlocked", true);
            model.addAttribute("errorMessage",
                    "Too many failed login attempts. Please try again in 15 minutes.");
            return "auth/login";
        }

        try {
            // 1. Xác thực credentials (email được dùng làm username)
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword()));

            loginAttemptService.reset(attemptKey);

            // 2. Tạo JWT và đặt vào HttpOnly cookie (SameSite=Strict)
            jwtUtils.addJwtCookie(response, jwtUtils.generateToken(authentication));

            // 3. Redirect theo role
            boolean isAdmin = authentication.getAuthorities().stream()
                    .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
            return "redirect:" + (isAdmin ? "/admin" : "/");
        } catch (AuthenticationException e) {
            loginAttemptService.recordFailure(attemptKey);
            model.addAttribute("loginError", true);
            return "auth/login";
        }
    }
}
