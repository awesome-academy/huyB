# 📋 Project Requirements — SUN Booking Tours

> Mock project · **2 tuần** · Spring Boot + Spring Data JPA + Thymeleaf + PostgreSQL

---

## 1. Tổng quan

**SUN Booking Tours** là ứng dụng web đặt tour du lịch trực tuyến.  
Người dùng có thể tìm kiếm, đặt tour và thanh toán qua internet banking.  
Admin quản lý toàn bộ nội dung và doanh thu hệ thống.

---

## 2. Actors

| Actor | Mô tả |
|-------|-------|
| **Guest** | Khách vãng lai, chưa đăng nhập |
| **User** | Người dùng đã đăng ký & đăng nhập |
| **Admin** | Quản trị viên hệ thống |

---

## 3. Yêu cầu chức năng

### 3.1 Guest

| ID | Chức năng | Ghi chú |
|----|-----------|---------|
| G-01 | Xem danh sách tour | Lọc theo danh mục, giá |
| G-02 | Xem chi tiết tour | Mô tả, giá, ngày khởi hành, rating |
| G-03 | Tìm kiếm tour | Theo tên, địa điểm |
| G-04 | Xem bài review | Lọc theo loại: Địa điểm / Ẩm thực / Tin tức |
| G-05 | Đăng ký tài khoản | Form email + password |

### 3.2 User

| ID | Chức năng | Ghi chú |
|----|-----------|---------|
| U-01 | Đăng nhập / Đăng xuất | Email+password hoặc OAuth2 |
| U-02 | Đăng nhập qua mạng xã hội | Google · Facebook · Twitter (OAuth2) |
| U-03 | Quản lý hồ sơ | Cập nhật tên, phone, avatar |
| U-04 | Quản lý tài khoản ngân hàng | CRUD tài khoản ngân hàng cá nhân |
| U-05 | Xem & tìm kiếm tour | Như Guest |
| U-06 | Đặt tour | Chọn số người, ngày — tạo Booking PENDING |
| U-07 | Thanh toán tour | Chuyển khoản Internet Banking + nhập mã giao dịch |
| U-08 | Hủy tour | Hủy booking chưa CONFIRMED |
| U-09 | Xem lịch sử booking | Danh sách + chi tiết trạng thái |
| U-10 | Xem review | Như Guest |
| U-11 | Viết / Sửa / Xóa review | CRUD review của chính mình |
| U-12 | Comment review | Bình luận bài review |
| U-13 | Reply comment | Trả lời bình luận (1 cấp lồng) |
| U-14 | Like review | Toggle like / unlike |
| U-15 | Rating tour | Đánh giá 1–5 sao (sau khi tour hoàn thành) |

### 3.3 Admin

| ID | Chức năng | Ghi chú |
|----|-----------|---------|
| A-01 | Quản lý người dùng | Xem, tìm kiếm, khoá / mở khoá |
| A-02 | Quản lý danh mục | CRUD category |
| A-03 | Quản lý tour | CRUD tour + upload thumbnail |
| A-04 | Quản lý booking | Xem, Confirm / Cancel |
| A-05 | Quản lý review | Ẩn / Xóa review vi phạm |
| A-06 | Quản lý doanh thu | Dashboard: tổng booking, doanh thu tháng |

---

## 4. Giới hạn scope (2 tuần)

| Tính năng | Quyết định |
|-----------|------------|
| OAuth2 | Implement Google; Facebook & Twitter cùng pattern, bổ sung nếu còn thời gian |
| Nested comment | Tối đa **1 cấp** (reply của reply không cho phép) |
| Thanh toán | Mock flow: hiển thị thông tin chuyển khoản → user nhập mã GD → Admin confirm |
| Email notification | ❌ Ngoài scope |
| Ảnh gallery tour | ❌ Chỉ 1 thumbnail |

---

## 5. Công nghệ

| Thành phần | Công nghệ |
|------------|-----------|
| Backend | Spring Boot 4.x |
| ORM | Spring Data JPA (Hibernate) |
| Database | PostgreSQL |
| Template Engine | Thymeleaf + Bootstrap 5 |
| Security | Spring Security + OAuth2 Client |
| Build Tool | Maven |
| Utility | Lombok |

