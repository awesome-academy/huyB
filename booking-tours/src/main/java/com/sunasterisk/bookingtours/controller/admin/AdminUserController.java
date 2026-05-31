package com.sunasterisk.bookingtours.controller.admin;

import com.sunasterisk.bookingtours.entity.User;
import com.sunasterisk.bookingtours.service.UserService;
import com.sunasterisk.bookingtours.util.PaginationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private static final int PAGE_SIZE = 20;

    private final UserService userService;

    /**
     * GET /admin/users — Danh sách user có phân trang và tìm kiếm.
     */
    @GetMapping
    public String listUsers(
            @RequestParam(value = "keyword", defaultValue = "") String keyword,
            @RequestParam(value = "page", defaultValue = "0") int page,
            Model model) {

        Pageable pageable = PageRequest.of(page, PAGE_SIZE, Sort.by("id").ascending());
        Page<User> userPage = userService.searchUsers(keyword.isBlank() ? null : keyword, pageable);

        int totalPages = userPage.getTotalPages();

        model.addAttribute("userPage", userPage);
        model.addAttribute("keyword", keyword);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("pageNumbers", PaginationUtils.getPageNumbers(page, totalPages));

        return "admin/users/list";
    }

    /**
     * POST /admin/users/{id}/toggle-lock — Khoá / mở khoá tài khoản.
     * Admin không thể tự khoá chính mình.
     * <p>
     * Dùng {@code RedirectAttributes.addAttribute} thay vì nối chuỗi thủ công
     * để Spring tự động URL-encode các tham số (xử lý đúng ký tự đặc biệt như
     * {@code &}, {@code =}, {@code #}, {@code %}, khoảng trắng, v.v.).
     * </p>
     */
    @PostMapping("/{id}/toggle-lock")
    public String toggleLock(
            @PathVariable Long id,
            @RequestParam(value = "keyword", defaultValue = "") String keyword,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @AuthenticationPrincipal UserDetails currentUser,
            RedirectAttributes redirectAttributes) {

        // Ngăn admin tự khoá chính mình
        User me = userService.getByEmail(currentUser.getUsername());
        if (me.getId().equals(id)) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "You cannot lock your own account.");
            return buildRedirect(keyword, page, redirectAttributes);
        }

        User updated = userService.toggleLock(id);
        String msg = updated.getIsActive()
                ? "The account has been unlocked successfully."
                : "The account has been locked successfully.";
        redirectAttributes.addFlashAttribute("successMessage", msg);

        return buildRedirect(keyword, page, redirectAttributes);
    }

    /**
     * Xây dựng redirect URL an toàn bằng {@code RedirectAttributes.addAttribute}.
     * Spring MVC sẽ tự động URL-encode các giá trị tham số, tránh URL bị lỗi
     * khi keyword chứa ký tự đặc biệt (&, =, #, %, khoảng trắng...).
     */
    private String buildRedirect(String keyword, int page, RedirectAttributes redirectAttributes) {
        redirectAttributes.addAttribute("page", page);
        if (!keyword.isBlank()) {
            redirectAttributes.addAttribute("keyword", keyword);
        }
        return "redirect:/admin/users";
    }
}
