package com.sunasterisk.bookingtours.repository;

import com.sunasterisk.bookingtours.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Tìm kiếm người dùng theo email, trả về Optional<User> để xử lý trường hợp không tìm thấy người dùng
     */
    Optional<User> findByEmail(String email);

    /**
     * Tìm kiếm user theo keyword (email hoặc full_name) với phân trang.
     * Nếu keyword rỗng / null thì trả về tất cả.
     */
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.role " +
            "WHERE (:keyword IS NULL OR :keyword = '' " +
            "       OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "       OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<User> searchUsers(@Param("keyword") String keyword, Pageable pageable);
}
