package com.dataflow.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PendingFileDeletionScheduler {

    private static final Logger logger = LoggerFactory.getLogger(PendingFileDeletionScheduler.class);

    private final PendingFileDeletionService pendingFileDeletionService;

    public PendingFileDeletionScheduler(
            PendingFileDeletionService pendingFileDeletionService) {
        this.pendingFileDeletionService = pendingFileDeletionService;
    }

    @Scheduled(fixedDelayString = "${file.cleanup.fixed-delay-ms:60000}", initialDelayString = "${file.cleanup.initial-delay-ms:60000}")
    public void retryPendingDeletions() {
        try {
            pendingFileDeletionService.processPendingDeletions();
        } catch (RuntimeException exception) {
            logger.error(
                    "Unexpected error while processing pending file deletions",
                    exception);
        }
    }
}
