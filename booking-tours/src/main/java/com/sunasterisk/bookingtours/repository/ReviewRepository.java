package com.sunasterisk.bookingtours.repository;

import com.sunasterisk.bookingtours.entity.Review;
import com.sunasterisk.bookingtours.entity.ReviewStatus;
import com.sunasterisk.bookingtours.entity.ReviewType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    @Query("SELECT r FROM Review r JOIN FETCH r.user WHERE r.status = :status AND (:type IS NULL OR r.type = :type) ORDER BY r.createdAt DESC")
    Page<Review> findAllByStatusAndType(@Param("status") ReviewStatus status,
                                        @Param("type") ReviewType type,
                                        Pageable pageable);

    @Query("SELECT r FROM Review r JOIN FETCH r.user WHERE (:type IS NULL OR r.type = :type) ORDER BY r.createdAt DESC")
    Page<Review> findAllByType(@Param("type") ReviewType type, Pageable pageable);

    long countByStatus(ReviewStatus status);
}
