---
phase: 02
title: application-dev.properties + application-prod.properties
status: pending
---

## application-dev.properties — Thay đổi

**Xóa:**
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/booking_tours
spring.datasource.username=postgres
spring.datasource.password=postgres
```

**Thêm:**
```properties
spring.datasource.url=jdbc:mysql://localhost:3306/booking_tours?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
spring.datasource.username=root
spring.datasource.password=root
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect
spring.jpa.properties.hibernate.dialect.storage_engine=innodb
```

## application-prod.properties — Thay đổi

Cập nhật comment hướng dẫn biến môi trường:
- `DB_URL` → `jdbc:mysql://<host>:3306/<dbname>?useSSL=true&serverTimezone=UTC`

Thêm:
```properties
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect
spring.jpa.properties.hibernate.dialect.storage_engine=innodb
```

## Todo
- [ ] Cập nhật datasource URL/username/password trong dev
- [ ] Thêm driver-class-name và dialect trong dev
- [ ] Cập nhật comment DB_URL trong prod
- [ ] Thêm driver-class-name và dialect trong prod
