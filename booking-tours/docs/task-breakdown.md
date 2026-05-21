# Task Breakdown — SUN Booking Tours

> **Mock project · 2 tuần · 10 ngày làm việc**  
> Mỗi task ≤ 8h · Tổng ≈ 80h

---

## WEEK 1 — Foundation, Auth, Tour & Category

### Day 1 — Project Setup (8h)

| # | Task | Giờ |
|---|------|-----|
| 1.1 | Khởi tạo Spring Boot project: Web, JPA, Security, OAuth2 Client, Thymeleaf, PostgreSQL, Lombok | 2h |
| 1.2 | Cấu hình PostgreSQL + tạo file `schema.sql` (12 bảng) + seed data roles | 3h |
| 1.3 | Setup cấu trúc package, `BaseEntity`, `GlobalExceptionHandler`, base layout Thymeleaf + Bootstrap 5 | 3h |

### Day 2 — Authentication (8h)

| # | Task | Giờ |
|---|------|-----|
| 2.1 | Entity `User`, `Role` — JPA mapping, `UserRepository`, `UserDetailsService` | 2h |
| 2.2 | Đăng ký: form, validate (email unique, password confirm), BCrypt, lưu DB | 3h |
| 2.3 | Đăng nhập / Đăng xuất: Spring Security form login, redirect theo role (ADMIN/USER) | 3h |

### Day 3 — OAuth2 + User Profile (8h)

| # | Task | Giờ |
|---|------|-----|
| 3.1 | Entity `OAuthAccount` — OAuth2 login Google (custom `OAuth2UserService`: tìm / tạo user, link `oauth_accounts`) | 5h |
| 3.2 | Quản lý hồ sơ: xem & cập nhật `full_name`, `phone`, `avatar_url` | 3h |

### Day 4 — Bank Account + Admin: User & Category (8h)

| # | Task | Giờ |
|---|------|-----|
| 4.1 | Entity `UserBankAccount` — CRUD tài khoản ngân hàng cá nhân (set default) | 3h |
| 4.2 | Admin — Danh sách user (phân trang, search), khoá / mở khoá tài khoản | 2h |
| 4.3 | Entity `Category` — Admin CRUD category | 3h |

### Day 5 — Tour Management & Listing (8h)

| # | Task | Giờ |
|---|------|-----|
| 5.1 | Entity `Tour` — Admin CRUD tour (form, upload thumbnail, chọn category, status) | 4h |
| 5.2 | Guest/User — Danh sách tour: phân trang, lọc category, hiển thị `avg_rating` | 2h |
| 5.3 | Guest/User — Chi tiết tour + Tìm kiếm tour (theo tên / địa điểm) | 2h |

---

## WEEK 2 — Booking, Payment, Review, Rating & Admin Dashboard

### Day 6 — Booking (8h)

| # | Task | Giờ |
|---|------|-----|
| 6.1 | Entity `Booking` — JPA mapping, enum `BookingStatus` | 1h |
| 6.2 | Đặt tour: form (số người, auto-tính tổng tiền), tạo Booking PENDING, generate `booking_code` | 4h |
| 6.3 | Lịch sử booking (User): danh sách, filter status, chi tiết; Hủy booking PENDING | 3h |

### Day 7 — Payment + Admin Booking (8h)

| # | Task | Giờ |
|---|------|-----|
| 7.1 | Entity `Payment` — JPA mapping, enum `PaymentStatus` | 1h |
| 7.2 | Thanh toán: chọn tài khoản ngân hàng, hiển thị thông tin chuyển khoản, nhập `transaction_code`, tạo Payment PENDING | 4h |
| 7.3 | Admin — Danh sách booking (filter status), Confirm payment → Booking CONFIRMED, Cancel → CANCELLED | 3h |

### Day 8 — Review System (8h)

| # | Task | Giờ |
|---|------|-----|
| 8.1 | Entity `Review` — JPA mapping, enum `ReviewType`, `ReviewStatus` | 1h |
| 8.2 | Guest/User — Danh sách review (lọc PLACE / FOOD / NEWS, phân trang), chi tiết review | 3h |
| 8.3 | User — Viết / Sửa / Xóa review | 2h |
| 8.4 | Admin — Ẩn / Xóa review vi phạm | 2h |

### Day 9 — Comment, Like & Rating (8h)

| # | Task | Giờ |
|---|------|-----|
| 9.1 | Entity `Comment` — JPA mapping (`parent_id` self-ref); hiển thị danh sách comments + replies | 2h |
| 9.2 | User — Thêm comment vào review; Reply comment (1 cấp) | 3h |
| 9.3 | Entity `Like` — Like / Unlike review (AJAX toggle, cập nhật `likes_count`) | 1h |
| 9.4 | Entity `Rating` — Rating tour 1–5 sao; cập nhật `avg_rating` trên `tours` | 2h |

### Day 10 — Admin Dashboard + Polish (8h)

| # | Task | Giờ |
|---|------|-----|
| 10.1 | Admin Dashboard: tổng số user, tour, booking hôm nay, doanh thu tháng hiện tại | 3h |
| 10.2 | Fix bug, server-side validation toàn app, trang lỗi 403 / 404 | 3h |
| 10.3 | Review UI/UX, kiểm thử luồng chính (đặt tour → thanh toán → admin confirm) | 2h |

---

## Tổng kết

| | Nội dung |
|-|----------|
| **Week 1** | Setup · Auth · OAuth2 · Profile · Bank Account · Category · Tour |
| **Week 2** | Booking · Payment · Review · Comment · Like · Rating · Admin Dashboard |
| **Tổng** | 10 ngày × 8h = **~80h** |

---

## Luồng nghiệp vụ chính

```
[Guest]   Xem tour ──► Đăng ký ──► Đăng nhập (email hoặc Google)
[User]    Tìm tour ──► Đặt tour (PENDING) ──► Chọn tk ngân hàng ──► Nhập mã GD (Payment PENDING)
[Admin]   Xem booking ──► Confirm ──► Booking CONFIRMED
[User]    Xem lịch sử ──► Rating tour ──► Viết review ──► Comment / Like
[Admin]   Kiểm duyệt review ──► Ẩn nếu vi phạm
```

