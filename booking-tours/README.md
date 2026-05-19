# 🌏 SUN Booking Tours

> Mock project · 2 tuần · Spring Boot + Thymeleaf + PostgreSQL

[![Java](https://img.shields.io/badge/Java-26-orange?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.6-brightgreen?logo=springboot)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-blue?logo=postgresql)](https://www.postgresql.org/)
[![Maven](https://img.shields.io/badge/Maven-3.9-red?logo=apachemaven)](https://maven.apache.org/)

---

## 📖 Giới thiệu

**SUN Booking Tours** là mock project xây dựng trong **2 tuần**, cho phép người dùng tìm kiếm, đặt tour và thanh toán qua internet banking. Hỗ trợ đăng nhập qua **Google / Facebook / Twitter** (OAuth2). Hệ thống có 3 vai trò: **Admin**, **User** và **Guest**.

---

## ✨ Tính năng chính

| Actor | Chức năng |
|-------|-----------|
| **Guest** | Xem & tìm kiếm tour · Xem review · Đăng ký tài khoản |
| **User** | Đăng nhập (email / OAuth2) · Quản lý hồ sơ & tài khoản ngân hàng · Đặt tour · Thanh toán · Hủy booking · Viết review · Comment (nested 1 cấp) · Like · Rating tour |
| **Admin** | Quản lý user / category / tour / booking · Xác nhận thanh toán · Kiểm duyệt review · Dashboard doanh thu |

---

## 🛠️ Công nghệ

| Thành phần | Công nghệ |
|------------|-----------|
| Backend | Spring Boot 4.0.6 |
| ORM | Spring Data JPA (Hibernate) |
| Database | PostgreSQL |
| Template Engine | Thymeleaf + Bootstrap 5 |
| Security | Spring Security + OAuth2 Client (Google/Facebook/Twitter) |
| Build Tool | Maven |
| Utility | Lombok |

---

## 🚀 Cài đặt & Chạy

### Yêu cầu
- Java 21+ · Maven 3.9+ · PostgreSQL 15+

### 1. Clone

```bash
git clone https://github.com/your-org/booking-tours.git
cd booking-tours
```

### 2. Tạo database

```sql
CREATE DATABASE booking_tours;
CREATE USER booking_user WITH PASSWORD 'your_password';
GRANT ALL PRIVILEGES ON DATABASE booking_tours TO booking_user;
```

### 3. Cấu hình `application.properties`

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/booking_tours
spring.datasource.username=booking_user
spring.datasource.password=your_password
spring.jpa.hibernate.ddl-auto=validate
spring.sql.init.mode=always

# OAuth2 - Google
spring.security.oauth2.client.registration.google.client-id=YOUR_CLIENT_ID
spring.security.oauth2.client.registration.google.client-secret=YOUR_CLIENT_SECRET
```

### 4. Chạy

```bash
./mvnw spring-boot:run
```

Truy cập: [http://localhost:8080](http://localhost:8080)

---

## 📁 Cấu trúc project

```
src/main/java/com/sunasterisk/bookingtours/
├── config/           # SecurityConfig, OAuth2 config
├── controller/
│   ├── admin/        # Admin controllers
│   └── user/         # User/Guest controllers
├── service/          # Business logic
├── repository/       # Spring Data JPA
├── entity/           # 12 JPA entities
├── dto/              # Request / Response DTOs
└── exception/        # GlobalExceptionHandler
src/main/resources/
├── templates/        # Thymeleaf (layout/, admin/, user/)
├── static/           # CSS, JS
└── schema.sql        # DDL khởi tạo 12 bảng
```

---

## 📚 Tài liệu

| | File |
|-|------|
| 📋 Yêu cầu dự án | [docs/project-requirement.md](docs/project-requirement.md) |
| 🗄️ Thiết kế database | [docs/database-design.md](docs/database-design.md) |
| 📅 Task breakdown | [docs/task-breakdown.md](docs/task-breakdown.md) |

---

## 🗓️ Kế hoạch 2 tuần

| Tuần | Nội dung |
|------|----------|
| **Week 1** (Day 1–5) | Project setup · Auth + OAuth2 · Profile · Bank account · Admin: User & Category · Tour management & listing |
| **Week 2** (Day 6–10) | Booking · Payment · Review CRUD · Comment (nested) · Like · Rating · Admin Dashboard · Polish |

