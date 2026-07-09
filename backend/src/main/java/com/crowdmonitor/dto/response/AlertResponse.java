package com.crowdmonitor.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AlertResponse {
    private Long id;
    private Long cameraId;
    private String cameraName;
    private String locationName;
    private String alertType;
    private String message;
    private Integer peopleCount;
    private Double occupancyPercentage;
    private Boolean isAcknowledged;
    private String acknowledgedBy;
    private LocalDateTime acknowledgedAt;
    private LocalDateTime createdAt;
}
