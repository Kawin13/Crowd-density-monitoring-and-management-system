package com.crowdmonitor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
public class CrowdMonitoringApplication {

    public static void main(String[] args) {
        // TIMEZONE FIX: this deployment's "local system time" is India
        // Standard Time. Every entity (User, Camera, Report, Alert,
        // CrowdData, AuditLog, RefreshToken) stamps its timestamp columns
        // with LocalDateTime.now(), which uses the JVM's DEFAULT time zone.
        // Pinning that default to Asia/Kolkata here, once, before the
        // Spring context starts, guarantees every LocalDateTime.now() call
        // across the whole application writes the correct local wall-clock
        // time regardless of what zone the underlying host OS happens to be
        // set to. The JDBC URL's serverTimezone parameter (see
        // application.properties / docker-compose.yml) and
        // hibernate.jdbc.time_zone are set to the same zone so MySQL
        // Connector/J does not apply any additional conversion on top.
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
        SpringApplication.run(CrowdMonitoringApplication.class, args);
    }
}
