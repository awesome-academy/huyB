package com.sunasterisk.bookingtours.util;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chống brute-force đăng nhập: đếm số lần login thất bại theo key
 * (IP + email) trong một cửa sổ thời gian trượt.
 *
 * <p>Quá {@value #MAX_ATTEMPTS} lần thất bại trong {@link #WINDOW} → chặn
 * cho đến khi cửa sổ trôi qua. Đăng nhập thành công xóa bộ đếm.
 *
 * <p>Lưu in-memory (ConcurrentHashMap) — đủ cho single-instance deployment.
 * Nếu scale ra nhiều instance thì chuyển sang store chia sẻ (Redis...).
 */
@Service
public class LoginAttemptService {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);

    private record AttemptWindow(int failures, Instant windowStart) {
    }

    private final Map<String, AttemptWindow> attempts = new ConcurrentHashMap<>();

    /**
     * Key nên gồm cả IP và email để một IP không khoá được tài khoản
     * của người khác trên IP khác (và ngược lại không dò được nhiều
     * tài khoản từ một IP).
     */
    public static String key(String clientIp, String email) {
        return clientIp + "|" + email.toLowerCase();
    }

    /**
     * @return true nếu key này đã vượt quá số lần thất bại cho phép
     *         trong cửa sổ hiện tại
     */
    public boolean isBlocked(String key) {
        AttemptWindow window = attempts.get(key);
        if (window == null) {
            return false;
        }
        if (isExpired(window)) {
            attempts.remove(key);
            return false;
        }
        return window.failures() >= MAX_ATTEMPTS;
    }

    /**
     * Ghi nhận một lần đăng nhập thất bại.
     */
    public void recordFailure(String key) {
        attempts.merge(key, new AttemptWindow(1, Instant.now()), (existing, initial) ->
                isExpired(existing)
                        ? initial
                        : new AttemptWindow(existing.failures() + 1, existing.windowStart()));

        // Dọn các entry đã hết hạn để map không phình vô hạn
        attempts.entrySet().removeIf(e -> isExpired(e.getValue()));
    }

    /**
     * Xóa bộ đếm khi đăng nhập thành công.
     */
    public void reset(String key) {
        attempts.remove(key);
    }

    private boolean isExpired(AttemptWindow window) {
        return window.windowStart().plus(WINDOW).isBefore(Instant.now());
    }
}
