package com.crowdmonitor.service;

import com.crowdmonitor.dto.response.AlertResponse;
import com.crowdmonitor.entity.Alert;
import com.crowdmonitor.entity.Camera;
import com.crowdmonitor.entity.CrowdData;
import com.crowdmonitor.entity.User;
import com.crowdmonitor.exception.ResourceNotFoundException;
import com.crowdmonitor.repository.AlertRepository;
import com.crowdmonitor.repository.UserRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlertService {

    private final AlertRepository alertRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    /**
     * ALERT DEDUPLICATION
     * ------------------------------------------------------------------
     * Tracks the last crowd level that was alerted on, per camera, so that
     * consecutive frames/analysis cycles reporting the SAME level do not
     * each generate a new Alert row (which would otherwise spam the
     * alerts list, reports, and any downstream notification consumers).
     *
     * A new Alert is only created when the level actually CHANGES for a
     * given camera (e.g. HIGH -> CRITICAL), matching the required example:
     *   HIGH HIGH HIGH HIGH         -> 1 alert (HIGH)
     *   HIGH HIGH CRITICAL CRITICAL -> 2 alerts (HIGH, then CRITICAL)
     *   OVERCROWDED OVERCROWDED     -> 1 alert (OVERCROWDED)
     *
     * The stored level is reset (via resetAlertState) whenever the camera
     * service stops or a new monitoring/analysis session begins, so the
     * next reading after a restart is always treated as fresh and can
     * alert again even if it happens to match the last level seen before
     * the stop.
     */
    private final Map<Long, Alert.AlertType> lastAlertedLevel = new ConcurrentHashMap<>();

    /**
     * ALERT STABILIZATION
     * ------------------------------------------------------------------
     * Raw per-frame YOLO counts can flicker across a capacity boundary
     * (e.g. 1 person, 2 people, 1 person, 2 people...), which would
     * otherwise cause the alerted level to bounce LOW/MEDIUM/HIGH back
     * and forth every cycle. A candidate level is only committed (i.e.
     * replaces lastAlertedLevel and may create an Alert) once it has been
     * seen for STABILIZATION_MIN_CYCLES consecutive analysis cycles, OR
     * has persisted for at least STABILIZATION_MIN_DURATION_MS — whichever
     * comes first. Anything short of that is treated as a temporary
     * fluctuation and ignored.
     */
    private static final int STABILIZATION_MIN_CYCLES = 3;
    private static final long STABILIZATION_MIN_DURATION_MS = 5_000;
    private final Map<Long, PendingLevel> pendingLevel = new ConcurrentHashMap<>();

    private static final class PendingLevel {
        final Alert.AlertType level;
        int count;
        final long firstSeenMillis;
        PendingLevel(Alert.AlertType level, int count, long firstSeenMillis) {
            this.level = level;
            this.count = count;
            this.firstSeenMillis = firstSeenMillis;
        }
    }

    /**
     * Clears the remembered last-alerted level for a camera. Must be called
     * whenever camera monitoring/analysis stops or (re)starts so alert
     * deduplication doesn't bleed across sessions.
     */
    public void resetAlertState(Long cameraId) {
        lastAlertedLevel.remove(cameraId);
        pendingLevel.remove(cameraId);
    }

    @Transactional(readOnly = true)
    public List<AlertResponse> getActiveAlerts() {
        return alertRepository.findByAcknowledgedFalseOrderByCreatedAtDesc()  // fixed query method name
                .stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AlertResponse> getRecentAlerts(int limit) {
        return alertRepository.findActiveAlerts(PageRequest.of(0, limit))
                .stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    /**
     * DEFENSE-IN-DEPTH: getAllAlerts() uses the inherited
     * JpaRepository.findAll(Pageable), which has no JOIN FETCH override and
     * remains vulnerable to the same LazyInitializationException that hit
     * findActiveAlerts(). @Transactional(readOnly = true) keeps the
     * Hibernate session open through mapToResponse(), so the camera lazy
     * proxy can still be safely initialized on first access even without
     * an explicit JOIN FETCH on this specific query.
     */
    @Transactional(readOnly = true)
    public Page<AlertResponse> getAllAlerts(int page, int size) {
        return alertRepository.findAll(PageRequest.of(page, size))
                .map(this::mapToResponse);
    }

    @Transactional
    public AlertResponse acknowledgeAlert(Long alertId) {
        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new ResourceNotFoundException("Alert", alertId));

        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username).orElse(null);

        alert.setAcknowledged(true);            // fixed: was setIsAcknowledged()
        alert.setAcknowledgedBy(user);
        alert.setAcknowledgedAt(LocalDateTime.now());

        Alert saved = alertRepository.save(alert);
        auditLogService.log("ALERT_ACKNOWLEDGED", "ALERT", saved.getId(),
                "Alert acknowledged for camera '" + saved.getCamera().getCameraName() + "'");
        return mapToResponse(saved);
    }

    @Transactional
    public void acknowledgeAllAlerts() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username).orElse(null);
        LocalDateTime now = LocalDateTime.now();

        List<Alert> unacknowledged = alertRepository.findByAcknowledgedFalseOrderByCreatedAtDesc();
        unacknowledged.forEach(a -> {
            a.setAcknowledged(true);            // fixed: was setIsAcknowledged()
            a.setAcknowledgedBy(user);
            a.setAcknowledgedAt(now);
        });
        alertRepository.saveAll(unacknowledged);
        if (!unacknowledged.isEmpty()) {
            auditLogService.log("ALERT_ACKNOWLEDGED", "ALERT", null,
                    "Bulk acknowledged " + unacknowledged.size() + " alert(s)");
        }
    }

    public long countUnacknowledgedAlerts() {
        return alertRepository.countByAcknowledgedFalse();  // fixed: was countByIsAcknowledgedFalse()
    }

    @Transactional
    public Alert createAlert(Camera camera, CrowdData crowdData,
                              BigDecimal occupancy, int peopleCount) {
        Alert.AlertType alertType = determineAlertType(occupancy.doubleValue());
        Long cameraId = camera.getId();

        Alert.AlertType committedLevel = lastAlertedLevel.get(cameraId);
        if (alertType == committedLevel) {
            // Same as the already-committed level — stable, nothing to do.
            // Clear any stale pending buffer for a different level that may
            // have been mid-way through stabilizing.
            pendingLevel.remove(cameraId);
            return null;
        }

        // The reading differs from the committed level. Run it through the
        // stabilization buffer instead of committing immediately, so a
        // single flickering frame (e.g. 1 person vs 2 people right at a
        // threshold) can't flip the alerted level on its own.
        long now = System.currentTimeMillis();
        PendingLevel pending = pendingLevel.compute(cameraId, (id, existing) -> {
            if (existing != null && existing.level == alertType) {
                existing.count++;
                return existing;
            }
            // Either the first time we've seen this candidate level, or it's
            // a different candidate than what was pending — start a fresh buffer.
            return new PendingLevel(alertType, 1, now);
        });

        boolean stable = pending.count >= STABILIZATION_MIN_CYCLES
                || (now - pending.firstSeenMillis) >= STABILIZATION_MIN_DURATION_MS;
        if (!stable) {
            return null; // still stabilizing — ignore this fluctuation
        }

        // Stabilized — commit the new level.
        lastAlertedLevel.put(cameraId, alertType);
        pendingLevel.remove(cameraId);

        if (alertType == Alert.AlertType.LOW) return null;

        String message = String.format("[%s] %s at %s: %d/%d people (%.1f%% occupancy)",
                alertType.name(), camera.getCameraName(), camera.getLocationName(),
                peopleCount, camera.getMaximumCapacity(), occupancy.doubleValue());

        Alert alert = new Alert();
        alert.setCamera(camera);
        alert.setCrowdData(crowdData);
        alert.setAlertType(alertType);
        alert.setMessage(message);
        alert.setPeopleCount(peopleCount);
        alert.setOccupancyPercentage(occupancy);
        alert.setAcknowledged(false);           // fixed: was setIsAcknowledged()

        return alertRepository.save(alert);
    }

    /**
     * Alert level classification — mirrors MonitoringService.classifyCrowdLevel.
     * Based on capacity-based occupancy percentage, never raw people count.
     *
     *   0   – 25 %  -> LOW        (no alert created, see createAlert above)
     *   26  – 50 %  -> MEDIUM
     *   51  – 75 %  -> HIGH
     *   76  – 100 % -> CRITICAL
     *   > 100 %     -> OVERCROWDED
     */
    private Alert.AlertType determineAlertType(double occupancyPercentage) {
        if (occupancyPercentage > 100) return Alert.AlertType.OVERCROWDED;
        if (occupancyPercentage >= 76) return Alert.AlertType.CRITICAL;
        if (occupancyPercentage >= 51) return Alert.AlertType.HIGH;
        if (occupancyPercentage >= 26) return Alert.AlertType.MEDIUM;
        return Alert.AlertType.LOW;
    }

    /**
     * Defensively hardened: guards against null nested references so a
     * single malformed row can never crash the entire alerts list with
     * an NPE — satisfies the requirement that the alerts page must
     * never fail.
     */
    public AlertResponse mapToResponse(Alert alert) {
        Camera camera = alert.getCamera();
        return AlertResponse.builder()
                .id(alert.getId())
                .cameraId(camera != null ? camera.getId() : null)
                .cameraName(camera != null ? camera.getCameraName() : "Unknown Camera")
                .locationName(camera != null ? camera.getLocationName() : "Unknown Location")
                .alertType(alert.getAlertType() != null ? alert.getAlertType().name() : "LOW")
                .message(alert.getMessage())
                .peopleCount(alert.getPeopleCount())
                .occupancyPercentage(alert.getOccupancyPercentage() != null
                        ? alert.getOccupancyPercentage().doubleValue() : 0.0)
                .isAcknowledged(alert.getAcknowledged() != null ? alert.getAcknowledged() : false)
                .acknowledgedBy(alert.getAcknowledgedBy() != null
                        ? alert.getAcknowledgedBy().getUsername() : null)
                .acknowledgedAt(alert.getAcknowledgedAt())
                .createdAt(alert.getCreatedAt())
                .build();
    }
}
