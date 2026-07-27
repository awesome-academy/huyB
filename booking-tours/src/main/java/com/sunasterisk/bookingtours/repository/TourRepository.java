package com.sunasterisk.bookingtours.repository;

import com.sunasterisk.bookingtours.entity.Tour;
import com.sunasterisk.bookingtours.entity.TourStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Repository cho entity {@link Tour}.
 */
public interface TourRepository extends JpaRepository<Tour, Long> {

    boolean existsByTitleIgnoreCase(String title);

    boolean existsByTitleIgnoreCaseAndIdNot(String title, Long id);

    /**
     * Tìm tour theo id và status — dùng để lấy chi tiết tour công khai (chỉ ACTIVE).
     *
     * @param id     id của tour
     * @param status trạng thái tour (truyền {@link TourStatus#ACTIVE})
     * @return {@code Optional<Tour>} với category đã được fetch
     */
    @EntityGraph(attributePaths = "category")
    Optional<Tour> findByIdAndStatus(Long id, TourStatus status);

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

    /**
     * Tìm kiếm tour công khai (chỉ ACTIVE) theo từ khoá và/hoặc category.
     * Dùng cho trang danh sách tour của Guest / User.
     *
     * <p>Dùng {@code @Param("status")} thay vì inline enum trong JPQL để tránh
     * lỗi parse với một số phiên bản Hibernate.
     * countQuery tường minh (không join) để Spring Data JPA tính đúng totalElements.
     *
     * <p>Dùng {@code LEFT JOIN t.category c} thay vì path navigation {@code t.category.id}
     * để tránh implicit INNER JOIN do JPQL spec sinh ra. Vì {@code category_id} là nullable
     * (ON DELETE SET NULL), INNER JOIN sẽ loại bỏ các tour có {@code category_id = NULL}
     * ngay cả khi {@code :categoryId IS NULL} (không lọc theo category).
     *
     * @param keyword    từ khoá tìm kiếm (null → bỏ qua)
     * @param categoryId id danh mục (null → tất cả danh mục)
     * @param status     trạng thái tour cần lọc (truyền {@link TourStatus#ACTIVE})
     * @param pageable   thông tin phân trang
     * @return {@code Page<Tour>} ACTIVE với category đã được fetch
     */
    @EntityGraph(attributePaths = "category")
    @Query(value = "SELECT t FROM Tour t LEFT JOIN t.category c " +
                   "WHERE t.status = :status " +
                   "AND (:keyword IS NULL OR :keyword = '' " +
                   "       OR LOWER(t.title)       LIKE LOWER(CONCAT('%', :keyword, '%')) " +
                   "       OR LOWER(t.destination) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
                   "AND (:categoryId IS NULL OR c.id = :categoryId) " +
                   "ORDER BY t.createdAt DESC",
           countQuery = "SELECT COUNT(t) FROM Tour t LEFT JOIN t.category c " +
                        "WHERE t.status = :status " +
                        "AND (:keyword IS NULL OR :keyword = '' " +
                        "       OR LOWER(t.title)       LIKE LOWER(CONCAT('%', :keyword, '%')) " +
                        "       OR LOWER(t.destination) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
                        "AND (:categoryId IS NULL OR c.id = :categoryId)")
    Page<Tour> searchPublic(@Param("keyword") String keyword,
                            @Param("categoryId") Long categoryId,
                            @Param("status") TourStatus status,
                            Pageable pageable);
}
