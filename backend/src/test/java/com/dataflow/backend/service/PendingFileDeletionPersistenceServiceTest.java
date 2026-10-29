package com.dataflow.backend.service;

import com.dataflow.backend.entity.PendingFileDeletion;
import com.dataflow.backend.repository.PendingFileDeletionRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PendingFileDeletionPersistenceServiceTest {

        @Mock
        private PendingFileDeletionRepository repository;

        @InjectMocks
        private PendingFileDeletionPersistenceService persistenceService;

        private PendingFileDeletion createClaimedPending(Long id) {
                PendingFileDeletion pending = new PendingFileDeletion();
                pending.setId(id);
                pending.setStoredName("stored-file.csv");
                pending.setAttempts(0);
                pending.setClaimToken(UUID.randomUUID());
                pending.setClaimedUntil(LocalDateTime.now().plusMinutes(5));
                return pending;
        }

        @Test
        void claimBatchShouldAssignTokenAndLeaseToAvailableTasks() {
                PendingFileDeletion pending = new PendingFileDeletion();
                pending.setId(1L);
                pending.setStoredName("stored-file.csv");

                when(repository.findAvailableForClaim(
                                any(LocalDateTime.class), eq(10)))
                                .thenReturn(List.of(pending));

                List<PendingFileDeletion> result = persistenceService.claimBatch(10);

                assertEquals(1, result.size());
                assertNotNull(pending.getClaimToken());
                assertNotNull(pending.getClaimedUntil());
                assertTrue(pending.getClaimedUntil().isAfter(LocalDateTime.now()));

                verify(repository).saveAllAndFlush(List.of(pending));
        }

        @Test
        void recordFailureShouldIncrementAttemptsAndSaveErrorMessage() {
                PendingFileDeletion pending = createClaimedPending(1L);
                pending.setAttempts(2);

                when(repository.findByIdForUpdate(1L))
                                .thenReturn(Optional.of(pending));

                LocalDateTime before = LocalDateTime.now();

                boolean recorded = persistenceService.recordFailure(
                                1L,
                                pending.getClaimToken(),
                                new RuntimeException("Storage unavailable"));

                LocalDateTime after = LocalDateTime.now();

                assertTrue(recorded);
                assertEquals(3, pending.getAttempts());
                assertEquals("Storage unavailable", pending.getLastError());
                assertNotNull(pending.getNextAttemptAt());
                assertTrue(
                                !pending.getNextAttemptAt().isBefore(before.plusMinutes(4))
                                                && !pending.getNextAttemptAt().isAfter(after.plusMinutes(4)),
                                "El tercer intento debe programarse aproximadamente cuatro minutos después");

                assertEquals(null, pending.getClaimToken());
                assertEquals(null, pending.getClaimedUntil());

                verify(repository).findByIdForUpdate(1L);
                verify(repository).save(pending);
        }

        @Test
        void recordFailureShouldUseExceptionClassWhenMessageIsNull() {
                PendingFileDeletion pending = createClaimedPending(2L);

                when(repository.findByIdForUpdate(2L))
                                .thenReturn(Optional.of(pending));

                boolean recorded = persistenceService.recordFailure(
                                2L,
                                pending.getClaimToken(),
                                new RuntimeException((String) null));

                assertTrue(recorded);
                assertEquals("RuntimeException", pending.getLastError());
                assertEquals(1, pending.getAttempts());

                verify(repository).save(pending);
        }

        @Test
        void recordFailureShouldTruncateErrorMessageTo1000Characters() {
                PendingFileDeletion pending = createClaimedPending(3L);

                when(repository.findByIdForUpdate(3L))
                                .thenReturn(Optional.of(pending));

                String longMessage = "x".repeat(1200);

                boolean recorded = persistenceService.recordFailure(
                                3L,
                                pending.getClaimToken(),
                                new RuntimeException(longMessage));

                assertTrue(recorded);
                assertEquals(1000, pending.getLastError().length());
                assertEquals("x".repeat(1000), pending.getLastError());

                verify(repository).save(pending);
        }

        @Test
        void recordFailureShouldNotSaveWhenPendingEntryDoesNotExist() {
                UUID token = UUID.randomUUID();

                when(repository.findByIdForUpdate(4L))
                                .thenReturn(Optional.empty());

                boolean recorded = persistenceService.recordFailure(
                                4L,
                                token,
                                new RuntimeException("Storage unavailable"));

                assertFalse(recorded);
                verify(repository).findByIdForUpdate(4L);
                verify(repository, never()).save(any(PendingFileDeletion.class));
        }

        @Test
        void recordFailureShouldRejectAnInvalidClaimToken() {
                PendingFileDeletion pending = createClaimedPending(5L);
                UUID originalToken = pending.getClaimToken();

                when(repository.findByIdForUpdate(5L))
                                .thenReturn(Optional.of(pending));

                boolean recorded = persistenceService.recordFailure(
                                5L,
                                UUID.randomUUID(),
                                new RuntimeException("Storage unavailable"));

                assertFalse(recorded);
                assertEquals(0, pending.getAttempts());
                assertEquals(originalToken, pending.getClaimToken());

                verify(repository, never()).save(any(PendingFileDeletion.class));
        }

        @Test
        void recordFailureShouldRejectAnExpiredClaim() {
                PendingFileDeletion pending = createClaimedPending(6L);
                pending.setClaimedUntil(LocalDateTime.now().minusSeconds(1));

                when(repository.findByIdForUpdate(6L))
                                .thenReturn(Optional.of(pending));

                boolean recorded = persistenceService.recordFailure(
                                6L,
                                pending.getClaimToken(),
                                new RuntimeException("Storage unavailable"));

                assertFalse(recorded);
                assertEquals(0, pending.getAttempts());

                verify(repository, never()).save(any(PendingFileDeletion.class));
        }

        @Test
        void removePendingShouldDeleteOnlyWithAValidClaim() {
                PendingFileDeletion pending = createClaimedPending(7L);

                when(repository.findByIdForUpdate(7L))
                                .thenReturn(Optional.of(pending));

                boolean removed = persistenceService.removePending(
                                7L, pending.getClaimToken());

                assertTrue(removed);
                verify(repository).delete(pending);
        }

        @Test
        void removePendingShouldRejectAnInvalidClaimToken() {
                PendingFileDeletion pending = createClaimedPending(8L);

                when(repository.findByIdForUpdate(8L))
                                .thenReturn(Optional.of(pending));

                boolean removed = persistenceService.removePending(
                                8L, UUID.randomUUID());

                assertFalse(removed);
                verify(repository, never()).delete(any(PendingFileDeletion.class));
        }

        @Test
        void removePendingShouldRejectAnExpiredClaim() {
                PendingFileDeletion pending = createClaimedPending(10L);
                UUID originalToken = pending.getClaimToken();
                pending.setClaimedUntil(LocalDateTime.now().minusSeconds(1));

                when(repository.findByIdForUpdate(10L))
                                .thenReturn(Optional.of(pending));

                boolean removed = persistenceService.removePending(
                                10L, originalToken);

                assertFalse(removed);
                assertEquals(originalToken, pending.getClaimToken());

                verify(repository).findByIdForUpdate(10L);
                verify(repository, never()).delete(any(PendingFileDeletion.class));
        }

        @Test
        void recordFailureShouldCapRetryDelayAt60Minutes() {
                PendingFileDeletion pending = createClaimedPending(9L);
                pending.setAttempts(6);

                when(repository.findByIdForUpdate(9L))
                                .thenReturn(Optional.of(pending));

                LocalDateTime before = LocalDateTime.now();

                boolean recorded = persistenceService.recordFailure(
                                9L,
                                pending.getClaimToken(),
                                new RuntimeException("Storage unavailable"));

                LocalDateTime after = LocalDateTime.now();

                assertTrue(recorded);
                assertEquals(7, pending.getAttempts());
                assertNotNull(pending.getNextAttemptAt());
                assertTrue(
                                !pending.getNextAttemptAt().isBefore(before.plusMinutes(60))
                                                && !pending.getNextAttemptAt().isAfter(after.plusMinutes(60)),
                                "El séptimo fallo debe programarse aproximadamente 60 minutos después");

                verify(repository).save(pending);
        }
}
