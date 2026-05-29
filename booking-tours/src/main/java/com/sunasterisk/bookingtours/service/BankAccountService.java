package com.sunasterisk.bookingtours.service;

import com.sunasterisk.bookingtours.dto.BankAccountRequest;
import com.sunasterisk.bookingtours.entity.UserBankAccount;

import java.util.List;

public interface BankAccountService {

    /**
     * Lấy danh sách tài khoản ngân hàng của user.
     * Sắp xếp: default trước, sau đó theo thời gian tạo mới nhất.
     *
     * @param userEmail email của user đang đăng nhập
     * @return danh sách UserBankAccount
     */
    List<UserBankAccount> getAccountsByUser(String userEmail);

    /**
     * Thêm tài khoản ngân hàng mới.
     * Nếu request.isDefault = true → unset default cũ, set default mới.
     * Nếu là tài khoản đầu tiên → tự động set default.
     *
     * @param userEmail email của user đang đăng nhập
     * @param request   DTO chứa thông tin tài khoản
     * @return UserBankAccount vừa tạo
     */
    UserBankAccount addAccount(String userEmail, BankAccountRequest request);

    /**
     * Cập nhật thông tin tài khoản ngân hàng.
     * Chỉ cho phép sửa tài khoản thuộc về user đang đăng nhập.
     *
     * @param userEmail email của user đang đăng nhập
     * @param accountId ID tài khoản cần cập nhật
     * @param request   DTO chứa thông tin mới
     * @return UserBankAccount đã cập nhật
     */
    UserBankAccount updateAccount(String userEmail, Long accountId, BankAccountRequest request);

    /**
     * Xóa tài khoản ngân hàng.
     * Chỉ cho phép xóa tài khoản thuộc về user đang đăng nhập.
     * Nếu xóa tài khoản default và còn tài khoản khác → tự động set default cho tài khoản cũ nhất.
     *
     * @param userEmail email của user đang đăng nhập
     * @param accountId ID tài khoản cần xóa
     */
    void deleteAccount(String userEmail, Long accountId);

    /**
     * Đặt một tài khoản làm tài khoản mặc định.
     * Unset default của tài khoản hiện tại, set default cho tài khoản mới.
     *
     * @param userEmail email của user đang đăng nhập
     * @param accountId ID tài khoản muốn set default
     */
    void setDefaultAccount(String userEmail, Long accountId);
}
