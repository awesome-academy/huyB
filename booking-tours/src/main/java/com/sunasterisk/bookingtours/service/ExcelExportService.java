package com.sunasterisk.bookingtours.service;

import com.sunasterisk.bookingtours.entity.BookingStatus;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.time.LocalDate;

/**
 * Tạo file Excel cho chức năng export dữ liệu Admin.
 */
public interface ExcelExportService {

    /**
     * Export danh sách booking khớp với bộ lọc ra file Excel.
     *
     * @param keyword  từ khoá tìm kiếm (null → bỏ qua)
     * @param status   trạng thái (null → tất cả)
     * @param fromDate ngày khởi hành từ (null → bỏ qua)
     * @param toDate   ngày khởi hành đến (null → bỏ qua)
     * @return XSSFWorkbook sẵn sàng ghi ra response stream
     */
    XSSFWorkbook exportBookings(String keyword, BookingStatus status,
                                LocalDate fromDate, LocalDate toDate);
}
