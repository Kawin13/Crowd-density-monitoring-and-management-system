package com.crowdmonitor.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "cameras")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Camera {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "camera_name", nullable = false, length = 200)
    private String cameraName;

    @Enumerated(EnumType.STRING)
    @Column(name = "camera_type", nullable = false)
    private CameraType cameraType;

    @Column(name = "location_name", nullable = false, length = 300)
    private String locationName;

    @Column(name = "maximum_capacity", nullable = false)
    private Integer maximumCapacity = 100;

    @Column(name = "stream_url", length = 1000)
    private String streamUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private CameraStatus status = CameraStatus.INACTIVE;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public enum CameraType {
        MOBILE, CCTV, VIDEO_UPLOAD
    }

    public enum CameraStatus {
        ACTIVE, INACTIVE, MONITORING, ERROR
    }
}
