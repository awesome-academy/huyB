package com.sunasterisk.bookingtours.service;

import com.sunasterisk.bookingtours.dto.CommentRequest;
import com.sunasterisk.bookingtours.entity.Comment;

import java.util.List;

/**
 * Service xử lý nghiệp vụ comment và reply cho review.
 */
public interface CommentService {

    /**
     * 9.1 — Lấy danh sách comment gốc của một review (không bao gồm reply).
     *
     * @param reviewId id của review
     * @return danh sách comment gốc, sắp xếp theo thời gian tạo tăng dần
     */
    List<Comment> findRootComments(Long reviewId);

    /**
     * 9.1 — Lấy danh sách reply của tất cả comment gốc thuộc một review.
     *
     * @param reviewId id của review
     * @return danh sách reply, sắp xếp theo thời gian tạo tăng dần
     */
    List<Comment> findReplies(Long reviewId);

    /**
     * 9.2 — Thêm comment gốc vào một review.
     *
     * @param reviewId id của review
     * @param email    email người dùng đang đăng nhập
     * @param request  dữ liệu comment
     * @return comment vừa tạo
     */
    Comment addComment(Long reviewId, String email, CommentRequest request);

    /**
     * 9.2 — Reply một comment gốc (chỉ 1 cấp — không cho phép reply một reply).
     *
     * @param reviewId id của review chứa comment gốc
     * @param parentId id của comment gốc cần reply
     * @param email    email người dùng đang đăng nhập
     * @param request  dữ liệu reply
     * @return reply vừa tạo
     * @throws IllegalStateException nếu parentId đã là một reply (vi phạm giới hạn 1 cấp)
     */
    Comment addReply(Long reviewId, Long parentId, String email, CommentRequest request);

    /**
     * 9.2 — Xóa mềm (soft-delete) một comment hoặc reply.
     * Chỉ chủ sở hữu mới được xóa.
     *
     * @param commentId id của comment
     * @param email     email người dùng đang đăng nhập
     */
    void deleteComment(Long commentId, String email);
}
