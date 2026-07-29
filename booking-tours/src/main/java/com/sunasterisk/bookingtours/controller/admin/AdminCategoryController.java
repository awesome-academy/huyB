package com.sunasterisk.bookingtours.controller.admin;

import com.sunasterisk.bookingtours.dto.CategoryRequest;
import com.sunasterisk.bookingtours.entity.Category;
import com.sunasterisk.bookingtours.service.CategoryService;
import com.sunasterisk.bookingtours.util.PaginationUtils;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Tag(name = "Admin - Categories", description = "Quản lý danh mục tour")
@Controller
@RequestMapping("/admin/categories")
@RequiredArgsConstructor
public class AdminCategoryController {

    private static final int PAGE_SIZE = 15;

    private final CategoryService categoryService;

    // ----------------------------------------------------------------
    // LIST
    // ----------------------------------------------------------------

    /**
     * GET /admin/categories — Danh sách category có phân trang và tìm kiếm.
     */
    @Operation(summary = "Danh sách category (Admin)", description = "Phân trang và tìm kiếm danh mục tour")
    @GetMapping
    public String listCategories(
            @RequestParam(value = "keyword", defaultValue = "") String keyword,
            @RequestParam(value = "page", defaultValue = "0") int page,
            Model model) {

        Pageable pageable = PageRequest.of(page, PAGE_SIZE);
        Page<Category> categoryPage = categoryService.search(
                keyword.isBlank() ? null : keyword, pageable);

        int totalPages = categoryPage.getTotalPages();

        model.addAttribute("categoryPage", categoryPage);
        model.addAttribute("keyword", keyword);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("pageNumbers", PaginationUtils.getPageNumbers(page, totalPages));

        return "admin/categories/list";
    }

    // ----------------------------------------------------------------
    // CREATE
    // ----------------------------------------------------------------

    /**
     * GET /admin/categories/new — Form tạo category mới.
     */
    @Operation(summary = "Form tạo category", description = "Hiển thị form tạo danh mục mới")
    @GetMapping("/new")
    public String showCreateForm(Model model) {
        model.addAttribute("categoryRequest", new CategoryRequest());
        model.addAttribute("editMode", false);
        return "admin/categories/form";
    }

    /**
     * POST /admin/categories — Lưu category mới.
     */
    @Operation(summary = "Tạo category", description = "Validate và lưu danh mục tour mới")
    @PostMapping
    public String createCategory(
            @Valid @ModelAttribute("categoryRequest") CategoryRequest request,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            model.addAttribute("editMode", false);
            return "admin/categories/form";
        }

        try {
            categoryService.create(request);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Category '" + request.getName() + "' created successfully.");
        } catch (IllegalArgumentException e) {
            model.addAttribute("editMode", false);
            model.addAttribute("errorMessage", e.getMessage());
            return "admin/categories/form";
        }

        return "redirect:/admin/categories";
    }

    // ----------------------------------------------------------------
    // EDIT
    // ----------------------------------------------------------------

    /**
     * GET /admin/categories/{id}/edit — Form chỉnh sửa category.
     */
    @Operation(summary = "Form chỉnh sửa category", description = "Pre-fill form với dữ liệu category hiện tại")
    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Long id, Model model) {
        Category category = categoryService.getById(id);

        CategoryRequest request = new CategoryRequest();
        request.setName(category.getName());
        request.setDescription(category.getDescription());

        model.addAttribute("categoryRequest", request);
        model.addAttribute("categoryId", id);
        model.addAttribute("editMode", true);
        return "admin/categories/form";
    }

    /**
     * POST /admin/categories/{id} — Cập nhật category.
     */
    @Operation(summary = "Cập nhật category", description = "Validate và lưu thay đổi danh mục tour")
    @PostMapping("/{id}")
    public String updateCategory(
            @PathVariable Long id,
            @Valid @ModelAttribute("categoryRequest") CategoryRequest request,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            model.addAttribute("categoryId", id);
            model.addAttribute("editMode", true);
            return "admin/categories/form";
        }

        try {
            categoryService.update(id, request);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Category updated successfully.");
        } catch (IllegalArgumentException e) {
            model.addAttribute("categoryId", id);
            model.addAttribute("editMode", true);
            model.addAttribute("errorMessage", e.getMessage());
            return "admin/categories/form";
        }

        return "redirect:/admin/categories";
    }

    // ----------------------------------------------------------------
    // DELETE
    // ----------------------------------------------------------------

    /**
     * POST /admin/categories/{id}/delete — Xóa category.
     */
    @Operation(summary = "Xóa category", description = "Xóa danh mục tour theo id")
    @PostMapping("/{id}/delete")
    public String deleteCategory(
            @PathVariable Long id,
            RedirectAttributes redirectAttributes) {

        try {
            Category category = categoryService.getById(id);
            String name = category.getName();
            categoryService.delete(id);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Category '" + name + "' deleted successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Cannot delete category: " + e.getMessage());
        }

        return "redirect:/admin/categories";
    }
}
