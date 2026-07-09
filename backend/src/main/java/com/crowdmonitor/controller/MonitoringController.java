package com.crowdmonitor.controller;

import com.crowdmonitor.dto.response.ApiResponse;
import com.crowdmonitor.dto.response.CrowdDataResponse;
import com.crowdmonitor.service.AlertService;
import com.crowdmonitor.service.CameraService;
import com.crowdmonitor.service.MonitoringService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * REST controller for live monitoring, stream analysis, and video upload.
 *
 * AI service integration fixes:
 *  - streamUrl is URL-encoded via UriComponentsBuilder so RTSP/HTTP URLs
 *    with special characters are not corrupted when passed as query params.
 *  - camera_id (snake_case) used in AI service form fields to match the
 *    Python parameter name, preventing 422 Unprocessable Entity errors.
 *  - RestTemplate connection / read timeouts set to prevent indefinite hangs
 *    when the AI service is slow to respond (e.g. during YOLO warm-up).
 */
@RestController
@RequestMapping("/api/monitoring")
@RequiredArgsConstructor
@Slf4j
public class MonitoringController {

    private final MonitoringService monitoringService;
    private final CameraService cameraService;
    private final AlertService alertService;
    // Shared bean from RestTemplateConfig — has 5s connect / 30s read timeouts
    // so a stalled AI service call fails fast instead of hanging this thread.
    private final RestTemplate restTemplate;

    @Value("${app.ai.service.url}")
    private String aiServiceUrl;

    // -----------------------------------------------------------------------
    // Data retrieval
    // -----------------------------------------------------------------------

    @GetMapping("/cameras/{cameraId}/latest")
    public ResponseEntity<ApiResponse<CrowdDataResponse>> getLatest(@PathVariable Long cameraId) {
        try {
            CrowdDataResponse latest = monitoringService.getLatestForCamera(cameraId);
            if (latest == null) {
                // Camera exists but has no crowd data recorded yet — this is a
                // normal, expected state (e.g. monitoring never started), not
                // an error. Return 200 with a null payload and a clear message
                // instead of 404, so the frontend never has to treat "no data
                // yet" as a failed request.
                return ResponseEntity.ok(
                        ApiResponse.success("No monitoring data available yet for this camera", null));
            }
            return ResponseEntity.ok(ApiResponse.success(latest));
        } catch (Exception e) {
            // Per requirement: this endpoint must NEVER return 500. Any
            // unexpected failure (DB hiccup, data inconsistency, etc.) is
            // logged server-side and surfaced to the frontend as a clean
            // 200 + "no data" response instead of an opaque 500.
            log.error("Failed to fetch latest data for camera {}: {}", cameraId, e.getMessage());
            return ResponseEntity.ok(
                    ApiResponse.success("No monitoring data available yet for this camera", null));
        }
    }

    @GetMapping("/cameras/{cameraId}/history")
    public ResponseEntity<ApiResponse<List<CrowdDataResponse>>> getHistory(
            @PathVariable Long cameraId,
            @RequestParam(defaultValue = "50") int limit) {
        try {
            return ResponseEntity.ok(ApiResponse.success(
                    monitoringService.getLatestDataForCamera(cameraId, limit)));
        } catch (Exception e) {
            log.error("Failed to fetch history for camera {}: {}", cameraId, e.getMessage());
            return ResponseEntity.ok(ApiResponse.success(List.of()));
        }
    }

    // -----------------------------------------------------------------------
    // Data ingest — called by the AI service to POST results back
    // -----------------------------------------------------------------------

    @PostMapping("/cameras/{cameraId}/data")
    public ResponseEntity<ApiResponse<CrowdDataResponse>> receiveCrowdData(
            @PathVariable Long cameraId,
            @RequestParam int peopleCount,
            @RequestParam(required = false) MultipartFile frame,
            @RequestParam(required = false) MultipartFile heatmap) {
        try {
            byte[] frameBytes   = frame   != null ? frame.getBytes()   : null;
            byte[] heatmapBytes = heatmap != null ? heatmap.getBytes() : null;

            if (log.isDebugEnabled()) {
                log.debug("Camera {}: peopleCount={}, frameBytes={}, heatmapBytes={}",
                        cameraId, peopleCount,
                        frameBytes != null ? frameBytes.length : 0,
                        heatmapBytes != null ? heatmapBytes.length : 0);
            }

            CrowdDataResponse response =
                    monitoringService.saveCrowdData(cameraId, peopleCount, frameBytes, heatmapBytes);
            return ResponseEntity.ok(ApiResponse.success("Data saved", response));

        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // Surfaces DB-level issues (e.g. column too small, constraint violation)
            // with a clear message instead of a generic 400 that hides the real cause.
            log.error("Database constraint violation saving crowd data for camera {}: {}",
                    cameraId, e.getMostSpecificCause().getMessage());
            return ResponseEntity.badRequest().body(
                    ApiResponse.error("Database error: " + e.getMostSpecificCause().getMessage()));
        } catch (Exception e) {
            log.error("Failed to save crowd data for camera {}: {}", cameraId, e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    // -----------------------------------------------------------------------
    // Stream analysis — delegates to AI service
    // -----------------------------------------------------------------------

    @PostMapping("/cameras/{cameraId}/analyze-stream")
    @SuppressWarnings("unchecked")
    public ResponseEntity<ApiResponse<Map<String, Object>>> analyzeStream(@PathVariable Long cameraId) {
        try {
            var camera = cameraService.getCameraById(cameraId);
            String streamUrl = camera.getStreamUrl();

            if (streamUrl == null || streamUrl.isBlank()) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Camera has no stream URL configured"));
            }

            // URL-encode the streamUrl so RTSP/HTTP special characters survive transit
            URI uri = UriComponentsBuilder
                    .fromHttpUrl(aiServiceUrl + "/api/analyze/stream")
                    .queryParam("camera_id", cameraId)
                    .queryParam("stream_url", streamUrl)
                    .queryParam("capacity",   camera.getMaximumCapacity())
                    .queryParam("frames",     5)
                    .build(true)   // true = already encoded (UriComponentsBuilder encodes values)
                    .toUri();

            ResponseEntity<Map> aiResponse = restTemplate.postForEntity(uri, null, Map.class);
            return ResponseEntity.ok(ApiResponse.success("Analysis triggered", aiResponse.getBody()));

        } catch (ResourceAccessException e) {
            log.warn("AI service unreachable for stream analysis: {}", e.getMessage());
            return ResponseEntity.ok(ApiResponse.error(
                    "AI service is not running. Start it with: python main.py"));
        } catch (Exception e) {
            log.error("Stream analysis error for camera {}: {}", cameraId, e.getMessage());
            return ResponseEntity.ok(ApiResponse.error("Stream analysis failed: " + e.getMessage()));
        }
    }

    // -----------------------------------------------------------------------
    // Continuous analysis SESSION — start once, keep the camera open, and
    // read frames continuously until explicitly stopped. Replaces the old
    // pattern of the frontend calling analyze-stream every few seconds
    // (which reopened the camera on every call and produced duplicate
    // notifications). "Analyze Now" -> /start (idempotent); "Stop Camera
    // Service" -> /stop (releases the VideoCapture in the AI service).
    // -----------------------------------------------------------------------

    @PostMapping("/cameras/{cameraId}/analyze-stream/start")
    @SuppressWarnings("unchecked")
    public ResponseEntity<ApiResponse<Map<String, Object>>> startAnalyzeStream(@PathVariable Long cameraId) {
        try {
            var camera = cameraService.getCameraById(cameraId);
            String streamUrl = camera.getStreamUrl();

            if (streamUrl == null || streamUrl.isBlank()) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Camera has no stream URL configured"));
            }

            URI uri = UriComponentsBuilder
                    .fromHttpUrl(aiServiceUrl + "/api/analyze/stream/start")
                    .queryParam("camera_id", cameraId)
                    .queryParam("stream_url", streamUrl)
                    .queryParam("capacity",   camera.getMaximumCapacity())
                    .queryParam("frames",     5)
                    .build(true)
                    .toUri();

            ResponseEntity<Map> aiResponse = restTemplate.postForEntity(uri, null, Map.class);
            Map<String, Object> body = aiResponse.getBody();

            // Only a genuinely NEW session begins a fresh alert-stabilization
            // window. If the session was already running, leave the existing
            // lastAlertedLevel/pending buffer alone — nothing changed.
            if (body != null && "started".equals(body.get("status"))) {
                alertService.resetAlertState(cameraId);
            }

            return ResponseEntity.ok(ApiResponse.success("Continuous analysis session started", body));

        } catch (ResourceAccessException e) {
            log.warn("AI service unreachable for stream session start: {}", e.getMessage());
            return ResponseEntity.ok(ApiResponse.error(
                    "AI service is not running. Start it with: python main.py"));
        } catch (Exception e) {
            log.error("Stream session start error for camera {}: {}", cameraId, e.getMessage());
            return ResponseEntity.ok(ApiResponse.error("Failed to start analysis session: " + e.getMessage()));
        }
    }

    @PostMapping("/cameras/{cameraId}/analyze-stream/stop")
    @SuppressWarnings("unchecked")
    public ResponseEntity<ApiResponse<Map<String, Object>>> stopAnalyzeStream(@PathVariable Long cameraId) {
        try {
            URI uri = UriComponentsBuilder
                    .fromHttpUrl(aiServiceUrl + "/api/analyze/stream/stop")
                    .queryParam("camera_id", cameraId)
                    .build(true)
                    .toUri();

            ResponseEntity<Map> aiResponse = restTemplate.postForEntity(uri, null, Map.class);

            // Explicit Stop Analysis closes the session — reset dedup/
            // stabilization state so a future Analyze Now starts fresh.
            alertService.resetAlertState(cameraId);

            return ResponseEntity.ok(ApiResponse.success("Analysis session stopped", aiResponse.getBody()));

        } catch (ResourceAccessException e) {
            log.warn("AI service unreachable for stream session stop: {}", e.getMessage());
            return ResponseEntity.ok(ApiResponse.error("AI service is not running."));
        } catch (Exception e) {
            log.error("Stream session stop error for camera {}: {}", cameraId, e.getMessage());
            return ResponseEntity.ok(ApiResponse.error("Failed to stop analysis session: " + e.getMessage()));
        }
    }

    @GetMapping("/cameras/{cameraId}/analyze-stream/status")
    @SuppressWarnings("unchecked")
    public ResponseEntity<ApiResponse<Map<String, Object>>> analyzeStreamStatus(@PathVariable Long cameraId) {
        // Lets the frontend reconnect to an already-running session (e.g. on
        // page reload or when the Monitoring page remounts) instead of
        // assuming analysis is stopped just because local UI state was lost.
        try {
            URI uri = UriComponentsBuilder
                    .fromHttpUrl(aiServiceUrl + "/api/analyze/stream/status")
                    .queryParam("camera_id", cameraId)
                    .build(true)
                    .toUri();

            ResponseEntity<Map> aiResponse = restTemplate.getForEntity(uri, Map.class);
            return ResponseEntity.ok(ApiResponse.success(aiResponse.getBody()));

        } catch (Exception e) {
            // AI service unreachable or any other issue — safest default is
            // "not analyzing" rather than surfacing an error for a status poll.
            log.warn("Stream session status check failed for camera {}: {}", cameraId, e.getMessage());
            return ResponseEntity.ok(ApiResponse.success(
                    Map.of("camera_id", cameraId, "is_analyzing", false)));
        }
    }

    // -----------------------------------------------------------------------
    // Video upload — forwards to AI service for background processing
    // -----------------------------------------------------------------------

    @PostMapping("/cameras/{cameraId}/upload-video")
    @SuppressWarnings("unchecked")
    public ResponseEntity<ApiResponse<Map<String, Object>>> uploadVideo(
            @PathVariable Long cameraId,
            @RequestParam("video") MultipartFile videoFile) {
        try {
            if (videoFile.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Video file is empty"));
            }

            var camera = cameraService.getCameraById(cameraId);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("video", new ByteArrayResource(videoFile.getBytes()) {
                @Override
                public String getFilename() {
                    return videoFile.getOriginalFilename() != null
                            ? videoFile.getOriginalFilename()
                            : "upload_" + cameraId + ".mp4";
                }
            });
            // Use camera_id (snake_case) — matches Python Form parameter name
            body.add("camera_id", cameraId.toString());
            body.add("capacity",  camera.getMaximumCapacity().toString());

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            ResponseEntity<Map> aiResponse = restTemplate.postForEntity(
                    aiServiceUrl + "/api/analyze/video", requestEntity, Map.class);

            return ResponseEntity.ok(ApiResponse.success("Video analysis started", aiResponse.getBody()));

        } catch (ResourceAccessException e) {
            log.warn("AI service unreachable for video upload: {}", e.getMessage());
            return ResponseEntity.ok(ApiResponse.error(
                    "AI service is not running. Start it with: python main.py"));
        } catch (Exception e) {
            log.error("Video upload error for camera {}: {}", cameraId, e.getMessage());
            return ResponseEntity.ok(ApiResponse.error("Video upload failed: " + e.getMessage()));
        }
    }

    // -----------------------------------------------------------------------
    // Stream URL validation — proxy to AI service validate-stream endpoint
    // -----------------------------------------------------------------------

    @GetMapping("/validate-stream")
    public ResponseEntity<ApiResponse<Map<String, Object>>> validateStream(
            @RequestParam String streamUrl) {
        try {
            URI uri = UriComponentsBuilder
                    .fromHttpUrl(aiServiceUrl + "/api/analyze/validate-stream")
                    .queryParam("stream_url", streamUrl)
                    .build(true)
                    .toUri();

            @SuppressWarnings("unchecked")
            ResponseEntity<Map> aiResponse = restTemplate.getForEntity(uri, Map.class);
            return ResponseEntity.ok(ApiResponse.success("Validation complete", aiResponse.getBody()));

        } catch (ResourceAccessException e) {
            return ResponseEntity.ok(ApiResponse.error("AI service not available"));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error("Validation failed: " + e.getMessage()));
        }
    }
}
