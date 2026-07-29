package com.sunasterisk.bookingtours.repository;

import com.sunasterisk.bookingtours.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Tìm kiếm người dùng theo email, trả về Optional<User> để xử lý trường hợp không tìm thấy người dùng
     */
    Optional<User> findByEmail(String email);

    /**
     * Tìm user theo email, fetch sẵn Role trong cùng query.
     * Dùng cho JwtAuthenticationFilter — chạy ngoài transaction nên không thể
     * truy cập lazy proxy {@code user.role} sau khi query kết thúc.
     */
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.role WHERE u.email = :email")
    Optional<User> findByEmailWithRole(@Param("email") String email);

    /**
     * Tìm kiếm user theo keyword (email hoặc full_name) với phân trang.
     * Nếu keyword rỗng / null thì trả về tất cả.
     * <p>
     * Dùng {@code @EntityGraph} thay vì {@code LEFT JOIN FETCH} để tránh lỗi HHH000104
     * ("firstResult/maxResults specified with collection fetch; applying in memory").
     * {@code @EntityGraph} sinh ra LEFT JOIN thông thường, cho phép database xử lý
     * LIMIT/OFFSET đúng cách thay vì load toàn bộ dữ liệu vào bộ nhớ.
     * </p>
     */
    /** Lấy tất cả id của user đang active — dùng cho broadcast notification. */
    @Query("SELECT u.id FROM User u WHERE u.isActive = true")
    List<Long> findAllActiveUserIds();

    @EntityGraph(attributePaths = "role")
    @Query("SELECT u FROM User u " +
            "WHERE (:keyword IS NULL OR :keyword = '' " +
            "       OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "       OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<User> searchUsers(@Param("keyword") String keyword, Pageable pageable);
}
