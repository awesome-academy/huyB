package com.sunasterisk.bookingtours.controller;

import com.sunasterisk.bookingtours.dto.RatingRequest;
import com.sunasterisk.bookingtours.entity.Tour;
import com.sunasterisk.bookingtours.service.CategoryService;
import com.sunasterisk.bookingtours.service.RatingService;
import com.sunasterisk.bookingtours.service.TourService;
import com.sunasterisk.bookingtours.soap.CurrencyConversionClient;
import com.sunasterisk.bookingtours.soap.CurrencyConversionResponse;
import com.sunasterisk.bookingtours.util.PaginationUtils;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Controller công khai cho trang danh sách và chi tiết tour.
 * Cho phép cả Guest và User xem danh sách, tìm kiếm và chi tiết tour.
 */
@Tag(name = "Tours", description = "Danh sách, tìm kiếm và chi tiết tour")
@Controller
@RequestMapping("/tours")
@RequiredArgsConstructor
public class TourController {

    private static final int PAGE_SIZE = 9;

    private final TourService tourService;
    private final CategoryService categoryService;
    private final RatingService ratingService;
    private final CurrencyConversionClient currencyClient;

    /**
     * GET /tours — Danh sách tour công khai: phân trang, lọc category, tìm kiếm theo tên/địa điểm.
     *
     * @param keyword    từ khoá tìm kiếm (tiêu đề hoặc điểm đến)
     * @param categoryId id danh mục muốn lọc (null = tất cả)
     * @param page       chỉ số trang (0-based)
     * @param model      Spring MVC model
     * @return view name
     */
    @Operation(summary = "Danh sách tour công khai", description = "Phân trang, lọc theo category, tìm kiếm theo tên/địa điểm")
    @GetMapping
    public String listTours(
            @RequestParam(value = "keyword", defaultValue = "") String keyword,
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            Model model) {

        Pageable pageable = PageRequest.of(page, PAGE_SIZE);
        Page<Tour> tourPage = tourService.searchPublic(
                keyword.isBlank() ? null : keyword,
                categoryId,
                pageable);

        int totalPages = tourPage.getTotalPages();

        model.addAttribute("tourPage", tourPage);
        model.addAttribute("keyword", keyword);
        model.addAttribute("categoryId", categoryId);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("pageNumbers", PaginationUtils.getPageNumbers(page, totalPages));
        model.addAttribute("categories", categoryService.getAll());

        return "tours/list";
    }

    /**
     * GET /tours/{id} — Chi tiết tour công khai (chỉ ACTIVE).
     * Hiển thị đầy đủ thông tin: mô tả, giá, ngày khởi hành, rating, category, v.v.
     * 9.4 — Truyền thêm điểm rating hiện tại của user (nếu đã đăng nhập).
     *
     * @param id    id của tour
     * @param model Spring MVC model
     * @return view name
     */
    @Operation(summary = "Chi tiết tour công khai", description = "Chỉ hiển thị tour ACTIVE")
    @GetMapping("/{id}")
    public String tourDetail(@PathVariable Long id, Authentication authentication, Model model) {
        Tour tour = tourService.getPublicById(id);
        model.addAttribute("tour", tour);

        // SOAP: quy đổi giá tour sang USD và EUR qua SOAP endpoint nội bộ
        if (tour.getPrice() != null) {
            CurrencyConversionResponse usd = currencyClient.convertCurrency(tour.getPrice(), "VND", "USD");
            CurrencyConversionResponse eur = currencyClient.convertCurrency(tour.getPrice(), "VND", "EUR");
            model.addAttribute("priceUsd", usd != null ? usd.getConvertedAmount() : null);
            model.addAttribute("priceEur", eur != null ? eur.getConvertedAmount() : null);
        }

        // 9.4: Lấy điểm rating hiện tại của user để hiển thị trạng thái sao đã chọn
        Short userRating = null;
        if (authentication != null && authentication.isAuthenticated()) {
            userRating = ratingService.getUserRating(id, authentication.getName());
        }
        model.addAttribute("userRating", userRating);
        model.addAttribute("ratingRequest", new RatingRequest());

        return "tours/detail";
    }

    // ================================================================
    // 9.4 — Rating tour 1–5 sao
    // ================================================================

    /**
     * POST /tours/{id}/rate — User rating tour (1–5 sao).
     * Upsert: tạo mới hoặc cập nhật rating, sau đó cập nhật avg_rating trên tour.
     *
     * @param id               id của tour
     * @param ratingRequest    dữ liệu rating (score 1–5)
     * @param bindingResult    kết quả validate
     * @param authentication   thông tin user đăng nhập
     * @param redirectAttributes flash messages
     * @return redirect về trang chi tiết tour
     */
    @Operation(summary = "Rating tour", description = "User rating tour 1–5 sao, upsert nếu đã rating trước đó")
    @PostMapping("/{id}/rate")
    public String rateTour(@PathVariable Long id,
                           @Valid @ModelAttribute RatingRequest ratingRequest,
                           BindingResult bindingResult,
                           Authentication authentication,
                           RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Invalid rating score (must be 1–5).");
            return "redirect:/tours/" + id;
        }
        try {
            ratingService.rate(id, authentication.getName(), ratingRequest);
            redirectAttributes.addFlashAttribute("successMessage", "Thank you for your rating!");
        } catch (Exception e) {
            // Không đưa e.getMessage() ra UI — có thể lộ chi tiết nội bộ.
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to submit rating. Please try again.");
        }
        return "redirect:/tours/" + id;
    }
}
