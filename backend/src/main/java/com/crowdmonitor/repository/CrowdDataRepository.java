package com.crowdmonitor.repository;

import com.crowdmonitor.entity.CrowdData;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * FIX FOR LazyInitializationException ("could not initialize proxy Camera#N - no Session"):
 *
 * Root cause (from production stack trace):
 *   AlertService.mapToResponse() called camera.getCameraName() on an Alert
 *   whose camera field was a Hibernate LAZY proxy. Because open-in-view is
 *   disabled (spring.jpa.open-in-view=false) and neither getRecentAlerts()
 *   nor getDashboard() were @Transactional, the Hibernate session that
 *   loaded the Alert had already closed before mapToResponse() ran, making
 *   the proxy non-initializable -> LazyInitializationException -> 500.
 *
 * Two-layer fix applied here:
 *   1. JOIN FETCH on every @Query that returns entities whose camera/related
 *      fields are later dereferenced. This loads everything in one SQL
 *      statement so no lazy proxy is ever involved.
 *
 *   2. Derived-query methods (e.g. findTopByCameraIdOrderByRecordedAtDesc)
 *      cannot carry JOIN FETCH — callers of those methods are wrapped in
 *      @Transactional(readOnly = true) in the service layer so the session
 *      stays open through the mapping step.
 */
@Repository
public interface CrowdDataRepository extends JpaRepository<CrowdData, Long> {

    @Query(value = "SELECT cd FROM CrowdData cd JOIN FETCH cd.camera " +
                   "WHERE cd.camera.id = :cameraId ORDER BY cd.recordedAt DESC",
           countQuery = "SELECT COUNT(cd) FROM CrowdData cd WHERE cd.camera.id = :cameraId")
    List<CrowdData> findByCameraIdOrderByRecordedAtDesc(@Param("cameraId") Long cameraId, Pageable pageable);

    // Derived query — JOIN FETCH not possible here.
    // Callers MUST be @Transactional(readOnly = true). See DashboardService
    // and MonitoringService for the annotations that protect this call.
    Optional<CrowdData> findTopByCameraIdOrderByRecordedAtDesc(Long cameraId);

    @Query("SELECT cd FROM CrowdData cd JOIN FETCH cd.camera " +
           "WHERE cd.camera.id = :cameraId AND cd.recordedAt BETWEEN :start AND :end " +
           "ORDER BY cd.recordedAt ASC")
    List<CrowdData> findByCameraAndDateRange(@Param("cameraId") Long cameraId,
                                              @Param("start") LocalDateTime start,
                                              @Param("end") LocalDateTime end);

    @Query("SELECT cd FROM CrowdData cd JOIN FETCH cd.camera " +
           "WHERE cd.recordedAt BETWEEN :start AND :end ORDER BY cd.recordedAt DESC")
    List<CrowdData> findByDateRange(@Param("start") LocalDateTime start,
                                    @Param("end") LocalDateTime end);

    @Query("SELECT AVG(cd.occupancyPercentage) FROM CrowdData cd WHERE cd.recordedAt >= :since")
    Double findAverageOccupancySince(@Param("since") LocalDateTime since);

    @Query("SELECT SUM(cd.peopleCount) FROM CrowdData cd " +
           "WHERE cd.camera.id = :cameraId " +
           "AND cd.recordedAt = (SELECT MAX(cd2.recordedAt) FROM CrowdData cd2 WHERE cd2.camera.id = :cameraId)")
    Integer findLatestPeopleCountForCamera(@Param("cameraId") Long cameraId);

    @Query("SELECT HOUR(cd.recordedAt), AVG(cd.peopleCount) FROM CrowdData cd " +
           "WHERE cd.camera.id = :cameraId AND cd.recordedAt BETWEEN :start AND :end " +
           "GROUP BY HOUR(cd.recordedAt) ORDER BY HOUR(cd.recordedAt)")
    List<Object[]> findHourlyAverageByCamera(@Param("cameraId") Long cameraId,
                                              @Param("start") LocalDateTime start,
                                              @Param("end") LocalDateTime end);

    @Query("SELECT cd FROM CrowdData cd JOIN FETCH cd.camera " +
           "WHERE cd.recordedAt >= :since ORDER BY cd.recordedAt DESC")
    List<CrowdData> findRecentData(@Param("since") LocalDateTime since);

    // PERFORMANCE FIX (dashboard load time): DashboardService previously
    // called findTopByCameraIdOrderByRecordedAtDesc(camera.getId()) once per
    // camera in a loop — N separate round trips for N cameras. This single
    // query returns the latest CrowdData row for every camera in one round
    // trip, which DashboardService now uses instead of looping.
    @Query("SELECT cd FROM CrowdData cd JOIN FETCH cd.camera " +
           "WHERE cd.recordedAt = (SELECT MAX(cd2.recordedAt) FROM CrowdData cd2 WHERE cd2.camera = cd.camera)")
    List<CrowdData> findLatestForEachCamera();

    // PERFORMANCE FIX (dashboard load time): previously the dashboard loaded
    // every full CrowdData row (with an unneeded JOIN FETCH to Camera) from
    // the last 30 minutes just to count how many fell into each crowd-level
    // bucket in Java. For a busy deployment that can be thousands of rows
    // transferred just to compute four numbers. This does the counting in
    // the database instead and returns only the small aggregated result.
    @Query("SELECT cd.crowdLevel, COUNT(cd) FROM CrowdData cd " +
           "WHERE cd.recordedAt >= :since GROUP BY cd.crowdLevel")
    List<Object[]> countByCrowdLevelSince(@Param("since") LocalDateTime since);

    // PERFORMANCE FIX (dashboard load time): AnalyticsService.buildAnalytics()
    // previously hard-coded an empty hourly trend whenever no cameraId was
    // given (the "all cameras" view used by the dashboard), instead of
    // querying anything. This provides the missing all-camera equivalent of
    // findHourlyAverageByCamera().
    @Query("SELECT HOUR(cd.recordedAt), AVG(cd.peopleCount) FROM CrowdData cd " +
           "WHERE cd.recordedAt BETWEEN :start AND :end " +
           "GROUP BY HOUR(cd.recordedAt) ORDER BY HOUR(cd.recordedAt)")
    List<Object[]> findHourlyAverageAll(@Param("start") LocalDateTime start,
                                         @Param("end") LocalDateTime end);

    // PERFORMANCE FIX (dashboard load time): AnalyticsService previously
    // loaded every CrowdData row (with a JOIN FETCH to Camera it didn't need)
    // in the requested date range just to compute count/avg/max/min in Java.
    // For DAILY/WEEKLY/MONTHLY ranges across all cameras this could be a
    // very large result set on every single dashboard refresh. These two
    // queries compute the same numbers in the database.
    @Query("SELECT COUNT(cd), AVG(cd.peopleCount), MAX(cd.peopleCount), MIN(cd.peopleCount) " +
           "FROM CrowdData cd WHERE cd.recordedAt BETWEEN :start AND :end")
    List<Object[]> findStatsBetween(@Param("start") LocalDateTime start,
                                     @Param("end") LocalDateTime end);

    @Query("SELECT COUNT(cd), AVG(cd.peopleCount), MAX(cd.peopleCount), MIN(cd.peopleCount) " +
           "FROM CrowdData cd WHERE cd.camera.id = :cameraId AND cd.recordedAt BETWEEN :start AND :end")
    List<Object[]> findStatsForCameraBetween(@Param("cameraId") Long cameraId,
                                              @Param("start") LocalDateTime start,
                                              @Param("end") LocalDateTime end);

    @Query("SELECT cd.crowdLevel, COUNT(cd) FROM CrowdData cd " +
           "WHERE cd.recordedAt BETWEEN :start AND :end GROUP BY cd.crowdLevel")
    List<Object[]> countByCrowdLevelBetween(@Param("start") LocalDateTime start,
                                             @Param("end") LocalDateTime end);

    @Query("SELECT cd.crowdLevel, COUNT(cd) FROM CrowdData cd " +
           "WHERE cd.camera.id = :cameraId AND cd.recordedAt BETWEEN :start AND :end GROUP BY cd.crowdLevel")
    List<Object[]> countByCrowdLevelForCameraBetween(@Param("cameraId") Long cameraId,
                                                       @Param("start") LocalDateTime start,
                                                       @Param("end") LocalDateTime end);
}
