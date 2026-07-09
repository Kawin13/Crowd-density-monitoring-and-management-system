package com.crowdmonitor.repository;

import com.crowdmonitor.entity.Camera;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface CameraRepository extends JpaRepository<Camera, Long> {
    List<Camera> findByStatus(Camera.CameraStatus status);
    List<Camera> findByCameraType(Camera.CameraType cameraType);
    long countByStatus(Camera.CameraStatus status);

    // PERFORMANCE FIX (dashboard load time): DashboardService previously
    // ran countByStatus(ACTIVE) + countByStatus(MONITORING) as two separate
    // queries. This combines them into a single round trip.
    long countByStatusIn(Collection<Camera.CameraStatus> statuses);

    @Query("SELECT c FROM Camera c WHERE c.status = 'MONITORING' OR c.status = 'ACTIVE'")
    List<Camera> findActiveCameras();
}
