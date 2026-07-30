package com.sunasterisk.bookingtours.repository;

import com.sunasterisk.bookingtours.entity.ScheduledJobLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduledJobLogRepository extends JpaRepository<ScheduledJobLog, Long> {
}
