package com.crowdmonitor.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * Maps to the existing "reports" table defined in database/schema.sql.
 * No new columns or tables — this entity only exposes columns that were
 * already part of the schema (report_name, report_type, format, camera_id,
 * start_date, end_date, file_path, generated_by, generated_at).
 */
@Entity
@Table(name = "reports")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "report_name", nullable = false, length = 300)
    private String reportName;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false)
    private ReportType reportType = ReportType.CUSTOM;

    @Enumerated(EnumType.STRING)
    @Column(name = "format", nullable = false)
    private ReportFormat format;

    @Column(name = "camera_id")
    private Long cameraId;

    @Column(name = "start_date", nullable = false)
    private LocalDateTime startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDateTime endDate;

    @Column(name = "file_path", length = 1000)
    private String filePath;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "generated_by")
    private User generatedBy;

    @Column(name = "generated_at")
    private LocalDateTime generatedAt = LocalDateTime.now();

    @PrePersist
    public void prePersist() {
        this.generatedAt = LocalDateTime.now();
    }

    public enum ReportType { DAILY, WEEKLY, MONTHLY, CUSTOM }

    public enum ReportFormat { PDF, EXCEL }
}
