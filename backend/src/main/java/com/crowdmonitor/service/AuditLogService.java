package com.crowdmonitor.service;

import com.crowdmonitor.entity.AuditLog;
import com.crowdmonitor.entity.User;
import com.crowdmonitor.repository.AuditLogRepository;
import com.crowdmonitor.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Central place for writing to the existing audit_logs table
 * (id, user_id, action, entity_type, entity_id, details, ip_address, created_at).
 *
 * No schema changes: "module" and "description" from the audit requirements
 * are represented using the existing entity_type and details columns —
 * there are no separate module/description columns in the schema.
 *
 * Every write runs in its own REQUIRES_NEW transaction so that a logging
 * failure (or being called from a read-only transactional method) can
 * never roll back or block the primary business operation that triggered it.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String action, String entityType, Long entityId, String details) {
        try {
            AuditLog entry = new AuditLog();
            entry.setUser(resolveCurrentUser());
            entry.setAction(action);
            entry.setEntityType(entityType);
            entry.setEntityId(entityId);
            entry.setDetails(details);
            auditLogRepository.save(entry);
        } catch (Exception e) {
            // Auditing must never break the calling business operation.
            log.warn("Failed to write audit log for action '{}': {}", action, e.getMessage());
        }
    }

    /** Convenience overload for a specific acting user (e.g. right after login,
     *  before the SecurityContext may be fully populated on this thread). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logForUser(User user, String action, String entityType, Long entityId, String details) {
        try {
            AuditLog entry = new AuditLog();
            entry.setUser(user);
            entry.setAction(action);
            entry.setEntityType(entityType);
            entry.setEntityId(entityId);
            entry.setDetails(details);
            auditLogRepository.save(entry);
        } catch (Exception e) {
            log.warn("Failed to write audit log for action '{}': {}", action, e.getMessage());
        }
    }

    private User resolveCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            return null;
        }
        return userRepository.findByUsername(auth.getName()).orElse(null);
    }
}
