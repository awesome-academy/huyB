package com.sunasterisk.bookingtours.controller;

import com.sunasterisk.bookingtours.dto.ReviewRequest;
import com.sunasterisk.bookingtours.entity.Review;
import com.sunasterisk.bookingtours.entity.ReviewType;
import com.sunasterisk.bookingtours.service.ReviewService;
import com.sunasterisk.bookingtours.util.PaginationUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/reviews")
@RequiredArgsConstructor
public class ReviewController {
    private static final int PAGE_SIZE = 9;

    private final ReviewService reviewService;

    /**
     * 8.2 — Danh sách review (lọc PLACE/FOOD/NEWS, phân trang)
     */
    @GetMapping
    public String list(
            @RequestParam(value = "reviewType", required = false) ReviewType reviewType,
            @RequestParam(value = "page", defaultValue = "0") int page,
            Model model) {
        Pageable pageable = PageRequest.of(page, PAGE_SIZE);
        Page<Review> reviewPage = reviewService.findPublishedByType(reviewType, pageable);
        int totalPages = reviewPage.getTotalPages();

        model.addAttribute("reviewPage", reviewPage);
        model.addAttribute("reviewType", reviewType);
        model.addAttribute("reviewTypes", ReviewType.values());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("pageNumbers", PaginationUtils.getPageNumbers(page, totalPages));
        return "reviews/list";
    }

    /**
     * 8.2 — Chi tiết review
     */
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        Review review = reviewService.findById(id);
        model.addAttribute("review", review);
        return "reviews/detail";
    }

    /**
     * 8.3 — Form tạo review mới
     */
    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("reviewRequest", new ReviewRequest());
        model.addAttribute("reviewTypes", ReviewType.values());
        model.addAttribute("formMode", "CREATE");
        return "reviews/form";
    }

    /**
     * 8.3 — Lưu review mới
     */
    @PostMapping
    public String create(@Valid @ModelAttribute ReviewRequest reviewRequest,
                         BindingResult bindingResult,
                         Authentication authentication,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("reviewTypes", ReviewType.values());
            model.addAttribute("formMode", "CREATE");
            return "reviews/form";
        }
        try {
            Review review = reviewService.create(authentication.getName(), reviewRequest);
            redirectAttributes.addFlashAttribute("successMessage", "Review posted successfully!");
            return "redirect:/reviews/" + review.getId();
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return "reviews/form";
        } catch (Exception e) {
            return "reviews/form";
        }
    }

    /**
     * 8.3 — Form sửa review
     */
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Authentication authentication, Model model) {
        Review review = reviewService.findById(id);
        if (!review.getUser().getEmail().equals(authentication.getName())) {
            throw new AccessDeniedException("You are not allowed to edit this review.");
        }
        ReviewRequest reviewRequest = new ReviewRequest();
        reviewRequest.setTitle(review.getTitle());
        reviewRequest.setContent(review.getContent());
        reviewRequest.setReviewType(review.getType());

        model.addAttribute("review", review);
        model.addAttribute("reviewRequest", reviewRequest);
        model.addAttribute("reviewTypes", ReviewType.values());
        model.addAttribute("formMode", "EDIT");
        return "reviews/form";
    }

    /**
     * 8.3 — Cập nhật review
     */
    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute ReviewRequest reviewRequest,
                         BindingResult bindingResult,
                         Authentication authentication,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            Review review = reviewService.findById(id);
            model.addAttribute("review", review);
            model.addAttribute("reviewTypes", ReviewType.values());
            model.addAttribute("formMode", "EDIT");
            return "reviews/form";
        }
        try {
            reviewService.update(id, authentication.getName(), reviewRequest);
            redirectAttributes.addFlashAttribute("successMessage", "Review updated successfully!");
        } catch (AccessDeniedException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "You are not allowed to delete this review.");
            return "reviews/form";
        } catch (Exception e) {
            return "reviews/form";
        }
        return "redirect:/reviews/" + id;
    }

    /**
     * 8.3 — Xóa review
     */
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id,
                         Authentication authentication,
                         RedirectAttributes redirectAttributes) {
        try {
            reviewService.delete(id, authentication.getName());
            redirectAttributes.addFlashAttribute("successMessage", "Review deleted.");
        } catch (AccessDeniedException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "You are not allowed to delete this review.");
            return "reviews/" + id;
        } catch (Exception e) {
            return "reviews/" + id;
        }
        return "redirect:/reviews";
    }
}
