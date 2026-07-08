package com.sunasterisk.bookingtours.service.impl;

import com.sunasterisk.bookingtours.dto.BookingRequest;
import com.sunasterisk.bookingtours.entity.*;
import com.sunasterisk.bookingtours.exception.ResourceNotFoundException;
import com.sunasterisk.bookingtours.repository.BookingRepository;
import com.sunasterisk.bookingtours.repository.PaymentRepository;
import com.sunasterisk.bookingtours.repository.TourRepository;
import com.sunasterisk.bookingtours.repository.UserRepository;
import com.sunasterisk.bookingtours.service.BookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Triển khai {@link BookingService}.
 */
@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int MAX_RETRY = 10;

    private final BookingRepository bookingRepository;
    private final TourRepository tourRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public long countByStatus(BookingStatus status) {
        return bookingRepository.countByStatus(status);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public BigDecimal sumTotalPriceByStatus(BookingStatus status) {
        return bookingRepository.sumTotalPriceByStatus(status);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Luồng xử lý:
     * <ol>
     *   <li>Lấy User theo email từ SecurityContext.</li>
     *   <li>Lấy Tour theo id — chỉ cho phép đặt tour ACTIVE.</li>
     *   <li>Validate số người ≤ maxParticipants.</li>
     *   <li>Tính totalPrice = price × participants.</li>
     *   <li>Generate booking_code duy nhất.</li>
     *   <li>Lưu Booking với status = PENDING.</li>
     * </ol>
     */
    @Override
    @Transactional
    public Booking createBooking(String email, BookingRequest request) {
        // 1. Lấy user
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));

        // 2. Lấy tour (chỉ ACTIVE mới cho đặt)
        Tour tour = tourRepository.findByIdAndStatus(request.getTourId(), TourStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Tour", request.getTourId()));

        // 3. Validate số người
        int participants = request.getParticipants();
        if (participants > tour.getMaxParticipants()) {
            throw new IllegalArgumentException(
                    "Number of participants (" + participants +
                            ") exceeds max allowed (" + tour.getMaxParticipants() + ")");
        }

        // 4. Tính tổng tiền
        BigDecimal totalPrice = tour.getPrice().multiply(BigDecimal.valueOf(participants));

        // 5. Generate booking code
        String bookingCode = generateBookingCode();

        // 6. Tạo và lưu booking
        Booking booking = Booking.builder()
                .bookingCode(bookingCode)
                .user(user)
                .tour(tour)
                .participants(participants)
                .totalPrice(totalPrice)
                .status(BookingStatus.PENDING)
                .note(request.getNote() != null && !request.getNote().isBlank()
                        ? request.getNote().trim() : null)
                .build();

        return bookingRepository.save(booking);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Format: {@code BK-YYYYMMDD-XXXX} trong đó XXXX là 4 chữ số ngẫu nhiên (0000–9999).
     * Retry tối đa {@value MAX_RETRY} lần nếu trùng với mã đã tồn tại trong DB.
     *
     * @throws IllegalStateException nếu không thể generate mã duy nhất sau MAX_RETRY lần thử
     */
    @Override
    public String generateBookingCode() {
        String date = LocalDate.now().format(DATE_FMT);
        for (int i = 0; i < MAX_RETRY; i++) {
            int suffix = ThreadLocalRandom.current().nextInt(0, 10_000);
            String code = String.format("BK-%s-%04d", date, suffix);
            if (!bookingRepository.existsByBookingCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Unable to generate unique booking code after " + MAX_RETRY + " attempts");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public Page<Booking> getBookingHistory(String email, BookingStatus status, Pageable pageable) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
        return bookingRepository.findByUserIdAndStatus(user.getId(), status, pageable);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Page<Booking> search(String keyword, BookingStatus status, LocalDate fromDate, LocalDate toDate, Pageable pageable) {
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        return bookingRepository.searchByKeywordAndStatusAndDepartureDate(kw, status, fromDate, toDate, pageable);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public Booking getBookingDetail(String email, Long bookingId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
        Booking booking = bookingRepository.findByIdWithTourAndUser(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));
        if (!booking.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("You do not have permission to view this booking.");
        }
        return booking;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void cancelBooking(String email, Long bookingId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));
        if (!booking.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("You do not have permission to cancel this booking.");
        }
        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new IllegalStateException(
                    "Only PENDING bookings can be cancelled. Current status: " + booking.getStatus());
        }
        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void adminConfirmBooking(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));

        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new IllegalStateException(
                    "Only PENDING bookings can be confirmed. Current status: " + booking.getStatus());
        }
        booking.setStatus(BookingStatus.CONFIRMED);
        bookingRepository.save(booking);

        // Cập nhật payment status → CONFIRMED nếu tồn tại
        paymentRepository.findByBookingId(bookingId).ifPresent(payment -> {
            payment.setStatus(PaymentStatus.CONFIRMED);
            paymentRepository.save(payment);
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void adminCancelBooking(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));

        if (booking.getStatus() == BookingStatus.CANCELLED
                || booking.getStatus() == BookingStatus.COMPLETED) {
            throw new IllegalStateException(
                    "Cannot cancel a booking with status: " + booking.getStatus());
        }
        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);

        // Cập nhật payment status → FAILED nếu tồn tại
        paymentRepository.findByBookingId(bookingId).ifPresent(payment -> {
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public Booking getBookingById(Long bookingId) {
        return bookingRepository.findByIdWithTourAndUser(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public Booking getBookingByCodeForUser(String email, String bookingCode) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
        return bookingRepository.findByBookingCodeAndUserId(bookingCode, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingCode));
    }
}
