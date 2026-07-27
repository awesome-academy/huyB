package com.sunasterisk.bookingtours.service.impl;

import com.sunasterisk.bookingtours.dto.RatingRequest;
import com.sunasterisk.bookingtours.entity.Rating;
import com.sunasterisk.bookingtours.entity.Tour;
import com.sunasterisk.bookingtours.entity.User;
import com.sunasterisk.bookingtours.exception.ResourceNotFoundException;
import com.sunasterisk.bookingtours.repository.RatingRepository;
import com.sunasterisk.bookingtours.repository.TourRepository;
import com.sunasterisk.bookingtours.repository.UserRepository;
import com.sunasterisk.bookingtours.service.RatingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class RatingServiceImpl implements RatingService {

    private final RatingRepository ratingRepository;
    private final TourRepository tourRepository;
    private final UserRepository userRepository;

    // ----------------------------------------------------------------
    // 9.4 — Rating tour 1–5 sao; cập nhật avg_rating trên tours
    // ----------------------------------------------------------------

    @Override
    @Transactional
    public Rating rate(Long tourId, String email, RatingRequest request) {
        Tour tour = tourRepository.findById(tourId)
                .orElseThrow(() -> new ResourceNotFoundException("Tour", tourId));
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));

        // Upsert: tìm rating hiện tại hoặc tạo mới
        Rating rating = ratingRepository.findByTourIdAndUserId(tourId, user.getId())
                .orElse(Rating.builder().tour(tour).user(user).build());

        rating.setScore(request.getScore());
        rating = ratingRepository.save(rating);

        // Tính lại avg_rating và cập nhật vào bảng tours
        Double avg = ratingRepository.calculateAvgRating(tourId);
        BigDecimal avgRating = (avg != null)
                ? BigDecimal.valueOf(avg).setScale(1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        tour.setAvgRating(avgRating);
        tourRepository.save(tour);

        return rating;
    }

    @Override
    @Transactional(readOnly = true)
    public Short getUserRating(Long tourId, String email) {
        return userRepository.findByEmail(email)
                .flatMap(user -> ratingRepository.findByTourIdAndUserId(tourId, user.getId()))
                .map(Rating::getScore)
                .orElse(null);
    }
}
