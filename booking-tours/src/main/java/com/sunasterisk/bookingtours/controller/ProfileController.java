package com.sunasterisk.bookingtours.controller;

import com.sunasterisk.bookingtours.dto.NotificationDto;
import com.sunasterisk.bookingtours.dto.ProfileUpdateRequest;
import com.sunasterisk.bookingtours.entity.User;
import com.sunasterisk.bookingtours.service.NotificationService;
import com.sunasterisk.bookingtours.service.UserService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Tag(name = "Profile", description = "Quản lý thông tin cá nhân")
@Controller
@RequestMapping("/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final UserService userService;
    private final NotificationService notificationService;

    /**
     * Hiển thị trang hồ sơ cá nhân của user đang đăng nhập.
     */
    @Operation(summary = "Trang hồ sơ cá nhân", description = "Hiển thị thông tin và form chỉnh sửa profile")
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
    @Operation(summary = "Cập nhật hồ sơ", description = "Cập nhật full_name, phone, avatar_url")
    @PostMapping
    public String updateProfile(
            Authentication authentication,
            @Valid @ModelAttribute("profileForm") ProfileUpdateRequest form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        User user = userService.getByEmail(authentication.getName());

        // Giá trị avatar cũ (lưu từ trước khi có validation https) có thể không khớp
        // pattern mới. Form luôn resubmit giá trị đã điền sẵn, nên nếu user chỉ sửa
        // các field khác mà không đụng avatar, đừng để lỗi avatar-cũ chặn toàn bộ update:
        // coi như không có lỗi khi TẤT CẢ lỗi đều nằm ở avatarUrl và avatar không đổi.
        boolean onlyUnchangedAvatarError =
                java.util.Objects.equals(form.getAvatarUrl(), user.getAvatarUrl())
                        && bindingResult.getErrorCount() == bindingResult.getFieldErrorCount("avatarUrl");

        if (bindingResult.hasErrors() && !onlyUnchangedAvatarError) {
            // Truyền lại user để hiển thị thông tin hiện tại (email, avatar preview, ...)
            model.addAttribute("user", user);
            return "profile/profile";
        }

        try {
            userService.updateProfile(authentication.getName(), form);
            redirectAttributes.addFlashAttribute("successMessage", "Profile updated successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to update profile. Please try again.");
        }

        return "redirect:/profile";
    }

    @Operation(summary = "Trang danh sách thông báo", description = "Hiển thị toàn bộ thông báo của user có phân trang")
    @GetMapping("/notifications")
    public String showNotifications(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            Model model) {
        User user = userService.getByEmail(authentication.getName());
        Page<NotificationDto> notifications =
                notificationService.getNotifications(user.getId(), PageRequest.of(page, 15));
        model.addAttribute("notifications", notifications);
        model.addAttribute("currentPage", page);
        return "profile/notifications";
    }

    @Operation(summary = "Đánh dấu tất cả thông báo đã đọc", description = "Redirect về trang thông báo sau khi đánh dấu")
    @PostMapping("/notifications/mark-read")
    public String markAllNotificationsRead(Authentication authentication) {
        User user = userService.getByEmail(authentication.getName());
        notificationService.markAllRead(user.getId());
        return "redirect:/profile/notifications";
    }
}
