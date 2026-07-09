package com.crowdmonitor.service;

import com.crowdmonitor.dto.request.CameraRequest;
import com.crowdmonitor.dto.response.CameraResponse;
import com.crowdmonitor.entity.Camera;
import com.crowdmonitor.entity.CrowdData;
import com.crowdmonitor.entity.User;
import com.crowdmonitor.exception.ResourceNotFoundException;
import com.crowdmonitor.repository.CameraRepository;
import com.crowdmonitor.repository.CrowdDataRepository;
import com.crowdmonitor.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CameraService {

    private final CameraRepository cameraRepository;
    private final CrowdDataRepository crowdDataRepository;
    private final UserRepository userRepository;
    private final AlertService alertService;
    private final AuditLogService auditLogService;
    // Shared bean from RestTemplateConfig — used to tell the AI service to
    // release a camera's VideoCapture when its Camera Service is stopped.
    private final RestTemplate restTemplate;

    @Value("${app.ai.service.url}")
    private String aiServiceUrl;

    public List<CameraResponse> getAllCameras() {
        return cameraRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public CameraResponse getCameraById(Long id) {
        Camera camera = cameraRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Camera", id));
        return mapToResponse(camera);
    }

    @Transactional
    public CameraResponse createCamera(CameraRequest request) {
        Camera camera = new Camera();
        camera.setCameraName(request.getCameraName());
        camera.setCameraType(Camera.CameraType.valueOf(request.getCameraType().toUpperCase()));
        camera.setLocationName(request.getLocationName());
        camera.setMaximumCapacity(request.getMaximumCapacity());
        camera.setStreamUrl(request.getStreamUrl());
        camera.setDescription(request.getDescription());
        camera.setStatus(Camera.CameraStatus.INACTIVE);

        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        userRepository.findByUsername(username).ifPresent(camera::setCreatedBy);

        Camera saved = cameraRepository.save(camera);
        auditLogService.log("CAMERA_ADDED", "CAMERA", saved.getId(),
                "Camera '" + saved.getCameraName() + "' added");
        return mapToResponse(saved);
    }

    @Transactional
    public CameraResponse updateCamera(Long id, CameraRequest request) {
        Camera camera = cameraRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Camera", id));

        camera.setCameraName(request.getCameraName());
        camera.setCameraType(Camera.CameraType.valueOf(request.getCameraType().toUpperCase()));
        camera.setLocationName(request.getLocationName());
        camera.setMaximumCapacity(request.getMaximumCapacity());
        camera.setStreamUrl(request.getStreamUrl());
        camera.setDescription(request.getDescription());

        Camera saved = cameraRepository.save(camera);
        auditLogService.log("CAMERA_UPDATED", "CAMERA", saved.getId(),
                "Camera '" + saved.getCameraName() + "' updated");
        return mapToResponse(saved);
    }

    @Transactional
    public void deleteCamera(Long id) {
        Camera camera = cameraRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Camera", id));
        String cameraName = camera.getCameraName();
        cameraRepository.deleteById(id);
        auditLogService.log("CAMERA_DELETED", "CAMERA", id,
                "Camera '" + cameraName + "' deleted");
    }

    @Transactional
    public CameraResponse startMonitoring(Long id) {
        Camera camera = cameraRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Camera", id));
        camera.setStatus(Camera.CameraStatus.MONITORING);
        // New analysis session begins — clear any remembered alert level so
        // dedup doesn't bleed across sessions (e.g. camera was left at
        // CRITICAL, stopped, restarted, and immediately reads CRITICAL
        // again — that should still alert).
        alertService.resetAlertState(id);
        CameraResponse response = mapToResponse(cameraRepository.save(camera));
        auditLogService.log("MONITORING_STARTED", "CAMERA", id,
                "Monitoring started for camera '" + camera.getCameraName() + "'");
        return response;
    }

    @Transactional
    public CameraResponse stopMonitoring(Long id) {
        Camera camera = cameraRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Camera", id));
        camera.setStatus(Camera.CameraStatus.INACTIVE);
        // Camera service stopped — reset the stored alert level for this camera.
        alertService.resetAlertState(id);
        CameraResponse response = mapToResponse(cameraRepository.save(camera));
        auditLogService.log("MONITORING_STOPPED", "CAMERA", id,
                "Monitoring stopped for camera '" + camera.getCameraName() + "'");

        // Best-effort: tell the AI service to stop its continuous analysis
        // session and release the VideoCapture for this camera. Failures
        // here must never block the camera from being marked stopped in
        // the database — the AI service also self-cleans idle sessions,
        // so this is a "stop gracefully now" nudge, not a hard dependency.
        try {
            URI uri = UriComponentsBuilder
                    .fromHttpUrl(aiServiceUrl + "/api/analyze/stream/stop")
                    .queryParam("camera_id", id)
                    .build(true)
                    .toUri();
            restTemplate.postForEntity(uri, null, Map.class);
        } catch (Exception e) {
            log.warn("Could not stop AI analysis session for camera {} (AI service may be offline): {}",
                    id, e.getMessage());
        }

        return response;
    }

    public CameraResponse mapToResponse(Camera camera) {
        CrowdData latest = crowdDataRepository.findTopByCameraIdOrderByRecordedAtDesc(camera.getId()).orElse(null);
        return mapToResponse(camera, latest);
    }

    /**
     * Overload allowing callers that have already fetched a camera's latest
     * CrowdData (e.g. DashboardService, which needs it for the people-count
     * sum anyway) to avoid triggering a second, redundant
     * findTopByCameraIdOrderByRecordedAtDesc query for the same camera.
     *
     * Defensively hardened against null occupancyPercentage/crowdLevel on
     * an otherwise non-null CrowdData row — this directly prevents an NPE
     * from propagating up through DashboardService.getDashboard(), which
     * calls this method once per camera.
     */
    public CameraResponse mapToResponse(Camera camera, CrowdData latest) {
        return CameraResponse.builder()
                .id(camera.getId())
                .cameraName(camera.getCameraName())
                .cameraType(camera.getCameraType().name())
                .locationName(camera.getLocationName())
                .maximumCapacity(camera.getMaximumCapacity())
                .streamUrl(camera.getStreamUrl())
                .status(camera.getStatus().name())
                .description(camera.getDescription())
                .currentPeopleCount(latest != null ? latest.getPeopleCount() : 0)
                .currentOccupancy(latest != null && latest.getOccupancyPercentage() != null
                        ? latest.getOccupancyPercentage().doubleValue() : 0.0)
                .currentCrowdLevel(latest != null && latest.getCrowdLevel() != null
                        ? latest.getCrowdLevel().name() : "LOW")
                .createdAt(camera.getCreatedAt())
                .updatedAt(camera.getUpdatedAt())
                .build();
    }
}
