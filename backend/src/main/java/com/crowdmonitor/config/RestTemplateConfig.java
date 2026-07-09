package com.crowdmonitor.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Provides a single, shared RestTemplate bean for all calls from Spring Boot
 * to the FastAPI AI service.
 *
 * WHY THIS EXISTS:
 * MonitoringController previously created a `new RestTemplate()` per call with
 * no timeout configured. If the AI service stalled (e.g. YOLO model still
 * warming up, or a stream URL that hangs on open), the Spring Boot request
 * thread would block indefinitely, eventually exhausting the Tomcat thread
 * pool under load. Connect/read timeouts here ensure a stuck AI-service call
 * fails fast with a clear error instead of hanging the whole backend.
 *
 * API VERSION NOTE:
 * This project pins spring-boot-starter-parent to 3.2.0 (see pom.xml).
 * RestTemplateBuilder.setConnectTimeout(Duration) / .setReadTimeout(Duration)
 * are the correct method names for that version. The unprefixed
 * .connectTimeout(Duration) / .readTimeout(Duration) variants were only
 * introduced in Spring Boot 3.4.0 (setConnectTimeout/setReadTimeout were
 * deprecated, not removed, at that point) — using them against a 3.2.0
 * parent POM causes "cannot find symbol" at compile time because that
 * method does not exist on this version's RestTemplateBuilder.class.
 * If this project is later upgraded to Spring Boot 3.4.0+, switch back to
 * the unprefixed connectTimeout/readTimeout names to avoid deprecation
 * warnings.
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(5))   // time to establish TCP connection
                .setReadTimeout(Duration.ofSeconds(30))     // time to wait for AI service response
                                                             // (YOLO inference + multi-frame stream
                                                             // sampling can legitimately take a few
                                                             // seconds, so this is generous but bounded)
                .build();
    }
}
