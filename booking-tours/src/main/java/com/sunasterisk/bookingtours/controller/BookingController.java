package com.sunasterisk.bookingtours.controller;

import com.sunasterisk.bookingtours.dto.BookingRequest;
import com.sunasterisk.bookingtours.entity.Booking;
import com.sunasterisk.bookingtours.entity.BookingStatus;
import com.sunasterisk.bookingtours.entity.Tour;
import com.sunasterisk.bookingtours.service.BookingService;
import com.sunasterisk.bookingtours.service.PaymentService;
import com.sunasterisk.bookingtours.service.TourService;
import com.sunasterisk.bookingtours.util.PaginationUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Controller xử lý luồng đặt tour của User.
 *
 * <ul>
 *   <li>GET  /bookings                       — Lịch sử booking (danh sách + filter status)</li>
 *   <li>GET  /bookings/{id}                  — Chi tiết booking</li>
 *   <li>POST /bookings/{id}/cancel           — Hủy booking PENDING</li>
 *   <li>GET  /bookings/new?tourId=X          — Form đặt tour</li>
 *   <li>POST /bookings                       — Submit form, tạo Booking PENDING</li>
 *   <li>GET  /bookings/confirmation/{code}   — Trang xác nhận sau khi đặt thành công</li>
 * </ul>
 * <p>
 * Yêu cầu xác thực (anyRequest().authenticated() trong SecurityConfig).
 */
@Controller
@RequestMapping("/bookings")
@RequiredArgsConstructor
public class BookingController {

    private static final int PAGE_SIZE = 8;

    private final BookingService bookingService;
    private final TourService tourService;
    private final PaymentService paymentService;

    // ----------------------------------------------------------------
    // GET /bookings  — Lịch sử booking (task 6.3)
    // ----------------------------------------------------------------

    /**
     * GET /bookings — Danh sách lịch sử booking của user, có lọc theo status.
     *
     * @param status trạng thái cần lọc (rỗng = tất cả)
     * @param page   trang hiện tại (0-based), mặc định 0
     * @param auth   thông tin user đang đăng nhập
     * @param model  Spring MVC model
     * @return view {@code bookings/list}
     */
    @GetMapping
    public String bookingHistory(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            Authentication auth,
            Model model) {

        BookingStatus bookingStatus = null;
        if (status != null && !status.isBlank()) {
            try {
                bookingStatus = BookingStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // invalid status → show all
            }
        }

        PageRequest pageable = PageRequest.of(page, PAGE_SIZE, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Booking> bookingPage = bookingService.getBookingHistory(auth.getName(), bookingStatus, pageable);

        model.addAttribute("bookingPage", bookingPage);
        model.addAttribute("currentPage", bookingPage.getNumber());
        model.addAttribute("totalPages", bookingPage.getTotalPages());
        model.addAttribute("pageNumbers", PaginationUtils.getPageNumbers(bookingPage.getNumber(), bookingPage.getTotalPages()));
        model.addAttribute("selectedStatus", bookingStatus != null ? bookingStatus.name() : "");
        model.addAttribute("statuses", BookingStatus.values());
        return "bookings/list";
    }

    // ----------------------------------------------------------------
    // GET /bookings/{id}  — Chi tiết booking (task 6.3)
    // ----------------------------------------------------------------

    /**
     * GET /bookings/{id} — Chi tiết một booking.
     * Chỉ user sở hữu mới được xem.
     *
     * @param id    id của booking
     * @param auth  thông tin user đang đăng nhập
     * @param model Spring MVC model
     * @return view {@code bookings/detail}
     */
    @GetMapping("/{id}")
    public String bookingDetail(
            @PathVariable Long id,
            Authentication auth,
            Model model) {

        Booking booking = bookingService.getBookingDetail(auth.getName(), id);
        model.addAttribute("booking", booking);
        // Load payment info if exists
        paymentService.findByBookingId(id).ifPresent(p -> model.addAttribute("payment", p));
        return "bookings/detail";
    }

    // ----------------------------------------------------------------
    // POST /bookings/{id}/cancel  — Hủy booking PENDING (task 6.3)
    // ----------------------------------------------------------------

    /**
     * POST /bookings/{id}/cancel — Hủy booking nếu đang ở trạng thái PENDING.
     *
     * @param id            id của booking cần hủy
     * @param auth          thông tin user đang đăng nhập
     * @param redirectAttrs flash attributes
     * @return redirect về trang chi tiết booking
     */
    @PostMapping("/{id}/cancel")
    public String cancelBooking(
            @PathVariable Long id,
            Authentication auth,
            RedirectAttributes redirectAttrs) {

        try {
            bookingService.cancelBooking(auth.getName(), id);
            redirectAttrs.addFlashAttribute("successMessage", "Your booking has been cancelled successfully.");
        } catch (IllegalStateException e) {
            redirectAttrs.addFlashAttribute("errorMessage", e.getMessage());
        } catch (AccessDeniedException e) {
            redirectAttrs.addFlashAttribute("errorMessage", "You do not have permission to cancel this booking.");
            return "redirect:/bookings";
        }
        return "redirect:/bookings/" + id;
    }

    // ----------------------------------------------------------------
    // GET /bookings/new?tourId={id}
    // ----------------------------------------------------------------

    /**
     * Hiển thị form đặt tour.
     * Pre-populate tourId và participants = 1.
     *
     * @param tourId id của tour muốn đặt
     * @param model  Spring MVC model
     * @return view {@code bookings/new}
     */
    @GetMapping("/new")
    public String showBookingForm(
            @RequestParam("tourId") Long tourId,
            Model model) {

        Tour tour = tourService.getPublicById(tourId);

        BookingRequest bookingRequest = new BookingRequest();
        bookingRequest.setTourId(tourId);
        bookingRequest.setParticipants(1);

        model.addAttribute("tour", tour);
        model.addAttribute("bookingRequest", bookingRequest);
        return "bookings/new";
    }

    // ----------------------------------------------------------------
    // POST /bookings
    // ----------------------------------------------------------------

    /**
     * Xử lý submit form: validate → tạo Booking PENDING → redirect xác nhận.
     *
     * @param authentication thông tin user đang đăng nhập
     * @param bookingRequest DTO từ form
     * @param bindingResult  kết quả Bean Validation
     * @param model          Spring MVC model
     * @param redirectAttrs  flash attributes
     * @return redirect tới trang xác nhận hoặc render lại form nếu lỗi
     */
    @PostMapping
    public String submitBooking(
            Authentication authentication,
            @Valid @ModelAttribute("bookingRequest") BookingRequest bookingRequest,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttrs) {

        if (bindingResult.hasErrors()) {
            Tour tour = tourService.getPublicById(bookingRequest.getTourId());
            model.addAttribute("tour", tour);
            return "bookings/new";
        }

        try {
            Booking booking = bookingService.createBooking(authentication.getName(), bookingRequest);
            redirectAttrs.addFlashAttribute("successMessage",
                    "Booking created successfully! Your booking code: " + booking.getBookingCode());
            return "redirect:/bookings/confirmation/" + booking.getBookingCode();
        } catch (IllegalArgumentException e) {
            Tour tour = tourService.getPublicById(bookingRequest.getTourId());
            model.addAttribute("tour", tour);
            model.addAttribute("errorMessage", e.getMessage());
            return "bookings/new";
        } catch (Exception e) {
            redirectAttrs.addFlashAttribute("errorMessage", "Booking failed. Please try again later.");
            return "redirect:/tours/" + bookingRequest.getTourId();
        }
    }

    // ----------------------------------------------------------------
    // GET /bookings/confirmation/{bookingCode}
    // ----------------------------------------------------------------

    /**
     * Trang xác nhận booking (hiển thị chi tiết sau khi đặt thành công).
     * Chỉ cho phép user xem booking của chính mình.
     *
     * @param bookingCode    mã booking vừa tạo
     * @param authentication thông tin user đang đăng nhập
     * @param model          Spring MVC model
     * @return view {@code bookings/confirmation}
     */
    @GetMapping("/confirmation/{bookingCode}")
    public String confirmationPage(
            @PathVariable String bookingCode,
            Authentication authentication,
            Model model) {

        Booking booking = bookingService.getBookingByCodeForUser(authentication.getName(), bookingCode);
        model.addAttribute("booking", booking);
        return "bookings/confirmation";
    }
}
