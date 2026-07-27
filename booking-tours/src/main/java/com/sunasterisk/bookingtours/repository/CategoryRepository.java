package com.sunasterisk.bookingtours.repository;

import com.sunasterisk.bookingtours.entity.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);

    /**
     * Kiểm tra tên đã tồn tại, bỏ qua category đang cập nhật (dùng khi edit).
     */
    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    @Query("""
            SELECT c FROM Category c
            WHERE (CAST(:keyword AS string) IS NULL
                   OR LOWER(c.name) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%')))
            ORDER BY c.name ASC
            """)
    Page<Category> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);
}
