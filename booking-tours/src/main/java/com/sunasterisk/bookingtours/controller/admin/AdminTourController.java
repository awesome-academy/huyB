package com.sunasterisk.bookingtours.controller.admin;

import com.sunasterisk.bookingtours.dto.TourRequest;
import com.sunasterisk.bookingtours.entity.Tour;
import com.sunasterisk.bookingtours.entity.TourStatus;
import com.sunasterisk.bookingtours.service.CategoryService;
import com.sunasterisk.bookingtours.service.TourService;
import com.sunasterisk.bookingtours.util.FileStorageService;
import com.sunasterisk.bookingtours.util.PaginationUtils;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Controller quản lý tour phía Admin.
 * Xử lý CRUD tour bao gồm upload thumbnail và chọn category / status.
 *
 * <p>Tất cả endpoint đều yêu cầu role ADMIN (kiểm soát bởi SecurityConfig).
 */
@Tag(name = "Admin - Tours", description = "Quản lý tour du lịch")
@Slf4j
@Controller
@RequestMapping("/admin/tours")
@RequiredArgsConstructor
public class AdminTourController {

    private static final int PAGE_SIZE = 15;

    private final TourService tourService;
    private final CategoryService categoryService;
    private final FileStorageService fileStorageService;

    // ----------------------------------------------------------------
    // Helper: đưa dữ liệu dùng chung cho form (categories, statuses) vào model
    // ----------------------------------------------------------------

    /**
     * Thêm danh sách category và các giá trị TourStatus vào model
     * để render dropdown trong form tạo / chỉnh sửa tour.
     */
    private void addFormData(Model model) {
        model.addAttribute("categories", categoryService.getAll());
        model.addAttribute("tourStatuses", TourStatus.values());
    }

    // ----------------------------------------------------------------
    // LIST
    // ----------------------------------------------------------------

    /**
     * GET /admin/tours — Danh sách tour có phân trang và tìm kiếm theo tên / điểm đến.
     */
    @GetMapping
    public String listTours(
            @RequestParam(value = "keyword", defaultValue = "") String keyword,
            @RequestParam(value = "page", defaultValue = "0") int page,
            Model model) {

        Pageable pageable = PageRequest.of(page, PAGE_SIZE);
        Page<Tour> tourPage = tourService.search(keyword.isBlank() ? null : keyword, pageable);
        int totalPages = tourPage.getTotalPages();

        model.addAttribute("tourPage", tourPage);
        model.addAttribute("keyword", keyword);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("pageNumbers", PaginationUtils.getPageNumbers(page, totalPages));

        return "admin/tours/list";
    }

    // ----------------------------------------------------------------
    // CREATE
    // ----------------------------------------------------------------

    /**
     * GET /admin/tours/new — Hiển thị form tạo tour mới.
     */
    @GetMapping("/new")
    public String showCreateForm(Model model) {
        model.addAttribute("tourRequest", new TourRequest());
        model.addAttribute("editMode", false);
        addFormData(model);
        return "admin/tours/form";
    }

    /**
     * POST /admin/tours — Lưu tour mới.
     *
     * <p>Nếu admin upload ảnh thumbnail, file được lưu vào {@code /uploads/} và URL
     * được ghi vào {@code tourRequest.thumbnailUrl} trước khi gọi service.
     * Nếu không upload, trường này bị bỏ qua (null).
     */
    @PostMapping
    public String createTour(
            @Valid @ModelAttribute("tourRequest") TourRequest request,
            BindingResult bindingResult,
            @RequestParam(value = "thumbnailFile", required = false) MultipartFile thumbnailFile,
            Model model,
            RedirectAttributes redirectAttributes) {

        // Xử lý upload thumbnail nếu có
        if (thumbnailFile != null && !thumbnailFile.isEmpty()) {
            try {
                String uploadedUrl = fileStorageService.store(thumbnailFile);
                request.setThumbnailUrl(uploadedUrl);
            } catch (Exception e) {
                log.warn("Failed to upload thumbnail: {}", e.getMessage());
                bindingResult.rejectValue("thumbnailUrl", "upload.failed",
                        "Failed to upload image: " + e.getMessage());
            }
        }

        if (bindingResult.hasErrors()) {
            // Cleanup orphan upload (if any)
            fileStorageService.delete(request.getThumbnailUrl());
            request.setThumbnailUrl(null);

            // editMode = false vì đây là form tạo mới
            model.addAttribute("editMode", false);
            addFormData(model);
            return "admin/tours/form";
        }

        try {
            tourService.create(request);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Tour '" + request.getTitle() + "' created successfully.");
        } catch (IllegalArgumentException e) {
            // Cleanup orphan upload (if any)
            fileStorageService.delete(request.getThumbnailUrl());
            request.setThumbnailUrl(null);

            model.addAttribute("editMode", false);
            model.addAttribute("errorMessage", e.getMessage());
            addFormData(model);
            return "admin/tours/form";
        }

        return "redirect:/admin/tours";
    }

    // ----------------------------------------------------------------
    // EDIT
    // ----------------------------------------------------------------

    /**
     * GET /admin/tours/{id}/edit — Hiển thị form chỉnh sửa tour.
     * Map dữ liệu tour hiện tại vào {@link TourRequest} để pre-fill form.
     */
    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Long id, Model model) {
        Tour tour = tourService.getById(id);

        TourRequest tourRequest = new TourRequest();
        tourRequest.setTitle(tour.getTitle());
        tourRequest.setDescription(tour.getDescription());
        tourRequest.setPrice(tour.getPrice());
        tourRequest.setDurationDays(tour.getDurationDays());
        tourRequest.setMaxParticipants(tour.getMaxParticipants());
        tourRequest.setDepartureLocation(tour.getDepartureLocation());
        tourRequest.setDestination(tour.getDestination());
        tourRequest.setDepartureDate(tour.getDepartureDate());
        tourRequest.setThumbnailUrl(tour.getThumbnailUrl());
        // FK có SET NULL → category có thể null nếu category cha đã bị xóa
        tourRequest.setCategoryId(tour.getCategory() != null ? tour.getCategory().getId() : null);
        tourRequest.setStatus(tour.getStatus());

        model.addAttribute("tourRequest", tourRequest);
        model.addAttribute("tourId", id);
        model.addAttribute("editMode", true);
        addFormData(model);
        return "admin/tours/form";
    }

    /**
     * POST /admin/tours/{id} — Cập nhật tour.
     *
     * <p>Nếu admin upload ảnh mới, ảnh cũ sẽ bị xóa khỏi filesystem (best-effort)
     * và URL được cập nhật. Nếu không upload, giữ nguyên thumbnail cũ qua hidden field.
     */
    @PostMapping("/{id}")
    public String updateTour(
            @PathVariable Long id,
            @Valid @ModelAttribute("tourRequest") TourRequest request,
            BindingResult bindingResult,
            @RequestParam(value = "thumbnailFile", required = false) MultipartFile thumbnailFile,
            @RequestParam(value = "currentThumbnailUrl", required = false) String currentThumbnailUrl,
            Model model,
            RedirectAttributes redirectAttributes) {

        // Xử lý upload thumbnail mới nếu có
        boolean hasNewThumbnail = thumbnailFile != null && !thumbnailFile.isEmpty();
        if (hasNewThumbnail) {
            try {
                String uploadedUrl = fileStorageService.store(thumbnailFile);
                request.setThumbnailUrl(uploadedUrl);
            } catch (Exception e) {
                log.warn("Failed to upload thumbnail for tour {}: {}", id, e.getMessage());
                bindingResult.rejectValue("thumbnailUrl", "upload.failed",
                        "Failed to upload image: " + e.getMessage());
            }
        }

        if (bindingResult.hasErrors()) {
            // Nếu đã upload file mới rồi mà form lỗi, xóa file mới để tránh orphan
            // và giữ nguyên thumbnail cũ để không làm hỏng ảnh hiện tại.
            if (hasNewThumbnail) {
                fileStorageService.delete(request.getThumbnailUrl());
                request.setThumbnailUrl(currentThumbnailUrl);
            }
            model.addAttribute("tourId", id);
            model.addAttribute("editMode", true);
            addFormData(model);
            return "admin/tours/form";
        }

        try {
            tourService.update(id, request);
            // Chỉ xóa thumbnail cũ sau khi update thành công
            if (hasNewThumbnail) {
                fileStorageService.delete(currentThumbnailUrl);
            }
            redirectAttributes.addFlashAttribute("successMessage",
                    "Tour '" + request.getTitle() + "' updated successfully.");
        } catch (IllegalArgumentException e) {
            if (thumbnailFile != null && !thumbnailFile.isEmpty()) {
                fileStorageService.delete(request.getThumbnailUrl());
                request.setThumbnailUrl(currentThumbnailUrl);
            }
            model.addAttribute("tourId", id);
            model.addAttribute("editMode", true);
            model.addAttribute("errorMessage", e.getMessage());
            addFormData(model);
            return "admin/tours/form";
        }

        return "redirect:/admin/tours";
    }

    // ----------------------------------------------------------------
    // DELETE
    // ----------------------------------------------------------------

    /**
     * POST /admin/tours/{id}/delete — Xoá tour.
     * Cũng xóa thumbnail file khỏi filesystem nếu có (best-effort).
     */
    @PostMapping("/{id}/delete")
    public String deleteTour(
            @PathVariable Long id,
            RedirectAttributes redirectAttributes) {

        try {
            Tour tour = tourService.getById(id);
            String title = tour.getTitle();
            // Xóa thumbnail trên filesystem trước khi xóa record DB
            fileStorageService.delete(tour.getThumbnailUrl());
            tourService.delete(id);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Tour '" + title + "' deleted successfully.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Cannot delete tour: " + e.getMessage());
        }

        return "redirect:/admin/tours";
    }
}
