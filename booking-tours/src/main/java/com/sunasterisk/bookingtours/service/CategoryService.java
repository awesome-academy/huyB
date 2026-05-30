package com.sunasterisk.bookingtours.service;

import com.sunasterisk.bookingtours.dto.CategoryRequest;
import com.sunasterisk.bookingtours.entity.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface CategoryService {

    /**
     * Lấy danh sách tất cả category (không phân trang, dùng cho dropdown).
     */
    List<Category> getAll();

    /**
     * Lấy danh sách category có phân trang và tìm kiếm theo tên.
     *
     * @param keyword  từ khoá tìm kiếm (null / rỗng → trả về tất cả)
     * @param pageable thông tin phân trang
     * @return Page&lt;Category&gt;
     */
    Page<Category> search(String keyword, Pageable pageable);

    /**
     * Lấy thông tin một category theo id.
     */
    Category getById(Long id);

    /**
     * Tạo mới category.
     * Kiểm tra tên trùng lặp (case-insensitive).
     *
     * @param request DTO chứa thông tin category
     * @return Category vừa được lưu
     */
    Category create(CategoryRequest request);

    /**
     * Cập nhật category.
     * Kiểm tra tên trùng lặp với các category khác (bỏ qua chính nó).
     *
     * @param id      id của category cần cập nhật
     * @param request DTO chứa thông tin mới
     * @return Category đã cập nhật
     */
    Category update(Long id, CategoryRequest request);

    /**
     * Xóa category.
     * Nếu category đang được sử dụng bởi tour thì từ chối xóa.
     *
     * @param id id của category cần xóa
     */
    void delete(Long id);
}
