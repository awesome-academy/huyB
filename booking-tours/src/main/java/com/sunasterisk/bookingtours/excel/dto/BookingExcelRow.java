package com.sunasterisk.bookingtours.excel.dto;

import com.sunasterisk.bookingtours.excel.annotation.ExcelColumn;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTO mutable chứa một dòng booking để xuất Excel.
 * Phải là class (không dùng record) — ExcelMapper dùng no-arg constructor + field.set qua reflection.
 * Tên header khớp chính xác với BookingExcelExporter.HEADERS[].
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BookingExcelRow {

    @ExcelColumn(header = "Booking Code", order = 0)
    private String bookingCode;

    @ExcelColumn(header = "User Email", order = 1)
    private String userEmail;

    @ExcelColumn(header = "Tour Name", order = 2)
    private String tourName;

    @ExcelColumn(header = "Participants", order = 3)
    private Integer participants;

    @ExcelColumn(header = "Total Price (VND)", order = 4)
    private BigDecimal totalPrice;

    @ExcelColumn(header = "Status", order = 5)
    private String status;

    @ExcelColumn(header = "Departure Date", order = 6, dateFormat = "dd/MM/yyyy")
    private LocalDate departureDate;

    @ExcelColumn(header = "Created Date", order = 7, dateFormat = "dd/MM/yyyy HH:mm")
    private LocalDateTime createdAt;
}
