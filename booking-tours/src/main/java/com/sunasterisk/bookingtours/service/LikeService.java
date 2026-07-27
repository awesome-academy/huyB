package com.sunasterisk.bookingtours.service;

/**
 * Service xử lý nghiệp vụ like / unlike review.
 */
public interface LikeService {

    /**
     * 9.3 — Toggle like / unlike review.
     * Nếu user chưa like → tạo bản ghi Like và tăng likes_count.
     * Nếu user đã like → xóa bản ghi Like và giảm likes_count.
     *
     * @param reviewId id của review
     * @param email    email người dùng đang đăng nhập
     * @return true nếu sau thao tác user đang like, false nếu đã unlike
     */
    boolean toggleLike(Long reviewId, String email);

    /**
     * Kiểm tra user đã like review chưa.
     *
     * @param reviewId id của review
     * @param email    email người dùng
     * @return true nếu đã like
     */
    boolean isLikedByUser(Long reviewId, String email);
}
