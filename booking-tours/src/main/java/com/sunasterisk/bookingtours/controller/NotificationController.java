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
        User user = userService.getByEmail(authentication.getName());
        Page<NotificationDto> notifications =
                notificationService.getNotifications(user.getId(), PageRequest.of(page, size));
        return ResponseEntity.ok(notifications);
    }

    @Operation(summary = "Số thông báo chưa đọc")
    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(Authentication authentication) {
        User user = userService.getByEmail(authentication.getName());
        long count = notificationService.getUnreadCount(user.getId());
        return ResponseEntity.ok(Map.of("count", count));
    }

    @Operation(summary = "Đánh dấu tất cả thông báo đã đọc")
    @PostMapping("/mark-read")
    public ResponseEntity<Void> markAllRead(Authentication authentication) {
        User user = userService.getByEmail(authentication.getName());
        notificationService.markAllRead(user.getId());
        return ResponseEntity.ok().build();
    }
}
