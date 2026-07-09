package com.crowdmonitor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
public class CrowdMonitoringApplication {

    public static void main(String[] args) {
        // TIMEZONE FIX: the JDBC URL declares serverTimezone=UTC
        // (spring.datasource.url), which tells MySQL Connector/J that any
        // LocalDateTime/Timestamp value it sends should be interpreted as
        // UTC wall-clock time. Every entity in this project (User, Camera,
        // Report, Alert, CrowdData, AuditLog, RefreshToken) stamps its
        // timestamp columns with LocalDateTime.now(), which uses the JVM's
        // DEFAULT time zone. If that default zone is not also UTC (e.g. the
        // host OS is set to IST or another local zone), every timestamp
        // written to the database ends up offset from the wall-clock time
        // it was actually recorded at, and reads back inconsistently with
        // what the frontend displays. Pinning the JVM default to UTC here,
        // once, before the Spring context starts, guarantees every
        // LocalDateTime.now() call across the whole application uses the
        // exact same time base as the database connection.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(CrowdMonitoringApplication.class, args);
    }
}
