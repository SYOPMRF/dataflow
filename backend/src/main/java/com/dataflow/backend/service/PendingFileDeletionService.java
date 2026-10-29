package com.dataflow.backend.service;

import com.dataflow.backend.entity.PendingFileDeletion;
import com.dataflow.backend.repository.PendingFileDeletionRepository;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PendingFileDeletionService {

    private static final Logger logger = LoggerFactory.getLogger(PendingFileDeletionService.class);

    private static final int BATCH_SIZE = 50;

    private final PendingFileDeletionRepository repository;
    private final FileStorageService fileStorageService;
    private final PendingFileDeletionPersistenceService persistenceService;

    public PendingFileDeletionService(
            PendingFileDeletionRepository repository,
            FileStorageService fileStorageService,
            PendingFileDeletionPersistenceService persistenceService) {
        this.repository = repository;
        this.fileStorageService = fileStorageService;
        this.persistenceService = persistenceService;
    }

    @Transactional
    public void enqueue(String storedName) {
        repository.enqueueIfAbsent(storedName);
    }

    public void processPendingDeletions() {
        List<PendingFileDeletion> pendingDeletions = persistenceService.claimBatch(BATCH_SIZE);

        for (PendingFileDeletion pending : pendingDeletions) {
            try {
                processOne(pending);
            } catch (RuntimeException exception) {
                logger.error(
                        "Could not process pending file deletion task {}",
                        pending.getId(),
                        exception);
            }
        }
    }

    public void processOne(PendingFileDeletion pending) {
        Long id = pending.getId();
        UUID claimToken = pending.getClaimToken();

        if (claimToken == null) {
            logger.warn(
                    "Skipping pending file deletion task {} without a claim token",
                    id);
            return;
        }

        try {
            fileStorageService.delete(pending.getStoredName());
        } catch (RuntimeException deletionException) {
            try {
                boolean recorded = persistenceService.recordFailure(
                        id,
                        claimToken,
                        deletionException);

                if (!recorded) {
                    logger.warn(
                            "Could not record failure for task {} because its claim is no longer valid",
                            id);
                }
            } catch (RuntimeException persistenceException) {
                deletionException.addSuppressed(persistenceException);
                throw deletionException;
            }

            return;
        }

        boolean removed = persistenceService.removePending(id, claimToken);

        if (!removed) {
            logger.warn(
                    "Physical file was deleted, but task {} was not removed because its claim is no longer valid",
                    id);
        }
    }
}
