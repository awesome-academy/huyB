package com.sunasterisk.bookingtours.service.impl;

import com.sunasterisk.bookingtours.dto.CommentRequest;
import com.sunasterisk.bookingtours.entity.Comment;
import com.sunasterisk.bookingtours.entity.Review;
import com.sunasterisk.bookingtours.entity.User;
import com.sunasterisk.bookingtours.exception.ResourceNotFoundException;
import com.sunasterisk.bookingtours.repository.CommentRepository;
import com.sunasterisk.bookingtours.repository.ReviewRepository;
import com.sunasterisk.bookingtours.repository.UserRepository;
import com.sunasterisk.bookingtours.service.CommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    private final CommentRepository commentRepository;
    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;

    // ----------------------------------------------------------------
    // 9.1 — Hiển thị danh sách comments + replies
    // ----------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<Comment> findRootComments(Long reviewId) {
        // Lấy danh sách comment gốc (parent IS NULL) đã fetch user, chưa bị xóa mềm
        return commentRepository.findRootCommentsByReviewId(reviewId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Comment> findReplies(Long reviewId) {
        // Lấy tất cả reply (parent IS NOT NULL) của review, đã fetch user và parent
        return commentRepository.findRepliesByReviewId(reviewId);
    }

    // ----------------------------------------------------------------
    // 9.2 — Thêm comment / Reply comment (1 cấp)
    // ----------------------------------------------------------------

    @Override
    @Transactional
    public Comment addComment(Long reviewId, String email, CommentRequest request) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review", reviewId));
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));

        Comment comment = Comment.builder()
                .review(review)
                .user(user)
                .parent(null)   // comment gốc không có parent
                .content(request.getContent().trim())
                .build();

        return commentRepository.save(comment);
    }

    @Override
    @Transactional
    public Comment addReply(Long reviewId, Long parentId, String email, CommentRequest request) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review", reviewId));
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));

        Comment parent = commentRepository.findById(parentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment", parentId));

        // Kiểm tra giới hạn 1 cấp: parent phải là comment gốc (không phải reply)
        if (parent.getParent() != null) {
            throw new IllegalStateException("Replies to replies are not allowed (max 1 level).");
        }

        // Đảm bảo comment cha thuộc cùng review
        if (!parent.getReview().getId().equals(reviewId)) {
            throw new IllegalArgumentException("Parent comment does not belong to the specified review.");
        }

        Comment reply = Comment.builder()
                .review(review)
                .user(user)
                .parent(parent) // trỏ về comment gốc
                .content(request.getContent().trim())
                .build();

        return commentRepository.save(reply);
    }

    @Override
    @Transactional
    public void deleteComment(Long commentId, String email) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment", commentId));

        // Chỉ chủ sở hữu mới được xóa comment của mình
        if (!comment.getUser().getEmail().equals(email)) {
            throw new AccessDeniedException("You are not allowed to delete this comment.");
        }

        // Soft delete — không xóa bản ghi khỏi DB
        comment.setDeleted(true);
        commentRepository.save(comment);
    }
}
