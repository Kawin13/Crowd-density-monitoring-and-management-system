package com.crowdmonitor.controller;

import com.crowdmonitor.dto.response.ApiResponse;
import com.crowdmonitor.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/daily")
    public ResponseEntity<ApiResponse<Map<String, Object>>> daily(
            @RequestParam(required = false) Long cameraId) {
        return ResponseEntity.ok(ApiResponse.success(analyticsService.getDailyAnalytics(cameraId)));
    }

    @GetMapping("/weekly")
    public ResponseEntity<ApiResponse<Map<String, Object>>> weekly(
            @RequestParam(required = false) Long cameraId) {
        return ResponseEntity.ok(ApiResponse.success(analyticsService.getWeeklyAnalytics(cameraId)));
    }

    @GetMapping("/monthly")
    public ResponseEntity<ApiResponse<Map<String, Object>>> monthly(
            @RequestParam(required = false) Long cameraId) {
        return ResponseEntity.ok(ApiResponse.success(analyticsService.getMonthlyAnalytics(cameraId)));
    }

    @GetMapping("/custom")
    public ResponseEntity<ApiResponse<Map<String, Object>>> custom(
            @RequestParam(required = false) Long cameraId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        return ResponseEntity.ok(ApiResponse.success(analyticsService.getCustomAnalytics(cameraId, start, end)));
    }

    @GetMapping("/peak-hours")
    public ResponseEntity<ApiResponse<Map<String, Object>>> peakHours(
            @RequestParam Long cameraId) {
        return ResponseEntity.ok(ApiResponse.success(analyticsService.getPeakHours(cameraId)));
    }
}
