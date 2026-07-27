package com.sunasterisk.bookingtours.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Danh mục tour du lịch.
 * Bảng categories không có created_at / updated_at nên không kế thừa BaseEntity.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "categories")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", length = 100, unique = true, nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
}
