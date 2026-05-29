package com.sunasterisk.bookingtours.controller;

import com.sunasterisk.bookingtours.dto.ProfileUpdateRequest;
import com.sunasterisk.bookingtours.entity.User;
import com.sunasterisk.bookingtours.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final UserService userService;

    /**
     * Hiển thị trang hồ sơ cá nhân của user đang đăng nhập.
     */
    @GetMapping
    public String showProfile(Authentication authentication, Model model) {
        User user = userService.getByEmail(authentication.getName());

        // Pre-populate form với dữ liệu hiện tại
        ProfileUpdateRequest form = new ProfileUpdateRequest();
        form.setFullName(user.getFullName());
        form.setPhone(user.getPhone());
        form.setAvatarUrl(user.getAvatarUrl());

        model.addAttribute("user", user);
        model.addAttribute("profileForm", form);
        return "profile/profile";
    }

    /**
     * Xử lý cập nhật hồ sơ: full_name, phone, avatar_url.
     */
    @PostMapping
    public String updateProfile(
            Authentication authentication,
            @Valid @ModelAttribute("profileForm") ProfileUpdateRequest form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            // Truyền lại user để hiển thị thông tin hiện tại (email, avatar preview, ...)
            User user = userService.getByEmail(authentication.getName());
            model.addAttribute("user", user);
            return "profile/profile";
        }

        try {
            userService.updateProfile(authentication.getName(), form);
            redirectAttributes.addFlashAttribute("successMessage", "Profile updated successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to update profile: " + e.getMessage());
        }

        return "redirect:/profile";
    }
}
