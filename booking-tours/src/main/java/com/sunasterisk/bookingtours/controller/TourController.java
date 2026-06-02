package com.sunasterisk.bookingtours.controller;

import com.sunasterisk.bookingtours.entity.Tour;
import com.sunasterisk.bookingtours.service.CategoryService;
import com.sunasterisk.bookingtours.service.TourService;
import com.sunasterisk.bookingtours.util.PaginationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Controller công khai cho trang danh sách và chi tiết tour.
 * Cho phép cả Guest và User xem danh sách, tìm kiếm và chi tiết tour.
 */
@Controller
@RequestMapping("/tours")
@RequiredArgsConstructor
public class TourController {

    private static final int PAGE_SIZE = 9;

    private final TourService tourService;
    private final CategoryService categoryService;

    /**
     * GET /tours — Danh sách tour công khai: phân trang, lọc category, tìm kiếm theo tên/địa điểm.
     *
     * @param keyword    từ khoá tìm kiếm (tiêu đề hoặc điểm đến)
     * @param categoryId id danh mục muốn lọc (null = tất cả)
     * @param page       chỉ số trang (0-based)
     * @param model      Spring MVC model
     * @return view name
     */
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
     *
     * @param id    id của tour
     * @param model Spring MVC model
     * @return view name
     */
    @GetMapping("/{id}")
    public String tourDetail(@PathVariable Long id, Model model) {
        Tour tour = tourService.getPublicById(id);
        model.addAttribute("tour", tour);
        return "tours/detail";
    }
}
