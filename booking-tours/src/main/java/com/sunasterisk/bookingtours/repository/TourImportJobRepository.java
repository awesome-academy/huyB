package com.sunasterisk.bookingtours.repository;

import com.sunasterisk.bookingtours.entity.TourImportJob;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TourImportJobRepository extends JpaRepository<TourImportJob, Long> {

    /** Lấy N job gần nhất theo thứ tự tạo mới nhất — dùng cho import history UI. */
    List<TourImportJob> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
