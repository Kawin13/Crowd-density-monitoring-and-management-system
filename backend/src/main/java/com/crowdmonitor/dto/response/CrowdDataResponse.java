package com.crowdmonitor.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CrowdDataResponse {
    private Long id;
    private Long cameraId;
    private String cameraName;
    private String locationName;
    private Integer maximumCapacity;
    private Integer peopleCount;
    private BigDecimal occupancyPercentage;
    private String crowdLevel;
    private String frameDataBase64;
    private String heatmapDataBase64;
    private LocalDateTime recordedAt;
}
