package com.sunasterisk.bookingtours.repository;

import com.sunasterisk.bookingtours.entity.Rating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Repository cho entity {@link Rating}.
 */
public interface RatingRepository extends JpaRepository<Rating, Long> {

    /**
     * Tìm bản ghi rating theo tour và user.
     * Dùng để kiểm tra user đã rating chưa (upsert logic).
     *
     * @param tourId id của tour
     * @param userId id của user
     * @return Optional<Rating>
     */
    Optional<Rating> findByTourIdAndUserId(Long tourId, Long userId);

    /**
     * Tính điểm trung bình của tất cả rating thuộc một tour.
     * Trả về null nếu chưa có rating nào.
     *
     * @param tourId id của tour
     * @return điểm trung bình hoặc null
     */
    @Query("SELECT AVG(CAST(r.score AS double)) FROM Rating r WHERE r.tour.id = :tourId")
    Double calculateAvgRating(@Param("tourId") Long tourId);
}
