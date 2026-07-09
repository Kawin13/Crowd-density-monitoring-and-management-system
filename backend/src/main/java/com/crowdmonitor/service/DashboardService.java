package com.crowdmonitor.service;

import com.crowdmonitor.dto.response.AlertResponse;
import com.crowdmonitor.dto.response.CameraResponse;
import com.crowdmonitor.dto.response.DashboardResponse;
import com.crowdmonitor.entity.Camera;
import com.crowdmonitor.entity.CrowdData;
import com.crowdmonitor.repository.AlertRepository;
import com.crowdmonitor.repository.CameraRepository;
import com.crowdmonitor.repository.CrowdDataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final CameraRepository cameraRepository;
    private final CrowdDataRepository crowdDataRepository;
    private final AlertRepository alertRepository;
    private final CameraService cameraService;
    private final AlertService alertService;

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard() {
        long totalCameras = cameraRepository.count();

        // Per requirement: dashboard must load successfully even if database
        // tables are completely empty (e.g. first run before any cameras are
        // added). Returning early here avoids any edge-case risk in the
        // per-camera loops below when there is simply nothing to iterate.
        if (totalCameras == 0) {
            Map<String, Long> emptyCrowdLevels = new HashMap<>();
            for (CrowdData.CrowdLevel level : CrowdData.CrowdLevel.values()) {
                emptyCrowdLevels.put(level.name(), 0L);
            }
            Map<String, Long> emptyAlertTypes = new HashMap<>();
            for (com.crowdmonitor.entity.Alert.AlertType type :
                    com.crowdmonitor.entity.Alert.AlertType.values()) {
                emptyAlertTypes.put(type.name(), 0L);
            }
            return DashboardResponse.builder()
                    .totalCameras(0)
                    .activeCameras(0)
                    .monitoringCameras(0)
                    .totalPeopleCount(0)
                    .averageOccupancy(0.0)
                    .unacknowledgedAlerts(alertRepository.countByAcknowledgedFalse())
                    .crowdLevelCounts(emptyCrowdLevels)
                    .alertTypeCounts(emptyAlertTypes)
                    .recentCameraData(List.of())
                    .recentAlerts(alertService.getRecentAlerts(10))
                    .generatedAt(LocalDateTime.now())
                    .build();
        }

        long activeCameras = cameraRepository.countByStatusIn(
                List.of(Camera.CameraStatus.ACTIVE, Camera.CameraStatus.MONITORING));
        long monitoringCameras = cameraRepository.countByStatus(Camera.CameraStatus.MONITORING);

        // PERFORMANCE FIX: previously, this loop called
        // crowdDataRepository.findTopByCameraIdOrderByRecordedAtDesc(cam.getId())
        // once per camera here, and cameraService.mapToResponse() called the
        // exact same query AGAIN per camera further down — doubling the number
        // of queries needed (2N instead of N for N cameras). A later pass
        // reduced that to N by reusing one lookup, but this still meant one
        // database round trip per camera. findLatestForEachCamera() now
        // fetches the latest reading for every camera in a single query
        // regardless of how many cameras exist.
        List<Camera> allCameras = cameraRepository.findAll();
        Map<Long, CrowdData> latestByCamera = crowdDataRepository.findLatestForEachCamera().stream()
                .collect(Collectors.toMap(cd -> cd.getCamera().getId(), cd -> cd, (a, b) -> a));

        int totalPeopleCount = latestByCamera.values().stream()
                .mapToInt(CrowdData::getPeopleCount)
                .sum();

        Double avgOccupancy = crowdDataRepository
                .findAverageOccupancySince(LocalDateTime.now().minusHours(1));
        double averageOccupancy = avgOccupancy != null ? avgOccupancy : 0.0;

        // fixed: was countByIsAcknowledgedFalse()
        long unacknowledgedAlerts = alertRepository.countByAcknowledgedFalse();

        // Crowd level distribution
        Map<String, Long> crowdLevelCounts = new HashMap<>();
        for (CrowdData.CrowdLevel level : CrowdData.CrowdLevel.values()) {
            crowdLevelCounts.put(level.name(), 0L);
        }
        // PERFORMANCE FIX: previously loaded every full CrowdData row (plus
        // a JOIN FETCH to Camera) from the last 30 minutes just to bucket
        // them by level in Java. Counting is now done in the database.
        crowdDataRepository.countByCrowdLevelSince(LocalDateTime.now().minusMinutes(30))
                .forEach(row -> {
                    CrowdData.CrowdLevel level = (CrowdData.CrowdLevel) row[0];
                    Long count = (Long) row[1];
                    crowdLevelCounts.put(level != null ? level.name() : "LOW", count);
                });

        // Alert type distribution
        Map<String, Long> alertTypeCounts = new HashMap<>();
        for (com.crowdmonitor.entity.Alert.AlertType type :
                com.crowdmonitor.entity.Alert.AlertType.values()) {
            alertTypeCounts.put(type.name(), 0L);
        }
        // PERFORMANCE FIX: previously ran one countByAlertType() query per
        // enum value in a loop; now a single grouped query returns them all.
        alertRepository.countGroupedByAlertType().forEach(row -> {
            com.crowdmonitor.entity.Alert.AlertType type = (com.crowdmonitor.entity.Alert.AlertType) row[0];
            Long count = (Long) row[1];
            alertTypeCounts.put(type.name(), count);
        });

        List<CameraResponse> recentCameraData = allCameras.stream()
                .limit(10)
                .map(cam -> cameraService.mapToResponse(cam, latestByCamera.get(cam.getId())))
                .collect(Collectors.toList());

        List<AlertResponse> recentAlerts = alertService.getRecentAlerts(10);

        return DashboardResponse.builder()
                .totalCameras(totalCameras)
                .activeCameras(activeCameras)
                .monitoringCameras(monitoringCameras)
                .totalPeopleCount(totalPeopleCount)
                .averageOccupancy(Math.round(averageOccupancy * 100.0) / 100.0)
                .unacknowledgedAlerts(unacknowledgedAlerts)
                .crowdLevelCounts(crowdLevelCounts)
                .alertTypeCounts(alertTypeCounts)
                .recentCameraData(recentCameraData)
                .recentAlerts(recentAlerts)
                .generatedAt(LocalDateTime.now())
                .build();
    }
}
