package com.sunasterisk.bookingtours.repository;

import com.sunasterisk.bookingtours.entity.Like;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repository cho entity {@link Like}.
 */
public interface LikeRepository extends JpaRepository<Like, Long> {

    /**
     * Kiểm tra xem user đã like review chưa.
     *
     * @param reviewId id của review
     * @param userId   id của user
     * @return true nếu đã like
     */
    boolean existsByReviewIdAndUserId(Long reviewId, Long userId);

    /**
     * Tìm bản ghi like theo review và user để thực hiện unlike.
     *
     * @param reviewId id của review
     * @param userId   id của user
     * @return Optional<Like>
     */
    Optional<Like> findByReviewIdAndUserId(Long reviewId, Long userId);

    /**
     * Đếm tổng số lượt like của một review.
     *
     * @param reviewId id của review
     * @return số lượt like
     */
    long countByReviewId(Long reviewId);
}
