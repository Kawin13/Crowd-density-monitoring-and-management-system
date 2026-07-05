package com.crowdmonitor.service;

import com.crowdmonitor.dto.response.CrowdDataResponse;
import com.crowdmonitor.entity.Camera;
import com.crowdmonitor.entity.CrowdData;
import com.crowdmonitor.exception.ResourceNotFoundException;
import com.crowdmonitor.repository.CameraRepository;
import com.crowdmonitor.repository.CrowdDataRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MonitoringService {

    private final CameraRepository cameraRepository;
    private final CrowdDataRepository crowdDataRepository;
    private final AlertService alertService;

    @Transactional
    public CrowdDataResponse saveCrowdData(Long cameraId, int peopleCount,
                                            byte[] frameData, byte[] heatmapData) {
        Camera camera = cameraRepository.findById(cameraId)
                .orElseThrow(() -> new ResourceNotFoundException("Camera", cameraId));

        BigDecimal occupancy = calculateOccupancy(peopleCount, camera.getMaximumCapacity());
        CrowdData.CrowdLevel crowdLevel = classifyCrowdLevel(occupancy.doubleValue());

        CrowdData crowdData = new CrowdData();
        crowdData.setCamera(camera);
        crowdData.setPeopleCount(peopleCount);
        crowdData.setOccupancyPercentage(occupancy);
        crowdData.setCrowdLevel(crowdLevel);
        crowdData.setFrameData(frameData);
        crowdData.setHeatmapData(heatmapData);

        CrowdData saved = crowdDataRepository.save(crowdData);

        // Always notify AlertService of the current level. AlertService
        // itself decides whether to create a persisted Alert row: it skips
        // LOW levels and, per the alert-deduplication requirement, skips
        // any level that is the same as the last one recorded for this
        // camera (so consecutive frames at the same crowd level do not
        // create duplicate alerts/notifications).
        alertService.createAlert(camera, saved, occupancy, peopleCount);

        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<CrowdDataResponse> getLatestDataForCamera(Long cameraId, int limit) {
        return crowdDataRepository.findByCameraIdOrderByRecordedAtDesc(cameraId, PageRequest.of(0, limit))
                .stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    /**
     * Returns the latest CrowdData for a camera, or null if none exists yet.
     *
     * FIX: previously threw ResourceNotFoundException whenever a camera had
     * no crowd_data rows — but "no data yet" is a completely normal state
     * (e.g. a camera that has never been started, or was just added). That
     * exception was surfacing as an HTTP 404/500 to the frontend, which the
     * dashboard/monitoring pages then reported as "Failed to load". This
     * method now returns null for that case, and the controller responds
     * with a clean 200 + informative message instead.
     */
    @Transactional(readOnly = true)
    public CrowdDataResponse getLatestForCamera(Long cameraId) {
        if (!cameraRepository.existsById(cameraId)) {
            log.warn("getLatestForCamera called for non-existent camera id={}", cameraId);
            return null;
        }
        return crowdDataRepository.findTopByCameraIdOrderByRecordedAtDesc(cameraId)
                .map(this::mapToResponse)
                .orElse(null);
    }

    private BigDecimal calculateOccupancy(int peopleCount, int maxCapacity) {
        if (maxCapacity <= 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf((double) peopleCount / maxCapacity * 100)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Capacity-based crowd level classification.
     *
     * Formula:  occupancyPercentage = (detectedPeople / maximumCapacity) * 100
     *
     * Classification:
     *   0   – 25 %  -> LOW
     *   26  – 50 %  -> MEDIUM
     *   51  – 75 %  -> HIGH
     *   76  – 100 % -> CRITICAL
     *   > 100 %     -> OVERCROWDED
     *
     * Raw people count is NEVER used directly to determine crowd level —
     * only the capacity-relative occupancy percentage drives classification.
     */
    private CrowdData.CrowdLevel classifyCrowdLevel(double occupancyPercentage) {
        if (occupancyPercentage > 100) return CrowdData.CrowdLevel.OVERCROWDED;
        if (occupancyPercentage >= 76) return CrowdData.CrowdLevel.CRITICAL;
        if (occupancyPercentage >= 51) return CrowdData.CrowdLevel.HIGH;
        if (occupancyPercentage >= 26) return CrowdData.CrowdLevel.MEDIUM;
        return CrowdData.CrowdLevel.LOW;
    }

    /**
     * Defensively hardened: even though camera_id has a NOT NULL foreign key
     * constraint in the schema, this guards against any null nested
     * reference so a single malformed row can never crash the whole
     * dashboard/monitoring response with an NPE.
     */
    public CrowdDataResponse mapToResponse(CrowdData cd) {
        Camera camera = cd.getCamera();
        return CrowdDataResponse.builder()
                .id(cd.getId())
                .cameraId(camera != null ? camera.getId() : null)
                .cameraName(camera != null ? camera.getCameraName() : "Unknown Camera")
                .locationName(camera != null ? camera.getLocationName() : "Unknown Location")
                .maximumCapacity(camera != null ? camera.getMaximumCapacity() : 0)
                .peopleCount(cd.getPeopleCount())
                .occupancyPercentage(cd.getOccupancyPercentage())
                .crowdLevel(cd.getCrowdLevel() != null ? cd.getCrowdLevel().name() : "LOW")
                .frameDataBase64(cd.getFrameData() != null ? Base64.getEncoder().encodeToString(cd.getFrameData()) : null)
                .heatmapDataBase64(cd.getHeatmapData() != null ? Base64.getEncoder().encodeToString(cd.getHeatmapData()) : null)
                .recordedAt(cd.getRecordedAt())
                .build();
    }
}
