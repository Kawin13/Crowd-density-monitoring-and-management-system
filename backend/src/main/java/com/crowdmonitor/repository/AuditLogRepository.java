package com.crowdmonitor.repository;

import com.crowdmonitor.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    Page<AuditLog> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    Page<AuditLog> findByEntityTypeOrderByCreatedAtDesc(String entityType, Pageable pageable);
}
