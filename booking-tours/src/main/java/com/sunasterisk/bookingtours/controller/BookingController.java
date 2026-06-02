package com.sunasterisk.bookingtours.controller;

import com.sunasterisk.bookingtours.dto.BookingRequest;
import com.sunasterisk.bookingtours.entity.Booking;
import com.sunasterisk.bookingtours.entity.Tour;
import com.sunasterisk.bookingtours.entity.User;
import com.sunasterisk.bookingtours.exception.ResourceNotFoundException;
import com.sunasterisk.bookingtours.repository.BookingRepository;
import com.sunasterisk.bookingtours.service.BookingService;
import com.sunasterisk.bookingtours.service.TourService;
import com.sunasterisk.bookingtours.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
 *   <li>GET  /bookings/new?tourId=X         — Form đặt tour</li>
 *   <li>POST /bookings                       — Submit form, tạo Booking PENDING</li>
 *   <li>GET  /bookings/confirmation/{code}  — Trang xác nhận sau khi đặt thành công</li>
 * </ul>
 * <p>
 * Yêu cầu xác thực (anyRequest().authenticated() trong SecurityConfig).
 */
@Controller
@RequestMapping("/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;
    private final TourService tourService;
    private final UserService userService;
    private final BookingRepository bookingRepository;

    // ----------------------------------------------------------------
    // GET /bookings  — placeholder (task 6.3 will implement history)
    // ----------------------------------------------------------------

    /**
     * GET /bookings — Lịch sử booking của user (sẽ triển khai đầy đủ ở task 6.3).
     * Tạm thời redirect sang trang tours.
     */
    @GetMapping
    public String bookingHistory() {
        return "redirect:/tours";
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
            redirectAttrs.addFlashAttribute("errorMessage", "Booking failed: " + e.getMessage());
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

        User user = userService.getByEmail(authentication.getName());

        Booking booking = bookingRepository
                .findByBookingCodeAndUserId(bookingCode, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Booking not found: " + bookingCode));

        model.addAttribute("booking", booking);
        return "bookings/confirmation";
    }
}
