package com.sunasterisk.bookingtours.entity;

/**
 * Enum trạng thái hiển thị của một bài review.
 * <ul>
 *   <li>{@link #PUBLISHED} – Bài review đã được công khai, hiển thị cho mọi người dùng.</li>
 *   <li>{@link #HIDDEN}    – Bài review bị ẩn (do vi phạm chính sách hoặc admin can thiệp).</li>
 * </ul>
 */
public enum ReviewStatus {
    PUBLISHED,
    HIDDEN
}
