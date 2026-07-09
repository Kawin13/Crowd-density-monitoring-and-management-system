package com.crowdmonitor.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "alerts", indexes = {
    @Index(name = "idx_camera_alert",    columnList = "camera_id, created_at"),
    @Index(name = "idx_alert_type",      columnList = "alert_type"),
    @Index(name = "idx_is_acknowledged", columnList = "acknowledged")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "camera_id", nullable = false)
    private Camera camera;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "crowd_data_id")
    private CrowdData crowdData;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false)
    private AlertType alertType;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String message;

    @Column(name = "people_count", nullable = false)
    private Integer peopleCount;

    @Column(name = "occupancy_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal occupancyPercentage;

    // Renamed from isAcknowledged to acknowledged — DB column preserved via @Column
    @Column(name = "is_acknowledged")
    private Boolean acknowledged = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "acknowledged_by")
    private User acknowledgedBy;

    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public enum AlertType {
        LOW, MEDIUM, HIGH, CRITICAL, OVERCROWDED
    }
}
