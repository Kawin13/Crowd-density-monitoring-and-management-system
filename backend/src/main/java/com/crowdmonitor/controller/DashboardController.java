package com.crowdmonitor.controller;

import com.crowdmonitor.dto.response.ApiResponse;
import com.crowdmonitor.dto.response.DashboardResponse;
import com.crowdmonitor.service.DashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Slf4j
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    public ResponseEntity<ApiResponse<DashboardResponse>> getDashboard() {
        try {
            return ResponseEntity.ok(ApiResponse.success(dashboardService.getDashboard()));
        } catch (Exception e) {
            // Per requirement: the dashboard must NEVER return 500, and must
            // load successfully even if the database tables are empty or a
            // transient query failure occurs. Any unexpected exception here
            // is logged server-side and a safe, fully-zeroed DashboardResponse
            // is returned with 200 so the frontend renders an empty-but-valid
            // dashboard instead of an error screen.
            log.error("Failed to build dashboard response: {}", e.getMessage(), e);
            DashboardResponse safe = DashboardResponse.builder()
                    .totalCameras(0)
                    .activeCameras(0)
                    .monitoringCameras(0)
                    .totalPeopleCount(0)
                    .averageOccupancy(0.0)
                    .unacknowledgedAlerts(0)
                    .crowdLevelCounts(new HashMap<>())
                    .alertTypeCounts(new HashMap<>())
                    .recentCameraData(java.util.List.of())
                    .recentAlerts(java.util.List.of())
                    .generatedAt(LocalDateTime.now())
                    .build();
            return ResponseEntity.ok(ApiResponse.success("Dashboard data unavailable, showing defaults", safe));
        }
    }
}
