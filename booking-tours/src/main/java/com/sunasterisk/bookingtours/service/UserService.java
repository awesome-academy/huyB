package com.sunasterisk.bookingtours.service;

import com.sunasterisk.bookingtours.dto.ProfileUpdateRequest;
import com.sunasterisk.bookingtours.dto.RegisterRequest;
import com.sunasterisk.bookingtours.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserService {

    /**
     * Đăng ký tài khoản mới.
     * Validate email unique + password confirm, mã hóa BCrypt rồi lưu DB.
     *
     * @param request DTO chứa thông tin đăng ký
     * @return User vừa được lưu
     */
    User register(RegisterRequest request);

    /**
     * Kiểm tra email đã tồn tại trong DB hay chưa.
     */
    boolean emailExists(String email);

    /**
     * Lấy thông tin User theo email (username trong SecurityContext).
     *
     * @param email email của user đang đăng nhập
     * @return User entity
     */
    User getByEmail(String email);

    /**
     * Cập nhật hồ sơ cá nhân: full_name, phone, avatar_url.
     *
     * @param email   email của user đang đăng nhập (định danh)
     * @param request DTO chứa thông tin cần cập nhật
     * @return User đã được cập nhật
     */
    User updateProfile(String email, ProfileUpdateRequest request);

    // ----------------------------------------------------------------
    // Admin
    // ----------------------------------------------------------------

    /**
     * Lấy danh sách user có phân trang và tìm kiếm theo email / tên.
     *
     * @param keyword  từ khoá tìm kiếm (có thể null / rỗng → trả về tất cả)
     * @param pageable thông tin phân trang
     * @return Page<User>
     */
    Page<User> searchUsers(String keyword, Pageable pageable);

    /**
     * Khoá hoặc mở khoá tài khoản user.
     * Nếu đang active → set isActive = false; ngược lại set = true.
     *
     * @param userId id của user cần thay đổi trạng thái
     * @return User đã được cập nhật
     */
    User toggleLock(Long userId);
}
