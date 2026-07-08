package com.sunasterisk.bookingtours.controller;

import com.sunasterisk.bookingtours.dto.PaymentRequest;
import com.sunasterisk.bookingtours.entity.Booking;
import com.sunasterisk.bookingtours.entity.BookingStatus;
import com.sunasterisk.bookingtours.entity.Payment;
import com.sunasterisk.bookingtours.entity.UserBankAccount;
import com.sunasterisk.bookingtours.service.BankAccountService;
import com.sunasterisk.bookingtours.service.BookingService;
import com.sunasterisk.bookingtours.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Optional;

/**
 * Controller xử lý luồng thanh toán (task 7.2).
 *
 * <ul>
 *   <li>GET  /bookings/{bookingId}/pay  — Trang thanh toán: chọn tài khoản ngân hàng,
 *       hiển thị thông tin chuyển khoản, nhập transaction_code</li>
 *   <li>POST /bookings/{bookingId}/pay  — Tạo Payment PENDING</li>
 * </ul>
 */
@Controller
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final BookingService bookingService;
    private final BankAccountService bankAccountService;

    // ----------------------------------------------------------------
    // GET /bookings/{bookingId}/pay — Trang thanh toán
    // ----------------------------------------------------------------

    /**
     * Hiển thị trang thanh toán.
     * Gồm: thông tin booking, danh sách tài khoản ngân hàng của user, form nhập mã GD.
     *
     * @param bookingId id booking cần thanh toán
     * @param auth      thông tin user đang đăng nhập
     * @param model     Spring MVC model
     * @return view {@code bookings/pay}
     */
    @GetMapping("/bookings/{bookingId}/pay")
    public String showPaymentForm(
            @PathVariable Long bookingId,
            Authentication auth,
            Model model,
            RedirectAttributes redirectAttrs) {

        // Lấy booking (đã validate quyền sở hữu trong service)
        Booking booking;
        try {
            booking = bookingService.getBookingDetail(auth.getName(), bookingId);
        } catch (AccessDeniedException e) {
            redirectAttrs.addFlashAttribute("errorMessage", "You do not have permission to pay for this booking.");
            return "redirect:/bookings";
        }

        // Chỉ cho phép thanh toán booking PENDING
        if (booking.getStatus() != BookingStatus.PENDING) {
            redirectAttrs.addFlashAttribute("errorMessage",
                    "Payment is only allowed for PENDING bookings.");
            return "redirect:/bookings/" + bookingId;
        }

        // Kiểm tra đã có payment chưa
        Optional<Payment> existingPayment = paymentService.findByBookingId(bookingId);
        if (existingPayment.isPresent()) {
            redirectAttrs.addFlashAttribute("infoMessage",
                    "A payment has already been submitted for this booking. Please wait for admin confirmation.");
            return "redirect:/bookings/" + bookingId;
        }

        // Danh sách tài khoản ngân hàng của user
        List<UserBankAccount> bankAccounts = bankAccountService.getAccountsByUser(auth.getName());

        model.addAttribute("booking", booking);
        model.addAttribute("bankAccounts", bankAccounts);
        model.addAttribute("paymentRequest", new PaymentRequest());
        return "bookings/pay";
    }

    // ----------------------------------------------------------------
    // POST /bookings/{bookingId}/pay — Tạo Payment PENDING
    // ----------------------------------------------------------------

    /**
     * Xử lý submit form thanh toán.
     * Validate → tạo Payment PENDING → redirect về trang chi tiết booking.
     *
     * @param bookingId      id booking
     * @param paymentRequest DTO từ form
     * @param bindingResult  kết quả Bean Validation
     * @param auth           thông tin user đang đăng nhập
     * @param model          Spring MVC model
     * @param redirectAttrs  flash attributes
     * @return redirect tới trang chi tiết booking hoặc render lại form nếu lỗi
     */
    @PostMapping("/bookings/{bookingId}/pay")
    public String submitPayment(
            @PathVariable Long bookingId,
            @Valid @ModelAttribute("paymentRequest") PaymentRequest paymentRequest,
            BindingResult bindingResult,
            Authentication auth,
            Model model,
            RedirectAttributes redirectAttrs) {

        if (bindingResult.hasErrors()) {
            // Reload data for the form
            try {
                Booking booking = bookingService.getBookingDetail(auth.getName(), bookingId);
                List<UserBankAccount> bankAccounts = bankAccountService.getAccountsByUser(auth.getName());
                model.addAttribute("booking", booking);
                model.addAttribute("bankAccounts", bankAccounts);
            } catch (Exception ignored) {
            }
            return "bookings/pay";
        }

        try {
            paymentService.createPayment(auth.getName(), bookingId, paymentRequest);
            redirectAttrs.addFlashAttribute("successMessage",
                    "Payment submitted successfully! Please wait for admin confirmation.");
            return "redirect:/bookings/" + bookingId;
        } catch (IllegalStateException e) {
            redirectAttrs.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/bookings/" + bookingId;
        } catch (AccessDeniedException e) {
            redirectAttrs.addFlashAttribute("errorMessage",
                    "You do not have permission to pay for this booking.");
            return "redirect:/bookings";
        } catch (Exception e) {
            Booking booking = bookingService.getBookingDetail(auth.getName(), bookingId);
            List<UserBankAccount> bankAccounts = bankAccountService.getAccountsByUser(auth.getName());
            model.addAttribute("booking", booking);
            model.addAttribute("bankAccounts", bankAccounts);
            model.addAttribute("errorMessage", "Payment failed. Please try again.");
            return "bookings/pay";
        }
    }
}
