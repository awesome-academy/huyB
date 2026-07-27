package com.sunasterisk.bookingtours.service.impl;

import com.sunasterisk.bookingtours.dto.ReviewRequest;
import com.sunasterisk.bookingtours.entity.Review;
import com.sunasterisk.bookingtours.entity.ReviewStatus;
import com.sunasterisk.bookingtours.entity.ReviewType;
import com.sunasterisk.bookingtours.entity.User;
import com.sunasterisk.bookingtours.exception.ResourceNotFoundException;
import com.sunasterisk.bookingtours.repository.ReviewRepository;
import com.sunasterisk.bookingtours.repository.UserRepository;
import com.sunasterisk.bookingtours.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {
    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;

    @Override
    public Page<Review> findPublishedByType(ReviewType reviewType, Pageable pageable) {
        return reviewRepository.findAllByStatusAndType(ReviewStatus.PUBLISHED, reviewType, pageable);
    }

    @Override
    public Page<Review> findAllByType(ReviewType reviewType, Pageable pageable) {
        return reviewRepository.findAllByType(reviewType, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Review findById(Long id) {
        Review review = reviewRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Review", id));
        // Khởi tạo lazy proxy User trong khi session còn mở
        // để Thymeleaf có thể truy cập review.user sau khi transaction đóng
        Hibernate.initialize(review.getUser());
        return review;
    }

    @Override
    @Transactional
    public Review create(String email, ReviewRequest reviewRequest) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));

        Review review = Review.builder()
                .title(reviewRequest.getTitle().trim())
                .content(reviewRequest.getContent().trim())
                .type(reviewRequest.getReviewType())
                .user(user)
                .status(ReviewStatus.PUBLISHED)
                .build();
        return reviewRepository.save(review);
    }

    @Override
    @Transactional
    public Review update(Long id, String email, ReviewRequest reviewRequest) {
        Review review = findById(id);
        if (!review.getUser().getEmail().equals(email)) {
            throw new AccessDeniedException("You are not allowed to edit this review.");
        }
        review.setTitle(reviewRequest.getTitle().trim());
        review.setContent(reviewRequest.getContent().trim());
        review.setType(reviewRequest.getReviewType());
        return reviewRepository.save(review);
    }

    @Override
    @Transactional
    public void delete(Long id, String email) {
        Review review = findById(id);
        if (!review.getUser().getEmail().equals(email)) {
            throw new AccessDeniedException("You are not allowed to delete this review.");
        }
        reviewRepository.delete(review);
    }

    @Override
    @Transactional
    public void hide(Long id) {
        Review review = findById(id);
        review.setStatus(ReviewStatus.HIDDEN);
        reviewRepository.save(review);
    }

    @Override
    @Transactional
    public void restore(Long id) {
        Review review = findById(id);
        review.setStatus(ReviewStatus.PUBLISHED);
        reviewRepository.save(review);
    }

    @Override
    @Transactional
    public void adminDelete(Long id) {
        Review review = findById(id);
        reviewRepository.delete(review);
    }

    @Override
    public long countByStatus(ReviewStatus status) {
        return reviewRepository.countByStatus(status);
    }
}
