package com.crowdmonitor.repository;

import com.crowdmonitor.entity.Alert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {

    // field renamed: isAcknowledged -> acknowledged
    // JOIN FETCH a.camera prevents LazyInitializationException when
    // AlertService.mapToResponse() later reads camera.getCameraName() etc.
    @Query("SELECT a FROM Alert a JOIN FETCH a.camera WHERE a.acknowledged = false ORDER BY a.createdAt DESC")
    List<Alert> findByAcknowledgedFalseOrderByCreatedAtDesc();

    @Query(value = "SELECT a FROM Alert a JOIN FETCH a.camera WHERE a.camera.id = :cameraId ORDER BY a.createdAt DESC",
           countQuery = "SELECT COUNT(a) FROM Alert a WHERE a.camera.id = :cameraId")
    Page<Alert> findByCameraIdOrderByCreatedAtDesc(@Param("cameraId") Long cameraId, Pageable pageable);

    @Query(value = "SELECT a FROM Alert a JOIN FETCH a.camera WHERE a.alertType = :alertType ORDER BY a.createdAt DESC",
           countQuery = "SELECT COUNT(a) FROM Alert a WHERE a.alertType = :alertType")
    Page<Alert> findByAlertTypeOrderByCreatedAtDesc(@Param("alertType") Alert.AlertType alertType, Pageable pageable);

    @Query("SELECT a FROM Alert a JOIN FETCH a.camera WHERE a.createdAt BETWEEN :start AND :end ORDER BY a.createdAt DESC")
    List<Alert> findByDateRange(@Param("start") LocalDateTime start,
                                @Param("end") LocalDateTime end);

    // field renamed: isAcknowledged -> acknowledged
    long countByAcknowledgedFalse();

    long countByAlertType(Alert.AlertType alertType);

    // PERFORMANCE FIX (dashboard load time): DashboardService previously ran
    // one countByAlertType() query per AlertType enum value in a loop. This
    // single grouped query returns all the counts in one round trip.
    @Query("SELECT a.alertType, COUNT(a) FROM Alert a GROUP BY a.alertType")
    List<Object[]> countGroupedByAlertType();

    @Query("SELECT a FROM Alert a JOIN FETCH a.camera WHERE a.camera.id = :cameraId AND a.createdAt >= :since ORDER BY a.createdAt DESC")
    List<Alert> findRecentAlertsByCamera(@Param("cameraId") Long cameraId,
                                         @Param("since") LocalDateTime since);

    /**
     * FIX for LazyInitializationException: "could not initialize proxy
     * [Camera#N] - no Session".
     *
     * Previously this query loaded only the Alert entity, leaving its
     * camera field as an unfetched Hibernate lazy proxy. AlertService's
     * mapToResponse() then called camera.getCameraName() to build the
     * dashboard's "recent alerts" list — but neither getRecentAlerts() nor
     * DashboardService.getDashboard() were wrapped in @Transactional, and
     * open-in-view is disabled (spring.jpa.open-in-view=false), so the
     * Hibernate session that loaded the Alert had already closed by the
     * time the proxy was dereferenced. Any camera whose data happened to
     * be accessed after its originating session closed (which is
     * effectively guaranteed under normal request handling) crashed the
     * entire /api/dashboard response with a 500.
     *
     * JOIN FETCH a.camera loads the Alert and its Camera in a single query,
     * so camera is always a fully-populated entity, never a lazy proxy —
     * eliminating both the LazyInitializationException and an N+1 query
     * per alert.
     */
    @Query("SELECT a FROM Alert a JOIN FETCH a.camera WHERE a.acknowledged = false ORDER BY a.createdAt DESC")
    List<Alert> findActiveAlerts(Pageable pageable);
}
