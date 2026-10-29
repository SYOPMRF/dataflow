package com.dataflow.backend.service;

import com.dataflow.backend.entity.PendingFileDeletion;
import com.dataflow.backend.repository.PendingFileDeletionRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PendingFileDeletionServiceTest {

        @Mock
        private PendingFileDeletionRepository repository;

        @Mock
        private FileStorageService fileStorageService;

        @Mock
        private PendingFileDeletionPersistenceService persistenceService;

        @InjectMocks
        private PendingFileDeletionService pendingFileDeletionService;

        private PendingFileDeletion createClaimedPending(Long id, String storedName) {
                PendingFileDeletion pending = new PendingFileDeletion();
                pending.setId(id);
                pending.setStoredName(storedName);
                pending.setClaimToken(UUID.randomUUID());
                pending.setClaimedUntil(LocalDateTime.now().plusMinutes(5));
                return pending;
        }

        @Test
        void processOneShouldDeleteFileAndRemovePendingEntryWhenSuccessful() {
                PendingFileDeletion pending = createClaimedPending(1L, "stored-file.csv");

                when(persistenceService.removePending(1L, pending.getClaimToken()))
                                .thenReturn(true);

                pendingFileDeletionService.processOne(pending);

                verify(fileStorageService).delete("stored-file.csv");
                verify(persistenceService)
                                .removePending(1L, pending.getClaimToken());
                verify(persistenceService, never())
                                .recordFailure(anyLong(), any(UUID.class), any(RuntimeException.class));
        }

        @Test
        void processOneShouldRecordFailureWhenDeletionFails() {
                PendingFileDeletion pending = createClaimedPending(2L, "stored-file.csv");

                doThrow(new RuntimeException("Storage unavailable"))
                                .when(fileStorageService)
                                .delete("stored-file.csv");

                when(persistenceService.recordFailure(
                                eq(2L),
                                eq(pending.getClaimToken()),
                                any(RuntimeException.class)))
                                .thenReturn(true);

                assertDoesNotThrow(
                                () -> pendingFileDeletionService.processOne(pending));

                verify(fileStorageService).delete("stored-file.csv");
                verify(persistenceService).recordFailure(
                                eq(2L),
                                eq(pending.getClaimToken()),
                                any(RuntimeException.class));
                verify(persistenceService, never())
                                .removePending(anyLong(), any(UUID.class));
        }

        @Test
        void processPendingDeletionsShouldProcessClaimedBatch() {
                PendingFileDeletion first = createClaimedPending(3L, "older-eligible.csv");
                first.setAttempts(2);

                PendingFileDeletion second = createClaimedPending(4L, "newer-eligible.csv");

                when(persistenceService.claimBatch(anyInt()))
                                .thenReturn(List.of(first, second));
                when(persistenceService.removePending(
                                eq(3L), eq(first.getClaimToken())))
                                .thenReturn(true);
                when(persistenceService.removePending(
                                eq(4L), eq(second.getClaimToken())))
                                .thenReturn(true);

                pendingFileDeletionService.processPendingDeletions();

                verify(persistenceService).claimBatch(50);
                verify(fileStorageService).delete("older-eligible.csv");
                verify(fileStorageService).delete("newer-eligible.csv");
                verify(persistenceService)
                                .removePending(3L, first.getClaimToken());
                verify(persistenceService)
                                .removePending(4L, second.getClaimToken());
        }

        @Test
        void processPendingDeletionsShouldContinueWhenRecordingFailureThrows() {
                PendingFileDeletion failed = createClaimedPending(5L, "failed-file.csv");
                PendingFileDeletion successful = createClaimedPending(6L, "successful-file.csv");

                when(persistenceService.claimBatch(anyInt()))
                                .thenReturn(List.of(failed, successful));

                doThrow(new RuntimeException("Storage unavailable"))
                                .when(fileStorageService)
                                .delete("failed-file.csv");

                when(persistenceService.recordFailure(
                                eq(5L),
                                eq(failed.getClaimToken()),
                                any(RuntimeException.class)))
                                .thenThrow(new RuntimeException("Database unavailable"));

                when(persistenceService.removePending(
                                eq(6L), eq(successful.getClaimToken())))
                                .thenReturn(true);

                assertDoesNotThrow(
                                () -> pendingFileDeletionService.processPendingDeletions());

                verify(fileStorageService).delete("failed-file.csv");
                verify(fileStorageService).delete("successful-file.csv");
                verify(persistenceService).recordFailure(
                                eq(5L),
                                eq(failed.getClaimToken()),
                                any(RuntimeException.class));
                verify(persistenceService)
                                .removePending(6L, successful.getClaimToken());
        }

        @Test
        void processOneShouldSkipTaskWithoutClaimToken() {
                PendingFileDeletion pending = new PendingFileDeletion();
                pending.setId(7L);
                pending.setStoredName("unclaimed-file.csv");

                pendingFileDeletionService.processOne(pending);

                verify(fileStorageService, never()).delete(any());
                verify(persistenceService, never())
                                .removePending(anyLong(), any(UUID.class));
                verify(persistenceService, never())
                                .recordFailure(
                                                anyLong(),
                                                any(UUID.class),
                                                any(RuntimeException.class));
        }

        @Test
        void processOneShouldKeepTaskWhenClaimIsNoLongerValid() {
                PendingFileDeletion pending = createClaimedPending(
                                8L, "stored-file.csv");

                when(persistenceService.removePending(
                                8L, pending.getClaimToken()))
                                .thenReturn(false);

                assertDoesNotThrow(
                                () -> pendingFileDeletionService.processOne(pending));

                verify(fileStorageService).delete("stored-file.csv");
                verify(persistenceService)
                                .removePending(8L, pending.getClaimToken());
                verify(persistenceService, never())
                                .recordFailure(
                                                anyLong(),
                                                any(UUID.class),
                                                any(RuntimeException.class));
        }
}
