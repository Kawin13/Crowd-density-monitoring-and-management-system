package com.crowdmonitor.service;

import com.crowdmonitor.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Periodically removes expired rows from the existing refresh_tokens table.
 * Uses only the existing schema — no new tables/columns.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenCleanupTask {

    private final RefreshTokenRepository refreshTokenRepository;

    // Runs once an hour; also runs a few seconds after startup.
    @Scheduled(initialDelay = 30_000, fixedRate = 3_600_000)
    @Transactional
    public void purgeExpiredTokens() {
        int removed = refreshTokenRepository.deleteAllExpired(LocalDateTime.now());
        if (removed > 0) {
            log.info("Refresh token cleanup: removed {} expired token(s).", removed);
        }
    }
}
