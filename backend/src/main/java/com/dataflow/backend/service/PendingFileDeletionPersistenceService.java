package com.dataflow.backend.service;

import com.dataflow.backend.entity.PendingFileDeletion;
import com.dataflow.backend.repository.PendingFileDeletionRepository;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PendingFileDeletionPersistenceService {

    private static final Logger logger = LoggerFactory.getLogger(PendingFileDeletionPersistenceService.class);

    private static final long INITIAL_RETRY_DELAY_MINUTES = 1;

    private static final long MAX_RETRY_DELAY_MINUTES = 60;

    private static final int MAX_ERROR_LENGTH = 1000;

    private static final Duration CLAIM_LEASE_DURATION = Duration.ofMinutes(5);

    private final PendingFileDeletionRepository repository;

    public PendingFileDeletionPersistenceService(
            PendingFileDeletionRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public List<PendingFileDeletion> claimBatch(int batchSize) {
        LocalDateTime now = LocalDateTime.now();

        List<PendingFileDeletion> pendingDeletions = repository.findAvailableForClaim(now, batchSize);

        for (PendingFileDeletion pending : pendingDeletions) {
            pending.setClaimToken(UUID.randomUUID());
            pending.setClaimedUntil(now.plus(CLAIM_LEASE_DURATION));
        }

        repository.saveAllAndFlush(pendingDeletions);

        return pendingDeletions;
    }

    @Transactional
    public boolean removePending(Long id, UUID claimToken) {
        return repository.findByIdForUpdate(id)
                .filter(pending -> hasValidClaim(pending, claimToken))
                .map(pending -> {
                    repository.delete(pending);
                    return true;
                })
                .orElse(false);
    }

    @Transactional
    public boolean recordFailure(
            Long id,
            UUID claimToken,
            RuntimeException exception) {

        return repository.findByIdForUpdate(id)
                .filter(pending -> hasValidClaim(pending, claimToken))
                .map(pending -> {
                    pending.setAttempts(pending.getAttempts() + 1);

                    long multiplier = 1L << Math.min(pending.getAttempts() - 1, 6);

                    long delayMinutes = Math.min(
                            INITIAL_RETRY_DELAY_MINUTES * multiplier,
                            MAX_RETRY_DELAY_MINUTES);

                    pending.setNextAttemptAt(
                            LocalDateTime.now().plusMinutes(delayMinutes));

                    String message = exception.getMessage();

                    if (message == null || message.isBlank()) {
                        message = exception.getClass().getSimpleName();
                    }

                    if (message.length() > MAX_ERROR_LENGTH) {
                        message = message.substring(0, MAX_ERROR_LENGTH);
                    }

                    pending.setLastError(message);
                    pending.setClaimToken(null);
                    pending.setClaimedUntil(null);

                    repository.save(pending);

                    logger.warn(
                            "Physical file cleanup attempt {} failed for stored name {}",
                            pending.getAttempts(),
                            pending.getStoredName(),
                            exception);

                    return true;
                })
                .orElse(false);
    }

    private boolean hasValidClaim(
            PendingFileDeletion pending,
            UUID claimToken) {

        return claimToken != null
                && Objects.equals(pending.getClaimToken(), claimToken)
                && pending.getClaimedUntil() != null
                && pending.getClaimedUntil().isAfter(LocalDateTime.now());
    }
}
