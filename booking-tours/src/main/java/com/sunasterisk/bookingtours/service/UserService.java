package com.sunasterisk.bookingtours.service;

import com.sunasterisk.bookingtours.dto.RegisterRequest;
import com.sunasterisk.bookingtours.entity.User;

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
}
