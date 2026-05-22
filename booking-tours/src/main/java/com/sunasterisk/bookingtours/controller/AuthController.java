package com.sunasterisk.bookingtours.controller;

import com.sunasterisk.bookingtours.dto.RegisterRequest;
import com.sunasterisk.bookingtours.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

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
            RedirectAttributes redirectAttributes,
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
            redirectAttributes.addFlashAttribute("successMessage",
                    "Registration successful! Please log in.");
            return "redirect:/auth/login";
        } catch (Exception e) {
            model.addAttribute("errorMessage", e.getMessage());
            return "auth/register";
        }
    }

    // ----------------------------------------------------------------
    // Login (form rendered by Spring Security, page provided here)
    // ----------------------------------------------------------------

    @GetMapping("/login")
    public String showLoginForm() {
        return "auth/login";
    }
}
