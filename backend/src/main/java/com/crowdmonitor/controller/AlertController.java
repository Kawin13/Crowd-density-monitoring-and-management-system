package com.crowdmonitor.controller;

import com.crowdmonitor.dto.response.AlertResponse;
import com.crowdmonitor.dto.response.ApiResponse;
import com.crowdmonitor.service.AlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
@Slf4j
public class AlertController {

    private final AlertService alertService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<AlertResponse>>> getAllAlerts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            return ResponseEntity.ok(ApiResponse.success(alertService.getAllAlerts(page, size)));
        } catch (Exception e) {
            // Per requirement: alerts page must NEVER fail. Any unexpected
            // error (DB issue, serialization edge case, etc.) is logged
            // server-side and an empty page is returned with 200 so the
            // frontend renders "no alerts found" instead of an error screen.
            log.error("Failed to fetch alerts page={} size={}: {}", page, size, e.getMessage(), e);
            Page<AlertResponse> empty = new PageImpl<>(List.of(), PageRequest.of(page, size), 0);
            return ResponseEntity.ok(ApiResponse.success("No alerts found", empty));
        }
    }

    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<AlertResponse>>> getActiveAlerts() {
        try {
            return ResponseEntity.ok(ApiResponse.success(alertService.getActiveAlerts()));
        } catch (Exception e) {
            log.error("Failed to fetch active alerts: {}", e.getMessage(), e);
            return ResponseEntity.ok(ApiResponse.success("No active alerts found", List.of()));
        }
    }

    @PutMapping("/{id}/acknowledge")
    public ResponseEntity<ApiResponse<AlertResponse>> acknowledge(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(ApiResponse.success("Alert acknowledged", alertService.acknowledgeAlert(id)));
        } catch (Exception e) {
            log.error("Failed to acknowledge alert {}: {}", id, e.getMessage(), e);
            return ResponseEntity.ok(ApiResponse.error("Could not acknowledge alert: " + e.getMessage()));
        }
    }

    @PutMapping("/acknowledge-all")
    public ResponseEntity<ApiResponse<Void>> acknowledgeAll() {
        try {
            alertService.acknowledgeAllAlerts();
            return ResponseEntity.ok(ApiResponse.success("All alerts acknowledged", null));
        } catch (Exception e) {
            log.error("Failed to acknowledge all alerts: {}", e.getMessage(), e);
            return ResponseEntity.ok(ApiResponse.error("Could not acknowledge all alerts"));
        }
    }

    @GetMapping("/count")
    public ResponseEntity<ApiResponse<Long>> countUnacknowledged() {
        try {
            return ResponseEntity.ok(ApiResponse.success(alertService.countUnacknowledgedAlerts()));
        } catch (Exception e) {
            log.error("Failed to count unacknowledged alerts: {}", e.getMessage(), e);
            return ResponseEntity.ok(ApiResponse.success(0L));
        }
    }
}
