package com.crowdmonitor.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DashboardResponse {
    private long totalCameras;
    private long activeCameras;
    private long monitoringCameras;
    private int totalPeopleCount;
    private double averageOccupancy;
    private long unacknowledgedAlerts;
    private Map<String, Long> crowdLevelCounts;
    private Map<String, Long> alertTypeCounts;
    private List<CameraResponse> recentCameraData;
    private List<AlertResponse> recentAlerts;
    private LocalDateTime generatedAt;
}
