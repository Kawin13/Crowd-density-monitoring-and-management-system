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
 * This project now pins spring-boot-starter-parent to 3.5.16 (see pom.xml),
 * so the non-deprecated .connectTimeout(Duration)/.readTimeout(Duration)
 * names (introduced in 3.4.0) are used below instead of the older
 * setConnectTimeout/setReadTimeout, which are deprecated as of 3.4.0.
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .connectTimeout(Duration.ofSeconds(5))      // time to establish TCP connection
                .readTimeout(Duration.ofSeconds(30))        // time to wait for AI service response
                                                             // (YOLO inference + multi-frame stream
                                                             // sampling can legitimately take a few
                                                             // seconds, so this is generous but bounded)
                .build();
    }
}
