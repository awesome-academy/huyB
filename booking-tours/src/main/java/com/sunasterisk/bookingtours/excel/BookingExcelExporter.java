package com.sunasterisk.bookingtours.excel;

import com.sunasterisk.bookingtours.entity.Booking;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Tạo file Excel (.xlsx) từ danh sách booking.
 * Header row: bold, nền xanh nhạt, viền; rows xen kẽ trắng / xám nhạt.
 */
@Component
public class BookingExcelExporter {

    private static final String[] HEADERS = {
            "Booking Code", "User Email", "Tour Name",
            "Participants", "Total Price (VND)", "Status",
            "Departure Date", "Created Date"
    };

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /** Tạo workbook với sheet "Bookings" từ danh sách booking. */
    public XSSFWorkbook export(List<Booking> bookings) {
        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet sheet = workbook.createSheet("Bookings");

        CellStyle headerStyle = buildHeaderStyle(workbook);
        CellStyle evenStyle = buildRowStyle(workbook, new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xFF});
        CellStyle oddStyle = buildRowStyle(workbook, new byte[]{(byte) 0xF5, (byte) 0xF5, (byte) 0xF5});

        // Header row
        Row header = sheet.createRow(0);
        for (int i = 0; i < HEADERS.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(HEADERS[i]);
            cell.setCellStyle(headerStyle);
        }

        // Data rows
        int rowIdx = 1;
        for (Booking b : bookings) {
            Row row = sheet.createRow(rowIdx);
            CellStyle style = (rowIdx % 2 == 0) ? evenStyle : oddStyle;

            setCell(row, 0, b.getBookingCode(), style);
            setCell(row, 1, b.getUser() != null ? b.getUser().getEmail() : "", style);
            setCell(row, 2, b.getTour() != null ? b.getTour().getTitle() : "", style);
            setCell(row, 3, b.getParticipants() != null ? String.valueOf(b.getParticipants()) : "", style);
            setCell(row, 4, b.getTotalPrice() != null ? b.getTotalPrice().toPlainString() : "", style);
            setCell(row, 5, b.getStatus() != null ? b.getStatus().name() : "", style);
            setCell(row, 6,
                    b.getTour() != null && b.getTour().getDepartureDate() != null
                            ? b.getTour().getDepartureDate().format(DATE_FMT) : "", style);
            setCell(row, 7,
                    b.getCreatedAt() != null ? b.getCreatedAt().format(DATETIME_FMT) : "", style);
            rowIdx++;
        }

        // Auto-size all columns
        for (int i = 0; i < HEADERS.length; i++) {
            sheet.autoSizeColumn(i);
        }

        return workbook;
    }

    private void setCell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value != null ? value : "");
        cell.setCellStyle(style);
    }

    private CellStyle buildHeaderStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        // Nền xanh nhạt (#BDD7EE)
        style.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 0xBD, (byte) 0xD7, (byte) 0xEE}, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        // Viền
        setBorders(style, BorderStyle.THIN);
        // Font đậm
        XSSFFont font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private CellStyle buildRowStyle(XSSFWorkbook wb, byte[] rgb) {
        XSSFCellStyle style = wb.createCellStyle();
        style.setFillForegroundColor(new XSSFColor(rgb, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        setBorders(style, BorderStyle.THIN);
        return style;
    }

    private void setBorders(XSSFCellStyle style, BorderStyle border) {
        style.setBorderTop(border);
        style.setBorderBottom(border);
        style.setBorderLeft(border);
        style.setBorderRight(border);
    }
}
