package com.sunasterisk.bookingtours.service;

import com.sunasterisk.bookingtours.dto.TourRequest;
import com.sunasterisk.bookingtours.entity.Tour;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service quản lý nghiệp vụ cho entity {@link Tour}.
 */
public interface TourService {

    /**
     * Lấy danh sách tour có phân trang, tìm kiếm theo tiêu đề hoặc điểm đến.
     *
     * @param keyword  từ khoá tìm kiếm (null / rỗng → trả về tất cả)
     * @param pageable thông tin phân trang
     * @return {@code Page<Tour>} với category đã được fetch
     */
    Page<Tour> search(String keyword, Pageable pageable);

    /**
     * Lấy thông tin một tour theo id.
     *
     * @param id id của tour
     * @return tour tìm thấy
     * @throws com.sunasterisk.bookingtours.exception.ResourceNotFoundException nếu không tìm thấy
     */
    Tour getById(Long id);

    /**
     * Tạo mới tour. Kiểm tra tiêu đề trùng lặp (case-insensitive).
     *
     * @param tourRequest DTO chứa thông tin tour
     * @return tour vừa được lưu
     * @throws IllegalArgumentException nếu tiêu đề đã tồn tại
     */
    Tour create(TourRequest tourRequest);

    /**
     * Cập nhật tour. Kiểm tra tiêu đề trùng với các tour khác (bỏ qua chính nó).
     *
     * @param id          id của tour cần cập nhật
     * @param tourRequest DTO chứa thông tin mới
     * @return tour đã cập nhật
     * @throws IllegalArgumentException nếu tiêu đề đã tồn tại ở tour khác
     */
    Tour update(Long id, TourRequest tourRequest);

    /**
     * Xoá tour khỏi hệ thống.
     *
     * @param id id của tour cần xoá
     * @throws com.sunasterisk.bookingtours.exception.ResourceNotFoundException nếu không tìm thấy
     */
    void delete(Long id);
}
