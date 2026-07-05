package com.crowdmonitor.controller;

import com.crowdmonitor.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/pdf")
    public ResponseEntity<byte[]> generatePdf(
            @RequestParam(required = false) Long cameraId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {

        byte[] pdf = reportService.generatePdfReport(cameraId, start, end);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "crowd_report.pdf");
        return ResponseEntity.ok().headers(headers).body(pdf);
    }

    @GetMapping("/excel")
    public ResponseEntity<byte[]> generateExcel(
            @RequestParam(required = false) Long cameraId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {

        byte[] excel = reportService.generateExcelReport(cameraId, start, end);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDispositionFormData("attachment", "crowd_report.xlsx");
        return ResponseEntity.ok().headers(headers).body(excel);
    }
}
