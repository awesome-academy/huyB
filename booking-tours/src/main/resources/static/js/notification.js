/**
 * notification.js — WebSocket/STOMP client cho real-time notifications.
 * Kết nối qua SockJS + StompJS, subscribe /user/queue/notifications.
 * Khi nhận message: tăng badge, hiện toast Bootstrap 5.
 *
 * CSRF: Spring Security 7 dùng XorCsrfChannelInterceptor cho STOMP CONNECT.
 * Token phải là giá trị XOR-masked từ <meta name="_csrf">, KHÔNG phải raw cookie.
 * Raw cookie (XSRF-TOKEN) là UUID — không pass được XOR Base64 decode validation.
 */

(function () {
    'use strict';

    const BADGE_EL = document.getElementById('notif-count');
    const TOAST_CONTAINER = document.getElementById('toast-container');

    let badgeCount = 0;

    // Đọc XOR-masked CSRF token từ meta tag (set bởi Thymeleaf từ _csrf request attribute).
    // KHÔNG dùng getCookie('XSRF-TOKEN') vì Spring Security 7 XorCsrfChannelInterceptor
    // yêu cầu token phải là Base64(randomBytes || XOR(random, rawToken)).
    function getCsrfToken() {
        return document.querySelector('meta[name="_csrf"]')?.getAttribute('content') || '';
    }

    function getCsrfHeaderName() {
        return document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content') || 'X-XSRF-TOKEN';
    }

    function updateBadge(count) {
        badgeCount = Math.max(0, count);
        if (!BADGE_EL) return;
        if (badgeCount > 0) {
            BADGE_EL.textContent = badgeCount > 99 ? '99+' : badgeCount;
            BADGE_EL.classList.remove('d-none');
        } else {
            BADGE_EL.classList.add('d-none');
        }
    }

    function showToast(notification) {
        if (!TOAST_CONTAINER) return;

        const toastEl = document.createElement('div');
        toastEl.className = 'toast align-items-center text-bg-primary border-0';
        toastEl.setAttribute('role', 'alert');
        toastEl.setAttribute('aria-live', 'assertive');
        toastEl.setAttribute('aria-atomic', 'true');
        toastEl.innerHTML = `
            <div class="d-flex">
                <div class="toast-body">
                    <strong><i class="bi bi-bell-fill me-1"></i>${escapeHtml(notification.title)}</strong>
                    <div class="small mt-1">${escapeHtml(notification.message || '')}</div>
                </div>
                <button type="button" class="btn-close btn-close-white me-2 m-auto"
                        data-bs-dismiss="toast" aria-label="Close"></button>
            </div>`;

        TOAST_CONTAINER.appendChild(toastEl);
        const toast = new bootstrap.Toast(toastEl, { delay: 5000 });
        toast.show();
        toastEl.addEventListener('hidden.bs.toast', () => toastEl.remove());
    }

    function escapeHtml(text) {
        const div = document.createElement('div');
        div.appendChild(document.createTextNode(text));
        return div.innerHTML;
    }

    function fetchUnreadCount() {
        fetch('/api/notifications/unread-count', { credentials: 'same-origin' })
            .then(res => res.ok ? res.json() : { count: 0 })
            .then(data => updateBadge(data.count))
            .catch(() => {});
    }

    function connectWebSocket() {
        // Gửi XOR-masked CSRF token (từ meta tag) trong STOMP CONNECT header.
        // XorCsrfChannelInterceptor decode Base64 → XOR → so sánh với raw token trong session.
        const csrfToken = getCsrfToken();
        const headers = csrfToken ? { [getCsrfHeaderName()]: csrfToken } : {};

        const socket = new SockJS('/ws');
        const stompClient = Stomp.over(socket);
        stompClient.debug = null; // tắt log STOMP trong console

        stompClient.connect(headers, function () {
            stompClient.subscribe('/user/queue/notifications', function (frame) {
                const notification = JSON.parse(frame.body);
                updateBadge(badgeCount + 1);
                showToast(notification);
            });
        }, function (error) {
            // reconnect sau 5s nếu mất kết nối
            setTimeout(connectWebSocket, 5000);
        });
    }

    // Khởi tạo sau khi DOM sẵn sàng
    document.addEventListener('DOMContentLoaded', function () {
        fetchUnreadCount();

        // Bell chỉ navigate đến trang notifications — không auto mark-read.
        // User phải nhấn "Mark all read" trên trang để đánh dấu đã đọc.

        // Chỉ kết nối WebSocket khi user đã đăng nhập (badge element tồn tại)
        if (BADGE_EL !== null) {
            connectWebSocket();
        }
    });
})();
