package com.crowdmonitor.service;

import com.crowdmonitor.repository.CrowdDataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final CrowdDataRepository crowdDataRepository;

    public Map<String, Object> getDailyAnalytics(Long cameraId) {
        LocalDateTime start = LocalDate.now().atStartOfDay();
        LocalDateTime end = LocalDate.now().atTime(LocalTime.MAX);
        return buildAnalytics(cameraId, start, end, "DAILY");
    }

    public Map<String, Object> getWeeklyAnalytics(Long cameraId) {
        LocalDateTime start = LocalDate.now().minusDays(7).atStartOfDay();
        LocalDateTime end = LocalDateTime.now();
        return buildAnalytics(cameraId, start, end, "WEEKLY");
    }

    public Map<String, Object> getMonthlyAnalytics(Long cameraId) {
        LocalDateTime start = LocalDate.now().minusDays(30).atStartOfDay();
        LocalDateTime end = LocalDateTime.now();
        return buildAnalytics(cameraId, start, end, "MONTHLY");
    }

    public Map<String, Object> getCustomAnalytics(Long cameraId, LocalDateTime start, LocalDateTime end) {
        return buildAnalytics(cameraId, start, end, "CUSTOM");
    }

    private Map<String, Object> buildAnalytics(Long cameraId, LocalDateTime start, LocalDateTime end, String type) {
        List<Object[]> hourly;
        List<Object[]> statsRow;
        List<Object[]> levelRows;

        // PERFORMANCE FIX (dashboard load time): this used to load every
        // matching CrowdData row into memory (with a JOIN FETCH to Camera
        // it never used) just to compute count/avg/max/min and a level
        // breakdown in Java. It now asks the database for those aggregates
        // directly, which is dramatically cheaper for busy deployments and
        // large date ranges (this endpoint is polled on every dashboard
        // load). This also fixes the "all cameras" hourly trend, which was
        // previously hard-coded to an empty list instead of being queried.
        if (cameraId != null) {
            hourly = crowdDataRepository.findHourlyAverageByCamera(cameraId, start, end);
            statsRow = crowdDataRepository.findStatsForCameraBetween(cameraId, start, end);
            levelRows = crowdDataRepository.countByCrowdLevelForCameraBetween(cameraId, start, end);
        } else {
            hourly = crowdDataRepository.findHourlyAverageAll(start, end);
            statsRow = crowdDataRepository.findStatsBetween(start, end);
            levelRows = crowdDataRepository.countByCrowdLevelBetween(start, end);
        }

        // Build hourly trend
        List<Map<String, Object>> hourlyTrend = new ArrayList<>();
        for (Object[] row : hourly) {
            Map<String, Object> point = new HashMap<>();
            point.put("hour", row[0]);
            point.put("avgCount", row[1]);
            hourlyTrend.add(point);
        }

        // Crowd level distribution
        Map<String, Long> levelDist = new LinkedHashMap<>();
        for (Object[] row : levelRows) {
            com.crowdmonitor.entity.CrowdData.CrowdLevel level =
                    (com.crowdmonitor.entity.CrowdData.CrowdLevel) row[0];
            Long count = (Long) row[1];
            levelDist.put(level != null ? level.name() : "LOW", count);
        }

        // Summary stats (COUNT, AVG, MAX, MIN in one row; all null if no rows matched)
        Object[] stats = statsRow.isEmpty() ? new Object[]{0L, null, null, null} : statsRow.get(0);
        long totalRecords = stats[0] != null ? ((Number) stats[0]).longValue() : 0L;
        double avgPeopleCount = stats[1] != null ? ((Number) stats[1]).doubleValue() : 0.0;
        int maxPeopleCount = stats[2] != null ? ((Number) stats[2]).intValue() : 0;
        int minPeopleCount = stats[3] != null ? ((Number) stats[3]).intValue() : 0;

        Map<String, Object> result = new HashMap<>();
        result.put("type", type);
        result.put("startDate", start);
        result.put("endDate", end);
        result.put("cameraId", cameraId);
        result.put("totalRecords", totalRecords);
        result.put("averagePeopleCount", avgPeopleCount);
        result.put("maxPeopleCount", maxPeopleCount);
        result.put("minPeopleCount", minPeopleCount);
        result.put("hourlyTrend", hourlyTrend);
        result.put("crowdLevelDistribution", levelDist);

        return result;
    }

    public Map<String, Object> getPeakHours(Long cameraId) {
        LocalDateTime start = LocalDate.now().minusDays(7).atStartOfDay();
        LocalDateTime end = LocalDateTime.now();
        List<Object[]> hourly = crowdDataRepository.findHourlyAverageByCamera(cameraId, start, end);

        List<Map<String, Object>> peaks = new ArrayList<>();
        for (Object[] row : hourly) {
            Map<String, Object> point = new HashMap<>();
            point.put("hour", row[0]);
            point.put("avgCount", row[1]);
            peaks.add(point);
        }

        peaks.sort((a, b) -> Double.compare(
                ((Number) b.get("avgCount")).doubleValue(),
                ((Number) a.get("avgCount")).doubleValue()
        ));

        Map<String, Object> result = new HashMap<>();
        result.put("cameraId", cameraId);
        result.put("peakHours", peaks.subList(0, Math.min(5, peaks.size())));
        return result;
    }
}
