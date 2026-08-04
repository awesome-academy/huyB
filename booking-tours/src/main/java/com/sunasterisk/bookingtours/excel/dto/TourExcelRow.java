package com.sunasterisk.bookingtours.excel.dto;

import com.sunasterisk.bookingtours.excel.annotation.ExcelColumn;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO mutable chứa một dòng tour để nhập từ Excel.
 * Phải là class (không dùng record) — ExcelMapper dùng no-arg constructor + field.set qua reflection.
 * Tên header khớp chính xác với ExcelImportServiceImpl.TEMPLATE_HEADERS[].
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TourExcelRow {

    @ExcelColumn(header = "Title", order = 0, required = true)
    private String title;

    @ExcelColumn(header = "Description", order = 1)
    private String description;

    @ExcelColumn(header = "Price", order = 2, required = true)
    private BigDecimal price;

    @ExcelColumn(header = "Duration Days", order = 3, required = true)
    private Integer durationDays;

    @ExcelColumn(header = "Max Participants", order = 4, required = true)
    private Integer maxParticipants;

    @ExcelColumn(header = "Departure Location", order = 5, required = true)
    private String departureLocation;

    @ExcelColumn(header = "Destination", order = 6, required = true)
    private String destination;

    @ExcelColumn(header = "Departure Date (yyyy-MM-dd)", order = 7, required = true, dateFormat = "yyyy-MM-dd")
    private LocalDate departureDate;

    @ExcelColumn(header = "Category Name", order = 8)
    private String categoryName;
}
