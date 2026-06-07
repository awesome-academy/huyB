package com.sunasterisk.bookingtours.service.impl;

import com.sunasterisk.bookingtours.entity.Like;
import com.sunasterisk.bookingtours.entity.Review;
import com.sunasterisk.bookingtours.entity.User;
import com.sunasterisk.bookingtours.exception.ResourceNotFoundException;
import com.sunasterisk.bookingtours.repository.LikeRepository;
import com.sunasterisk.bookingtours.repository.ReviewRepository;
import com.sunasterisk.bookingtours.repository.UserRepository;
import com.sunasterisk.bookingtours.service.LikeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LikeServiceImpl implements LikeService {

    private final LikeRepository likeRepository;
    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;

    // ----------------------------------------------------------------
    // 9.3 — Toggle like / unlike review (AJAX)
    // ----------------------------------------------------------------

    @Override
    @Transactional
    public boolean toggleLike(Long reviewId, String email) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review", reviewId));
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));

        boolean alreadyLiked = likeRepository.existsByReviewIdAndUserId(reviewId, user.getId());

        if (alreadyLiked) {
            // Unlike: xóa bản ghi like và giảm likes_count (tối thiểu 0)
            likeRepository.findByReviewIdAndUserId(reviewId, user.getId())
                    .ifPresent(likeRepository::delete);
            review.setLikesCount(Math.max(0, review.getLikesCount() - 1));
            reviewRepository.save(review);
            return false;
        } else {
            // Like: tạo bản ghi mới và tăng likes_count
            Like like = Like.builder()
                    .review(review)
                    .user(user)
                    .build();
            likeRepository.save(like);
            review.setLikesCount(review.getLikesCount() + 1);
            reviewRepository.save(review);
            return true;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isLikedByUser(Long reviewId, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
        return likeRepository.existsByReviewIdAndUserId(reviewId, user.getId());
    }
}
