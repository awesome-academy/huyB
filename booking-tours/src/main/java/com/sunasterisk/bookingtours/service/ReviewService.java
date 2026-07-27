package com.sunasterisk.bookingtours.service;

import com.sunasterisk.bookingtours.dto.ReviewRequest;
import com.sunasterisk.bookingtours.entity.Review;
import com.sunasterisk.bookingtours.entity.ReviewStatus;
import com.sunasterisk.bookingtours.entity.ReviewType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ReviewService {
    Page<Review> findPublishedByType(ReviewType reviewType, Pageable pageable);

    Page<Review> findAllByType(ReviewType reviewType, Pageable pageable);

    Review findById(Long id);

    Review create(String email, ReviewRequest reviewRequest);

    Review update(Long id, String email, ReviewRequest reviewRequest);

    void delete(Long id, String email);

    void hide(Long id);

    void restore(Long id);

    void adminDelete(Long id);

    long countByStatus(ReviewStatus status);
}
