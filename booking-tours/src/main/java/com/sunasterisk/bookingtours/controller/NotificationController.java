package com.sunasterisk.bookingtours.controller;

import com.sunasterisk.bookingtours.dto.NotificationDto;
import com.sunasterisk.bookingtours.entity.User;
import com.sunasterisk.bookingtours.service.NotificationService;
import com.sunasterisk.bookingtours.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST API phục vụ notification bell trên navbar.
 *
 * <p>Luồng hoạt động:
 * <ol>
 *   <li>Trang load → {@code notification.js} gọi {@code GET /unread-count} để set badge</li>
 *   <li>User mở dropdown → {@code GET /api/notifications} để load danh sách</li>
 *   <li>User click "mark all read" → {@code POST /mark-read}</li>
 * </ol>
 *
 * <p>Push realtime (WebSocket) được xử lý riêng bởi {@code NotificationServiceImpl}
 * qua {@code SimpMessagingTemplate} — controller này chỉ phục vụ REST (initial load + actions).
 */
@Tag(name = "Notifications", description = "Quản lý thông báo người dùng")
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final UserService userService;

    @Operation(summary = "Lấy danh sách thông báo có phân trang")
    @GetMapping
    public ResponseEntity<Page<NotificationDto>> getNotifications(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        // authentication.getName() trả về email vì JWT subject được set là email
        User user = userService.getByEmail(authentication.getName());
        Page<NotificationDto> notifications =
                notificationService.getNotifications(user.getId(), PageRequest.of(page, size));
        return ResponseEntity.ok(notifications);
    }

    @Operation(summary = "Số thông báo chưa đọc")
    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(Authentication authentication) {
        // Được notification.js gọi khi page load để hiển thị badge ban đầu;
        // WebSocket push sẽ tăng badge realtime sau đó mà không cần gọi lại endpoint này
        User user = userService.getByEmail(authentication.getName());
        long count = notificationService.getUnreadCount(user.getId());
        return ResponseEntity.ok(Map.of("count", count));
    }

    @Operation(summary = "Đánh dấu tất cả thông báo đã đọc")
    @PostMapping("/mark-read")
    public ResponseEntity<Void> markAllRead(Authentication authentication) {
        // POST vì thay đổi trạng thái is_read trong DB; frontend reset badge về 0 sau call này
        User user = userService.getByEmail(authentication.getName());
        notificationService.markAllRead(user.getId());
        return ResponseEntity.ok().build();
    }
}
