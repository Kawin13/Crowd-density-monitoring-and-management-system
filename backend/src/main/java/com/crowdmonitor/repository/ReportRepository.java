package com.crowdmonitor.repository;

import com.crowdmonitor.entity.Report;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {
    Page<Report> findAllByOrderByGeneratedAtDesc(Pageable pageable);
    Page<Report> findByCameraIdOrderByGeneratedAtDesc(Long cameraId, Pageable pageable);
}
