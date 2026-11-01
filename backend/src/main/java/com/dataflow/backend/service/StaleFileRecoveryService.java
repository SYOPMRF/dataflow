package com.dataflow.backend.service;

import java.time.LocalDateTime;

import com.dataflow.backend.enums.FileStatus;
import com.dataflow.backend.repository.FileRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class StaleFileRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(StaleFileRecoveryService.class);

    private final FileRepository fileRepository;
    private final long timeoutMinutes;

    public StaleFileRecoveryService(
            FileRepository fileRepository,
            @Value("${file.processing.timeout-minutes:10}") long timeoutMinutes) {
        if (timeoutMinutes <= 0) {
            throw new IllegalArgumentException(
                    "File processing timeout must be greater than zero");
        }

        this.fileRepository = fileRepository;
        this.timeoutMinutes = timeoutMinutes;
    }

    @Scheduled(fixedDelayString = "${file.processing.recovery-interval-ms:60000}")
    public void recoverStaleFiles() {
        LocalDateTime cutoff = LocalDateTime.now()
                .minusMinutes(timeoutMinutes);

        int recoveredFiles = fileRepository.recoverStaleProcessingFiles(
                FileStatus.PROCESSING,
                FileStatus.FAILED,
                cutoff);

        if (recoveredFiles > 0) {
            log.warn(
                    "Recovered {} stale files stuck in PROCESSING status",
                    recoveredFiles);
        }
    }
}