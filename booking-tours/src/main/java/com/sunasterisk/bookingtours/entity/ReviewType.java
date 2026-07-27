package com.sunasterisk.bookingtours.entity;

/**
 * Enum phân loại nội dung của một bài review.
 * <ul>
 *   <li>{@link #PLACE} – Review về địa điểm du lịch.</li>
 *   <li>{@link #FOOD}  – Review về ẩm thực / nhà hàng.</li>
 *   <li>{@link #NEWS}  – Bài viết dạng tin tức / chia sẻ kinh nghiệm.</li>
 * </ul>
 */
public enum ReviewType {
    PLACE,
    FOOD,
    NEWS
}