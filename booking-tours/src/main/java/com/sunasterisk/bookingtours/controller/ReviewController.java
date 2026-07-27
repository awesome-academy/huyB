package com.sunasterisk.bookingtours.controller;

import com.sunasterisk.bookingtours.dto.CommentRequest;
import com.sunasterisk.bookingtours.dto.ReviewRequest;
import com.sunasterisk.bookingtours.entity.Comment;
import com.sunasterisk.bookingtours.entity.Review;
import com.sunasterisk.bookingtours.entity.ReviewType;
import com.sunasterisk.bookingtours.service.CommentService;
import com.sunasterisk.bookingtours.service.LikeService;
import com.sunasterisk.bookingtours.service.ReviewService;
import com.sunasterisk.bookingtours.util.PaginationUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/reviews")
@RequiredArgsConstructor
public class ReviewController {
    private static final int PAGE_SIZE = 9;

    private final ReviewService reviewService;
    private final CommentService commentService;
    private final LikeService likeService;

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
     * 9.1 — Hiển thị danh sách comments + replies
     * 9.3 — Hiển thị trạng thái like của user hiện tại
     */
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Authentication authentication, Model model) {
        Review review = reviewService.findById(id);
        model.addAttribute("review", review);

        // 9.1: Lấy comment gốc và reply để hiển thị trong template
        List<Comment> rootComments = commentService.findRootComments(id);
        List<Comment> replies = commentService.findReplies(id);
        model.addAttribute("rootComments", rootComments);
        // Nhóm reply theo parentId để dễ render trong template
        Map<Long, List<Comment>> repliesByParentId = new HashMap<>();
        for (Comment reply : replies) {
            repliesByParentId
                    .computeIfAbsent(reply.getParent().getId(), k -> new ArrayList<>())
                    .add(reply);
        }
        model.addAttribute("repliesByParentId", repliesByParentId);

        // Form thêm comment mới
        model.addAttribute("commentRequest", new CommentRequest());

        // 9.3: Trạng thái like của user hiện tại (null nếu chưa đăng nhập)
        boolean liked = false;
        if (authentication != null && authentication.isAuthenticated()) {
            liked = likeService.isLikedByUser(id, authentication.getName());
        }
        model.addAttribute("liked", liked);

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

    // ================================================================
    // 9.2 — Comment & Reply
    // ================================================================

    /**
     * 9.2 — Thêm comment gốc vào review.
     * POST /reviews/{id}/comments
     */
    @PostMapping("/{id}/comments")
    public String addComment(@PathVariable Long id,
                             @Valid @ModelAttribute CommentRequest commentRequest,
                             BindingResult bindingResult,
                             Authentication authentication,
                             RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            var fieldError = bindingResult.getFieldError("content");
            redirectAttributes.addFlashAttribute("errorMessage",
                    fieldError != null ? fieldError.getDefaultMessage() : "Invalid comment.");
            return "redirect:/reviews/" + id;
        }
        try {
            commentService.addComment(id, authentication.getName(), commentRequest);
            redirectAttributes.addFlashAttribute("successMessage", "Comment added successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to add comment: " + e.getMessage());
        }
        return "redirect:/reviews/" + id;
    }

    /**
     * 9.2 — Reply một comment gốc (1 cấp).
     * POST /reviews/{id}/comments/{parentId}/reply
     */
    @PostMapping("/{id}/comments/{parentId}/reply")
    public String addReply(@PathVariable Long id,
                           @PathVariable Long parentId,
                           @Valid @ModelAttribute CommentRequest commentRequest,
                           BindingResult bindingResult,
                           Authentication authentication,
                           RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            var fieldError = bindingResult.getFieldError("content");
            redirectAttributes.addFlashAttribute("errorMessage",
                    fieldError != null ? fieldError.getDefaultMessage() : "Invalid reply.");
            return "redirect:/reviews/" + id;
        }
        try {
            commentService.addReply(id, parentId, authentication.getName(), commentRequest);
            redirectAttributes.addFlashAttribute("successMessage", "Reply added successfully!");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Replies to replies are not allowed.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to add reply: " + e.getMessage());
        }
        return "redirect:/reviews/" + id;
    }

    /**
     * 9.2 — Xóa mềm comment hoặc reply (chỉ chủ sở hữu).
     * POST /reviews/{id}/comments/{commentId}/delete
     */
    @PostMapping("/{id}/comments/{commentId}/delete")
    public String deleteComment(@PathVariable Long id,
                                @PathVariable Long commentId,
                                Authentication authentication,
                                RedirectAttributes redirectAttributes) {
        try {
            commentService.deleteComment(commentId, authentication.getName());
            redirectAttributes.addFlashAttribute("successMessage", "Comment deleted.");
        } catch (AccessDeniedException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "You are not allowed to delete this comment.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to delete comment.");
        }
        return "redirect:/reviews/" + id;
    }

    // ================================================================
    // 9.3 — Like / Unlike review (AJAX toggle)
    // ================================================================

    /**
     * 9.3 — Toggle like / unlike review qua AJAX.
     * POST /reviews/{id}/like
     * Trả về JSON: {"liked": true/false, "likesCount": N}
     */
    @PostMapping("/{id}/like")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> toggleLike(@PathVariable Long id,
                                                          Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        try {
            boolean liked = likeService.toggleLike(id, authentication.getName());
            // Lấy lại review để trả về likes_count cập nhật
            Review review = reviewService.findById(id);
            return ResponseEntity.ok(Map.of(
                    "liked", liked,
                    "likesCount", review.getLikesCount()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
