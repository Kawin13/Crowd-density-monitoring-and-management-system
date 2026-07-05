package com.crowdmonitor.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CameraRequest {
    @NotBlank
    private String cameraName;

    @NotBlank
    private String cameraType;

    @NotBlank
    private String locationName;

    @NotNull
    @Min(1)
    private Integer maximumCapacity;

    private String streamUrl;

    private String description;
}
