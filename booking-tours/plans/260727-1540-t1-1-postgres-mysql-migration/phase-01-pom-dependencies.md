---
phase: 01
title: pom.xml — Đổi driver & Flyway dependency
status: pending
---

## Thay đổi

**Xóa:**
```xml
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-database-postgresql</artifactId>
</dependency>
```

**Thêm:**
```xml
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-mysql</artifactId>
</dependency>
```

## Todo
- [ ] Xóa postgresql dependency
- [ ] Xóa flyway-database-postgresql dependency
- [ ] Thêm mysql-connector-j dependency
- [ ] Thêm flyway-mysql dependency
- [ ] Verify `mvn dependency:resolve` không lỗi
