package com.sunasterisk.bookingtours.repository;

import com.sunasterisk.bookingtours.entity.UserBankAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserBankAccountRepository extends JpaRepository<UserBankAccount, Long> {

    /**
     * Lấy danh sách tài khoản ngân hàng của user, sắp xếp: default trước, sau đó theo created_at DESC.
     */
    List<UserBankAccount> findByUserIdOrderByIsDefaultDescCreatedAtDesc(Long userId);

    /**
     * Lấy tài khoản mặc định của user.
     */
    Optional<UserBankAccount> findByUserIdAndIsDefaultTrue(Long userId);

    /**
     * Kiểm tra tài khoản có thuộc về user không.
     */
    boolean existsByIdAndUserId(Long id, Long userId);

    /**
     * Bỏ default tất cả tài khoản của user (trước khi set default mới).
     */
    @Modifying
    @Query("UPDATE UserBankAccount u SET u.isDefault = false WHERE u.user.id = :userId")
    void clearDefaultByUserId(@Param("userId") Long userId);
}
