package com.sunasterisk.bookingtours.repository;

import com.sunasterisk.bookingtours.entity.Tour;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository cho entity {@link Tour}.
 */
public interface TourRepository extends JpaRepository<Tour, Long> {

    boolean existsByTitleIgnoreCase(String title);

    boolean existsByTitleIgnoreCaseAndIdNot(String title, Long id);

    /**
     * Tìm kiếm tour theo từ khoá (title hoặc destination), kết quả sắp xếp theo ngày tạo mới nhất.
     *
     * <p>Dùng {@code @EntityGraph} thay vì {@code LEFT JOIN FETCH} để nhất quán với
     * {@link UserRepository#searchUsers} trong cùng codebase.
     * Kỹ thuật: {@code @EntityGraph} sinh LEFT JOIN thông thường nhưng Spring Data JPA
     * tự động tách {@code countQuery} riêng (không có join), đảm bảo pagination đúng
     * ở SQL level mà không cần khai báo {@code countQuery} thủ công.
     *
     * <p>Lưu ý: HHH000104 ("applying in memory") chỉ xảy ra với collection fetch
     * ({@code @OneToMany} / {@code @ManyToMany}). {@code Tour.category} là {@code @ManyToOne}
     * (single-row, không nhân bản) nên cả {@code LEFT JOIN FETCH} lẫn {@code @EntityGraph}
     * đều an toàn với Pageable.
     *
     * @param keyword  từ khoá tìm kiếm (null hoặc rỗng → trả về tất cả)
     * @param pageable thông tin phân trang
     * @return {@code Page<Tour>} với {@code category} đã được fetch
     */
    @EntityGraph(attributePaths = "category")
    @Query("SELECT t FROM Tour t " +
           "WHERE (:keyword IS NULL OR :keyword = '' " +
           "       OR LOWER(t.title)       LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "       OR LOWER(t.destination) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "ORDER BY t.createdAt DESC")
    Page<Tour> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);
}
