package com.sunasterisk.bookingtours.controller.admin;

import com.sunasterisk.bookingtours.entity.ReviewStatus;
import com.sunasterisk.bookingtours.entity.ReviewType;
import com.sunasterisk.bookingtours.service.ReviewService;
import com.sunasterisk.bookingtours.util.PaginationUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Controller quản lý review phía Admin (task 8.4).
 * Tất cả endpoint yêu cầu role ADMIN (SecurityConfig).
 */
@Tag(name = "Admin - Reviews", description = "Kiểm duyệt và quản lý đánh giá")
@Slf4j
@Controller
@RequestMapping("/admin/reviews")
@RequiredArgsConstructor
public class AdminReviewController {

    private static final int PAGE_SIZE = 15;

    private final ReviewService reviewService;

    /**
     * GET /admin/reviews — Danh sách tất cả review, filter theo type & status
     */
    @GetMapping
    public String list(
            @RequestParam(value = "reviewType", required = false) ReviewType reviewType,
            @RequestParam(value = "status", required = false) ReviewStatus status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            Model model) {

        Pageable pageable = PageRequest.of(page, PAGE_SIZE);
        var reviewPage = reviewService.findAllByType(reviewType, pageable);
        int totalPages = reviewPage.getTotalPages();

        model.addAttribute("reviewPage", reviewPage);
        model.addAttribute("reviewType", reviewType);
        model.addAttribute("status", status);
        model.addAttribute("reviewTypes", ReviewType.values());
        model.addAttribute("statuses", ReviewStatus.values());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("pageNumbers", PaginationUtils.getPageNumbers(page, totalPages));
        model.addAttribute("totalPublished", reviewService.countByStatus(ReviewStatus.PUBLISHED));
        model.addAttribute("totalHidden", reviewService.countByStatus(ReviewStatus.HIDDEN));

        return "admin/reviews/list";
    }

    /**
     * POST /admin/reviews/{id}/hide — Ẩn review vi phạm
     */
    @PostMapping("/{id}/hide")
    public String hide(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            reviewService.hide(id);
            redirectAttributes.addFlashAttribute("successMessage", "Review #" + id + " has been hidden.");
        } catch (Exception e) {
            log.error("Error hiding review {}: {}", id, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Cannot hide review: " + e.getMessage());
        }
        return "redirect:/admin/reviews";
    }

    /**
     * POST /admin/reviews/{id}/restore — Khôi phục review đã ẩn
     */
    @PostMapping("/{id}/restore")
    public String restore(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            reviewService.restore(id);
            redirectAttributes.addFlashAttribute("successMessage", "Review #" + id + " has been restored.");
        } catch (Exception e) {
            log.error("Error restoring review {}: {}", id, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Cannot restore review: " + e.getMessage());
        }
        return "redirect:/admin/reviews";
    }

    /**
     * POST /admin/reviews/{id}/delete — Xóa hẳn review vi phạm
     */
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            reviewService.adminDelete(id);
            redirectAttributes.addFlashAttribute("successMessage", "Review #" + id + " has been permanently deleted.");
        } catch (Exception e) {
            log.error("Error deleting review {}: {}", id, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Cannot delete review: " + e.getMessage());
        }
        return "redirect:/admin/reviews";
    }
}
