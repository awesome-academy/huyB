package com.sunasterisk.bookingtours.excel;

import com.sunasterisk.bookingtours.entity.Booking;
import com.sunasterisk.bookingtours.excel.dto.BookingExcelRow;
import lombok.RequiredArgsConstructor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Tạo file Excel (.xlsx) từ danh sách booking.
 * Ủy toàn bộ xử lý cho {@link ExcelMapper} — không có code cell hay style thủ công.
 * Output giống hệt phiên bản Day-4: sheet "Bookings", header màu #BDD7EE,
 * các dòng data xen kẽ trắng/xám nhạt, viền mỏng, cột tự co giãn.
 */
@Component
@RequiredArgsConstructor
public class BookingExcelExporter {

    private final ExcelMapper excelMapper;

    /**
     * Tạo workbook với một sheet "Bookings" từ danh sách truyền vào.
     *
     * @param bookings danh sách booking cần xuất (có thể rỗng)
     * @return XSSFWorkbook sẵn sàng để stream ra HTTP response
     */
    public XSSFWorkbook export(List<Booking> bookings) {
        List<BookingExcelRow> rows = bookings.stream()
                .map(this::toRow)
                .toList();
        return excelMapper.export(rows, BookingExcelRow.class, "Bookings");
    }

    /**
     * Map entity {@link Booking} sang {@link BookingExcelRow} phẳng, an toàn với null.
     * Giữ nguyên các null guard từ các lời gọi setCell thủ công ở phiên bản Day-4.
     */
    private BookingExcelRow toRow(Booking b) {
        BookingExcelRow row = new BookingExcelRow();
        row.setBookingCode(b.getBookingCode());
        row.setUserEmail(b.getUser() != null ? b.getUser().getEmail() : "");
        row.setTourName(b.getTour() != null ? b.getTour().getTitle() : "");
        row.setParticipants(b.getParticipants());
        row.setTotalPrice(b.getTotalPrice());
        row.setStatus(b.getStatus() != null ? b.getStatus().name() : "");
        row.setDepartureDate(
                b.getTour() != null ? b.getTour().getDepartureDate() : null);
        row.setCreatedAt(b.getCreatedAt());
        return row;
    }
}
