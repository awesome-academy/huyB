---
feature_id: F003
title: Swagger / OpenAPI 3.0 Setup
lang: vi
status: complete
created: 2026-07-27
completed: 2026-07-27
completed_at: b129e8f
---

# F003 — Swagger / OpenAPI 3.0

## Mục tiêu

Tích hợp Springdoc OpenAPI 3.0 để tự động sinh tài liệu API từ controller annotations,
truy cập qua `http://localhost:8080/swagger-ui.html`.

## Phạm vi

### Thêm mới
- `pom.xml` — thêm `springdoc-openapi-starter-webmvc-ui:2.6.0`
- `src/main/java/.../config/SwaggerConfig.java` — OpenAPI bean + JWT security scheme
- Annotation `@Tag` trên 14 controllers; `@Operation` trên các endpoint chính

### Thay đổi
- `SecurityConfig.java` — permitAll `/swagger-ui/**`, `/v3/api-docs/**`; cập nhật CSP cho Swagger UI
- `application-dev.properties` — bật springdoc, tắt trong prod
- `application-prod.properties` — tắt springdoc

## Chi tiết kỹ thuật

### SwaggerConfig.java
```java
@Bean
OpenAPI openAPI() {
    return new OpenAPI()
        .info(new Info()
            .title("SUN Booking Tours API")
            .description("REST API documentation — SUN Booking Tours mock project")
            .version("1.0.0"))
        .addSecurityItem(new SecurityRequirement().addList("JWT Cookie"))
        .components(new Components()
            .addSecuritySchemes("JWT Cookie",
                new SecurityScheme()
                    .type(SecurityScheme.Type.APIKEY)
                    .in(SecurityScheme.In.COOKIE)
                    .name("JWT_TOKEN")
                    .description("HttpOnly JWT cookie tự động gửi kèm request")));
}
```

### SecurityConfig — permitAll + CSP
- Thêm vào `requestMatchers(...).permitAll()`:
  `/swagger-ui/**`, `/swagger-ui.html`, `/v3/api-docs/**`, `/v3/api-docs`
- CSP: thêm `'unsafe-eval'` vào `script-src` (Swagger UI dùng eval() cho JS renderer)
  và `cdn.jsdelivr.net` cho swagger-ui assets

### application-dev.properties
```properties
springdoc.swagger-ui.enabled=true
springdoc.api-docs.enabled=true
springdoc.swagger-ui.path=/swagger-ui.html
```

### application-prod.properties
```properties
springdoc.swagger-ui.enabled=false
springdoc.api-docs.enabled=false
```

### Controller annotations
- `@Tag(name="...", description="...")` ở class level — 14 controllers
- `@Operation(summary="...")` ở method level — ít nhất 1 method/controller
- `@ApiResponse` chỉ cho endpoints quan trọng (không bắt buộc toàn bộ)

## Điều kiện chấp nhận
- `http://localhost:8080/swagger-ui.html` hiển thị Swagger UI đầy đủ
- Tất cả 14 controllers hiện trong danh sách group theo tag
- SecurityScheme "JWT Cookie" hiển thị trong Authorize dialog
- Swagger UI bị tắt hoàn toàn ở prod (404)
