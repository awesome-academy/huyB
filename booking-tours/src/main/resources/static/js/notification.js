/**
 * notification.js — WebSocket/STOMP client cho real-time notifications.
 * Kết nối qua SockJS + StompJS, subscribe /user/queue/notifications.
 * Khi nhận message: tăng badge, hiện toast Bootstrap 5.
 */

(function () {
    'use strict';

    const BADGE_EL = document.getElementById('notif-count');
    const TOAST_CONTAINER = document.getElementById('toast-container');

    let badgeCount = 0;

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
        // Đọc CSRF token từ cookie XSRF-TOKEN (CookieCsrfTokenRepository.withHttpOnlyFalse)
        const csrfToken = getCookie('XSRF-TOKEN');
        const headers = csrfToken ? { 'X-XSRF-TOKEN': csrfToken } : {};

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

    function getCookie(name) {
        const match = document.cookie.match(new RegExp('(?:^|; )' + name + '=([^;]*)'));
        return match ? decodeURIComponent(match[1]) : null;
    }

    // Khởi tạo sau khi DOM sẵn sàng
    document.addEventListener('DOMContentLoaded', function () {
        fetchUnreadCount();

        const bellBtn = document.getElementById('notif-bell');
        if (bellBtn) {
            // Prevent immediate navigation — POST mark-read first, then navigate.
            // Without preventDefault the fetch is aborted when the page unloads.
            bellBtn.addEventListener('click', function (e) {
                e.preventDefault();
                const href = bellBtn.getAttribute('href') || '/profile/notifications';
                fetch('/api/notifications/mark-read', {
                    method: 'POST',
                    credentials: 'same-origin',
                    headers: { 'X-XSRF-TOKEN': getCookie('XSRF-TOKEN') || '' }
                }).finally(() => { window.location.href = href; });
            });
        }

        // Chỉ kết nối WebSocket khi user đã đăng nhập (badge element tồn tại)
        if (BADGE_EL !== null) {
            connectWebSocket();
        }
    });
})();
