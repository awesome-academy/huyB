package com.sunasterisk.bookingtours.service;

import com.sunasterisk.bookingtours.dto.RatingRequest;
import com.sunasterisk.bookingtours.entity.Rating;

/**
 * Service xử lý nghiệp vụ rating tour.
 */
public interface RatingService {

    /**
     * 9.4 — Upsert rating của user cho một tour (1–5 sao).
     * Nếu user chưa rating → tạo mới.
     * Nếu user đã rating → cập nhật điểm mới.
     * Sau mỗi lần upsert, avg_rating trên bảng tours được tính lại.
     *
     * @param tourId  id của tour
     * @param email   email người dùng đang đăng nhập
     * @param request dữ liệu rating
     * @return bản ghi Rating sau khi lưu
     */
    Rating rate(Long tourId, String email, RatingRequest request);

    /**
     * Lấy điểm rating hiện tại của user cho tour (nếu có).
     *
     * @param tourId id của tour
     * @param email  email người dùng
     * @return điểm số (1–5) hoặc null nếu chưa rating
     */
    Short getUserRating(Long tourId, String email);
}
