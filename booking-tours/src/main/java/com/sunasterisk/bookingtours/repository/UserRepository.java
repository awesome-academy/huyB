package com.sunasterisk.bookingtours.repository;

import com.sunasterisk.bookingtours.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Tìm kiếm người dùng theo email, trả về Optional<User> để xử lý trường hợp không tìm thấy người dùng
     */
    Optional<User> findByEmail(String email);
}
