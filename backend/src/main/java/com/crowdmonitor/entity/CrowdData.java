package com.crowdmonitor.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "crowd_data", indexes = {
    @Index(name = "idx_camera_recorded", columnList = "camera_id, recorded_at"),
    @Index(name = "idx_recorded_at", columnList = "recorded_at"),
    @Index(name = "idx_crowd_level", columnList = "crowd_level")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CrowdData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "camera_id", nullable = false)
    private Camera camera;

    @Column(name = "people_count", nullable = false)
    private Integer peopleCount = 0;

    @Column(name = "occupancy_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal occupancyPercentage = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "crowd_level", nullable = false)
    private CrowdLevel crowdLevel = CrowdLevel.LOW;

    /**
     * ROOT CAUSE FIX for "Data truncation: Data too long for column 'frame_data'":
     *
     * Hibernate's default column mapping for `@Lob private byte[] field` on MySQL
     * is BLOB (64 KB max) — NOT the LONGBLOB (4 GB max) that schema.sql originally
     * declared. Because spring.jpa.hibernate.ddl-auto=update is enabled, Hibernate
     * re-validates/alters the column to its own default mapping on every startup,
     * silently downgrading frame_data/heatmap_data back to BLOB regardless of what
     * the SQL script created. A single annotated JPEG frame (a few hundred KB to a
     * few MB) then exceeds 64 KB and MySQL throws Data truncation, which the
     * controller's generic catch(Exception) turns into an HTTP 400.
     *
     * columnDefinition = "LONGBLOB" makes the explicit type win over Hibernate's
     * default for byte[], so it now matches (and will keep matching, even after
     * future ddl-auto=update runs) what schema.sql declares.
     */
    @Lob
    @Column(name = "frame_data", columnDefinition = "LONGBLOB")
    private byte[] frameData;

    @Lob
    @Column(name = "heatmap_data", columnDefinition = "LONGBLOB")
    private byte[] heatmapData;

    @Column(name = "recorded_at")
    private LocalDateTime recordedAt = LocalDateTime.now();

    @PrePersist
    public void prePersist() {
        this.recordedAt = LocalDateTime.now();
    }

    public enum CrowdLevel {
        LOW, MEDIUM, HIGH, CRITICAL, OVERCROWDED
    }
}
