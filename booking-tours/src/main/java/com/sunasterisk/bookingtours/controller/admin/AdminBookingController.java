package com.sunasterisk.bookingtours.controller.admin;

import com.sunasterisk.bookingtours.entity.Booking;
import com.sunasterisk.bookingtours.entity.BookingStatus;
import com.sunasterisk.bookingtours.entity.Payment;
import com.sunasterisk.bookingtours.service.BookingService;
import com.sunasterisk.bookingtours.service.ExcelExportService;
import com.sunasterisk.bookingtours.service.PaymentService;
import com.sunasterisk.bookingtours.util.PaginationUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;

/**
 * Controller quản lý bookings phía Admin
 *
 * <p>Tất cả endpoint đều yêu cầu role ADMIN (kiểm soát bởi SecurityConfig).</p>
 */
@Tag(name = "Admin - Bookings", description = "Quản lý booking của toàn hệ thống")
@Slf4j
@Controller
@RequestMapping("/admin/bookings")
@RequiredArgsConstructor
public class AdminBookingController {

    private static final int PAGE_SIZE = 15;

    private final BookingService bookingService;
    private final PaymentService paymentService;
    private final ExcelExportService excelExportService;

    /**
     * GET /admin/bookings - Danh sách booking có phân trang, filter status, từ khoá, ngày
     */
    @Operation(summary = "Danh sách booking (Admin)", description = "Phân trang, filter status/keyword/ngày")
    @GetMapping
    public String listBookings(
            @RequestParam(value = "keyword", defaultValue = "") String keyword,
            @RequestParam(value = "status", required = false) BookingStatus status,
            @RequestParam(value = "fromDate", required = false) LocalDate fromDate,
            @RequestParam(value = "toDate", required = false) LocalDate toDate,
            @RequestParam(value = "page", defaultValue = "0") int page,
            Model model) {

        Pageable pageable = PageRequest.of(page, PAGE_SIZE);
        Page<Booking> bookingPage = bookingService.search(
                keyword.isBlank() ? null : keyword, status, fromDate, toDate, pageable);
        int totalPages = bookingPage.getTotalPages();

        model.addAttribute("bookingPage", bookingPage);
        model.addAttribute("keyword", keyword);
        model.addAttribute("status", status);
        model.addAttribute("fromDate", fromDate);
        model.addAttribute("toDate", toDate);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("pageNumbers", PaginationUtils.getPageNumbers(page, totalPages));

        // Stats
        model.addAttribute("totalPending", bookingService.countByStatus(BookingStatus.PENDING));
        model.addAttribute("totalConfirmed", bookingService.countByStatus(BookingStatus.CONFIRMED));
        model.addAttribute("totalCancelled", bookingService.countByStatus(BookingStatus.CANCELLED));
        model.addAttribute("totalCompleted", bookingService.sumTotalPriceByStatus(BookingStatus.CONFIRMED));

        return "admin/bookings/list";
    }

    /**
     * GET /admin/bookings/{id} - Chi tiết booking
     */
    @Operation(summary = "Chi tiết booking (Admin)", description = "Xem booking và payment đính kèm")
    @GetMapping("/{id}")
    public String bookingDetail(@PathVariable Long id, Model model) {
        Booking booking = bookingService.getBookingById(id);
        Payment payment = paymentService.findByBookingId(id).orElse(null);
        model.addAttribute("booking", booking);
        model.addAttribute("payment", payment);
        return "admin/bookings/detail";
    }

    /**
     * POST /admin/bookings/{id}/confirm - Xác nhận thanh toán → Booking CONFIRMED
     */
    @Operation(summary = "Xác nhận booking", description = "Chuyển trạng thái booking sang CONFIRMED")
    @PostMapping("/{id}/confirm")
    public String confirmBooking(@PathVariable Long id,
                                 RedirectAttributes redirectAttributes) {
        try {
            bookingService.adminConfirmBooking(id);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Booking #" + id + " has been confirmed successfully.");
        } catch (IllegalStateException | IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Cannot confirm booking: " + e.getMessage());
        }
        return "redirect:/admin/bookings";
    }

    /**
     * GET /admin/bookings/export — Xuất toàn bộ booking (với filter hiện tại) ra file Excel.
     */
    @Operation(summary = "Export bookings (Admin)", description = "Xuất danh sách booking ra .xlsx với filter tương tự list page")
    @GetMapping("/export")
    public ResponseEntity<Resource> exportBookings(
            @RequestParam(value = "keyword", defaultValue = "") String keyword,
            @RequestParam(value = "status", required = false) BookingStatus status,
            @RequestParam(value = "fromDate", required = false) LocalDate fromDate,
            @RequestParam(value = "toDate", required = false) LocalDate toDate) throws IOException {

        XSSFWorkbook workbook = excelExportService.exportBookings(
                keyword.isBlank() ? null : keyword, status, fromDate, toDate);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        workbook.write(out);
        workbook.close();

        byte[] bytes = out.toByteArray();
        String filename = "bookings_" + LocalDate.now() + ".xlsx";

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentLength(bytes.length)
                .body(new ByteArrayResource(bytes));
    }

    /**
     * POST /admin/bookings/{id}/cancel - Admin hủy booking → CANCELLED
     */
    @Operation(summary = "Hủy booking (Admin)", description = "Admin hủy booking, chuyển sang CANCELLED")
    @PostMapping("/{id}/cancel")
    public String cancelBooking(@PathVariable Long id,
                                RedirectAttributes redirectAttributes) {
        try {
            bookingService.adminCancelBooking(id);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Booking #" + id + " has been cancelled.");
        } catch (IllegalStateException | IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Cannot cancel booking: " + e.getMessage());
        }
        return "redirect:/admin/bookings";
    }
}
