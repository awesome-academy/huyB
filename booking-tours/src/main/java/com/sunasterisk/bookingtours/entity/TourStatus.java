package com.sunasterisk.bookingtours.entity;

/**
 * Trạng thái hoạt động của tour du lịch.
 *
 * <ul>
 *   <li>{@link #ACTIVE}   — tour đang mở đặt chỗ, hiển thị công khai.</li>
 *   <li>{@link #INACTIVE} — tour tạm ngừng, không hiển thị cho khách.</li>
 * </ul>
 */
public enum TourStatus {
    /** Tour đang hoạt động, hiển thị và nhận đặt chỗ. */
    ACTIVE,

    /** Tour tạm ngừng, ẩn khỏi danh sách công khai. */
    INACTIVE
}
