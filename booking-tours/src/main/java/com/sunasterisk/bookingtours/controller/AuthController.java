package com.sunasterisk.bookingtours.controller;

import com.sunasterisk.bookingtours.config.JwtUtils;
import com.sunasterisk.bookingtours.dto.RegisterRequest;
import com.sunasterisk.bookingtours.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;

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

        // Email unique check
        if (userService.emailExists(request.getEmail())) {
            bindingResult.rejectValue("email", "error.email", "Email is already registered");
            return "auth/register";
        }

        try {
            userService.register(request);
            return "redirect:/auth/login?registered=true";
        } catch (Exception e) {
            model.addAttribute("errorMessage", e.getMessage());
            return "auth/register";
        }
    }

    // ----------------------------------------------------------------
    // Login
    // ----------------------------------------------------------------

    @GetMapping("/login")
    public String showLoginForm() {
        return "auth/login";
    }

    /**
     * Xác thực thủ công thay vì dùng Spring Security formLogin filter.
     * Lý do: cần tạo JWT và đặt vào HttpOnly cookie trước khi redirect.
     */
    @PostMapping("/login")
    public String processLogin(
            @RequestParam String username,
            @RequestParam String password,
            HttpServletResponse response,
            Model model) {

        try {
            // 1. Xác thực credentials
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password));

            // 2. Tạo JWT và đặt vào HttpOnly cookie (SameSite=Strict)
            jwtUtils.addJwtCookie(response, jwtUtils.generateToken(authentication));

            // 3. Redirect theo role
            boolean isAdmin = authentication.getAuthorities().stream()
                    .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
            return "redirect:" + (isAdmin ? "/admin" : "/");
        } catch (AuthenticationException e) {
            model.addAttribute("loginError", true);
            return "auth/login";
        }
    }
}
