package com.crowdmonitor.service;

import com.crowdmonitor.entity.Alert;
import com.crowdmonitor.entity.CrowdData;
import com.crowdmonitor.entity.Report;
import com.crowdmonitor.entity.User;
import com.crowdmonitor.repository.AlertRepository;
import com.crowdmonitor.repository.CameraRepository;
import com.crowdmonitor.repository.CrowdDataRepository;
import com.crowdmonitor.repository.ReportRepository;
import com.crowdmonitor.repository.UserRepository;

// iText PDF imports — explicit, NO wildcard to avoid Font ambiguity
import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.FontFactory;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;

// Apache POI Excel imports — explicit, NO wildcard to avoid Font ambiguity
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {

    private final CrowdDataRepository crowdDataRepository;
    private final AlertRepository alertRepository;
    private final CameraRepository cameraRepository;
    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // -----------------------------------------------------------------------
    // PDF Report
    // -----------------------------------------------------------------------
    @Transactional
    public byte[] generatePdfReport(Long cameraId, LocalDateTime start, LocalDateTime end) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, baos);
            document.open();

            // Title — use fully-qualified iText Font to avoid any ambiguity
            com.itextpdf.text.Font titleFont =
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, BaseColor.DARK_GRAY);
            Paragraph title = new Paragraph("Crowd Density Monitoring Report", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);
            document.add(new Paragraph(" "));

            com.itextpdf.text.Font infoFont =
                    FontFactory.getFont(FontFactory.HELVETICA, 11, BaseColor.BLACK);
            document.add(new Paragraph(
                    "Report Period: " + start.format(FORMATTER) + " to " + end.format(FORMATTER),
                    infoFont));
            document.add(new Paragraph(
                    "Generated: " + LocalDateTime.now().format(FORMATTER), infoFont));
            document.add(new Paragraph(" "));

            // Camera info block
            if (cameraId != null) {
                final com.itextpdf.text.Font capturedInfoFont = infoFont;
                cameraRepository.findById(cameraId).ifPresent(cam -> {
                    try {
                        com.itextpdf.text.Font boldFont =
                                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, BaseColor.BLACK);
                        document.add(new Paragraph("Camera: " + cam.getCameraName(), boldFont));
                        document.add(new Paragraph("Location: " + cam.getLocationName(), capturedInfoFont));
                        document.add(new Paragraph("Maximum Capacity: " + cam.getMaximumCapacity(), capturedInfoFont));
                        document.add(new Paragraph(" "));
                    } catch (DocumentException e) {
                        log.error("PDF camera block error", e);
                    }
                });
            }

            // Crowd data table
            com.itextpdf.text.Font sectionFont =
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, BaseColor.DARK_GRAY);
            document.add(new Paragraph("Crowd Data Summary", sectionFont));
            document.add(new Paragraph(" "));

            PdfPTable dataTable = new PdfPTable(5);
            dataTable.setWidthPercentage(100);
            addPdfTableHeader(dataTable, "Time", "Camera", "People Count", "Occupancy %", "Crowd Level");

            List<CrowdData> dataList = cameraId != null
                    ? crowdDataRepository.findByCameraAndDateRange(cameraId, start, end)
                    : crowdDataRepository.findByDateRange(start, end);

            int rowCount = 0;
            for (CrowdData cd : dataList) {
                if (rowCount++ > 1000) break;
                addPdfTableRow(dataTable,
                        cd.getRecordedAt().format(FORMATTER),
                        cd.getCamera().getCameraName(),
                        String.valueOf(cd.getPeopleCount()),
                        cd.getOccupancyPercentage().toPlainString() + "%",
                        cd.getCrowdLevel().name());
            }
            document.add(dataTable);
            document.add(new Paragraph(" "));

            // Alerts table
            document.add(new Paragraph("Alert History", sectionFont));
            document.add(new Paragraph(" "));

            PdfPTable alertTable = new PdfPTable(4);
            alertTable.setWidthPercentage(100);
            addPdfTableHeader(alertTable, "Time", "Camera", "Alert Type", "Occupancy %");

            List<Alert> alerts = alertRepository.findByDateRange(start, end);
            int alertCount = 0;
            for (Alert alert : alerts) {
                if (alertCount++ > 500) break;
                addPdfTableRow(alertTable,
                        alert.getCreatedAt().format(FORMATTER),
                        alert.getCamera().getCameraName(),
                        alert.getAlertType().name(),
                        alert.getOccupancyPercentage().toPlainString() + "%");
            }
            document.add(alertTable);

            document.close();
            byte[] pdfBytes = baos.toByteArray();

            saveReportRecord(cameraId, start, end, Report.ReportFormat.PDF, "crowd_report.pdf");

            return pdfBytes;

        } catch (Exception e) {
            log.error("Failed to generate PDF report", e);
            throw new RuntimeException("PDF generation failed: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Excel Report
    // -----------------------------------------------------------------------
    @Transactional
    public byte[] generateExcelReport(Long cameraId, LocalDateTime start, LocalDateTime end) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {

            // --- Crowd Data sheet ---
            Sheet dataSheet = workbook.createSheet("Crowd Data");
            CellStyle headerStyle = createExcelHeaderStyle(workbook);

            String[] dataHeaders = {
                "ID", "Camera", "Location", "Capacity",
                "People Count", "Occupancy %", "Crowd Level", "Recorded At"
            };
            Row dataHeaderRow = dataSheet.createRow(0);
            for (int i = 0; i < dataHeaders.length; i++) {
                Cell cell = dataHeaderRow.createCell(i);
                cell.setCellValue(dataHeaders[i]);
                cell.setCellStyle(headerStyle);
            }

            List<CrowdData> dataList = cameraId != null
                    ? crowdDataRepository.findByCameraAndDateRange(cameraId, start, end)
                    : crowdDataRepository.findByDateRange(start, end);

            int rowNum = 1;
            for (CrowdData cd : dataList) {
                if (rowNum > 10000) break;
                Row row = dataSheet.createRow(rowNum++);
                row.createCell(0).setCellValue(cd.getId());
                row.createCell(1).setCellValue(cd.getCamera().getCameraName());
                row.createCell(2).setCellValue(cd.getCamera().getLocationName());
                row.createCell(3).setCellValue(cd.getCamera().getMaximumCapacity());
                row.createCell(4).setCellValue(cd.getPeopleCount());
                row.createCell(5).setCellValue(cd.getOccupancyPercentage().doubleValue());
                row.createCell(6).setCellValue(cd.getCrowdLevel().name());
                row.createCell(7).setCellValue(cd.getRecordedAt().format(FORMATTER));
            }
            for (int i = 0; i < dataHeaders.length; i++) {
                dataSheet.autoSizeColumn(i);
            }

            // --- Alerts sheet ---
            Sheet alertSheet = workbook.createSheet("Alerts");
            String[] alertHeaders = {
                "ID", "Camera", "Alert Type", "People Count",
                "Occupancy %", "Acknowledged", "Created At"
            };
            Row alertHeaderRow = alertSheet.createRow(0);
            for (int i = 0; i < alertHeaders.length; i++) {
                Cell cell = alertHeaderRow.createCell(i);
                cell.setCellValue(alertHeaders[i]);
                cell.setCellStyle(headerStyle);
            }

            List<Alert> alerts = alertRepository.findByDateRange(start, end);
            int aRowNum = 1;
            for (Alert a : alerts) {
                if (aRowNum > 5000) break;
                Row row = alertSheet.createRow(aRowNum++);
                row.createCell(0).setCellValue(a.getId());
                row.createCell(1).setCellValue(a.getCamera().getCameraName());
                row.createCell(2).setCellValue(a.getAlertType().name());
                row.createCell(3).setCellValue(a.getPeopleCount());
                row.createCell(4).setCellValue(a.getOccupancyPercentage().doubleValue());
                // fixed: was a.getIsAcknowledged() — field renamed to acknowledged
                row.createCell(5).setCellValue(a.getAcknowledged() ? "Yes" : "No");
                row.createCell(6).setCellValue(a.getCreatedAt().format(FORMATTER));
            }
            for (int i = 0; i < alertHeaders.length; i++) {
                alertSheet.autoSizeColumn(i);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            workbook.write(baos);
            byte[] excelBytes = baos.toByteArray();

            saveReportRecord(cameraId, start, end, Report.ReportFormat.EXCEL, "crowd_report.xlsx");

            return excelBytes;

        } catch (Exception e) {
            log.error("Failed to generate Excel report", e);
            throw new RuntimeException("Excel generation failed: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Report persistence — uses ONLY the existing "reports" table/columns.
    // -----------------------------------------------------------------------
    private void saveReportRecord(Long cameraId, LocalDateTime start, LocalDateTime end,
                                   Report.ReportFormat format, String fileName) {
        try {
            String username = SecurityContextHolder.getContext().getAuthentication() != null
                    ? SecurityContextHolder.getContext().getAuthentication().getName()
                    : null;
            User generatedBy = username != null
                    ? userRepository.findByUsername(username).orElse(null)
                    : null;

            String cameraLabel = cameraId != null
                    ? cameraRepository.findById(cameraId).map(c -> c.getCameraName()).orElse("Camera " + cameraId)
                    : "All Cameras";

            Report report = new Report();
            report.setReportName("Crowd Report - " + cameraLabel + " - " + start.format(FORMATTER));
            report.setReportType(Report.ReportType.CUSTOM);
            report.setFormat(format);
            report.setCameraId(cameraId);
            report.setStartDate(start);
            report.setEndDate(end);
            report.setFilePath(fileName);
            report.setGeneratedBy(generatedBy);

            Report saved = reportRepository.save(report);

            auditLogService.log("REPORT_GENERATED", "REPORT", saved.getId(),
                    "Generated " + format.name() + " report (" + report.getReportName() + ")");
        } catch (Exception e) {
            // A failure to record report metadata must never prevent the
            // already-generated PDF/Excel bytes from being returned to the user.
            log.error("Failed to save report record to database", e);
        }
    }

    // -----------------------------------------------------------------------
    // PDF helpers — named addPdfTableHeader/Row to avoid any naming collision
    // -----------------------------------------------------------------------
    private void addPdfTableHeader(PdfPTable table, String... headers) {
        // Fully-qualified to guarantee no ambiguity with POI Font
        com.itextpdf.text.Font headerFont =
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, BaseColor.WHITE);
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, headerFont));
            cell.setBackgroundColor(new BaseColor(52, 73, 94));
            cell.setPadding(6);
            table.addCell(cell);
        }
    }

    private void addPdfTableRow(PdfPTable table, String... values) {
        com.itextpdf.text.Font rowFont =
                FontFactory.getFont(FontFactory.HELVETICA, 9, BaseColor.BLACK);
        for (String v : values) {
            PdfPCell cell = new PdfPCell(new Phrase(v, rowFont));
            cell.setPadding(4);
            table.addCell(cell);
        }
    }

    // -----------------------------------------------------------------------
    // Excel helpers — uses org.apache.poi.ss.usermodel.Font (explicit import)
    // -----------------------------------------------------------------------
    private CellStyle createExcelHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        // Use the explicitly imported POI Font interface — no ambiguity
        org.apache.poi.ss.usermodel.Font poiFont = workbook.createFont();
        poiFont.setBold(true);
        style.setFont(poiFont);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }
}
