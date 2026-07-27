package com.sunasterisk.bookingtours.repository;

import com.sunasterisk.bookingtours.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Repository cho entity {@link Comment}.
 */
public interface CommentRepository extends JpaRepository<Comment, Long> {

    /**
     * Lấy danh sách comment gốc (parent_id IS NULL) của một review,
     * đồng thời fetch user để tránh N+1 khi render template.
     * Sắp xếp theo thời gian tạo tăng dần (cũ nhất lên trên).
     *
     * @param reviewId id của review
     * @return danh sách comment gốc chưa bị xóa mềm
     */
    @Query("SELECT c FROM Comment c JOIN FETCH c.user " +
            "WHERE c.review.id = :reviewId AND c.parent IS NULL AND c.isDeleted = false " +
            "ORDER BY c.createdAt ASC")
    List<Comment> findRootCommentsByReviewId(@Param("reviewId") Long reviewId);

    /**
     * Lấy danh sách reply (parent_id NOT NULL) của các comment gốc thuộc một review,
     * đồng thời fetch user để tránh N+1 khi render template.
     * Chỉ lấy tối đa 1 cấp reply theo thiết kế hệ thống.
     *
     * @param reviewId id của review
     * @return danh sách reply chưa bị xóa mềm
     */
    @Query("SELECT c FROM Comment c JOIN FETCH c.user JOIN FETCH c.parent " +
            "WHERE c.review.id = :reviewId AND c.parent IS NOT NULL AND c.isDeleted = false " +
            "ORDER BY c.createdAt ASC")
    List<Comment> findRepliesByReviewId(@Param("reviewId") Long reviewId);
}
