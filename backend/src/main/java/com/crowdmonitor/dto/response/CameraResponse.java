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
public class CameraResponse {
    private Long id;
    private String cameraName;
    private String cameraType;
    private String locationName;
    private Integer maximumCapacity;
    private String streamUrl;
    private String status;
    private String description;
    private Integer currentPeopleCount;
    private Double currentOccupancy;
    private String currentCrowdLevel;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
