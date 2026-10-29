package com.dataflow.backend.service;

import com.dataflow.backend.entity.File;
import com.dataflow.backend.entity.User;
import com.dataflow.backend.enums.FileStatus;
import com.dataflow.backend.repository.FileRepository;
import com.dataflow.backend.repository.UserRepository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

import com.dataflow.backend.entity.PendingFileDeletion;
import com.dataflow.backend.repository.PendingFileDeletionRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;

import java.util.UUID;

import static org.mockito.Mockito.doThrow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
                "spring.datasource.url=jdbc:postgresql://localhost:5432/dataflow_test",
                "spring.datasource.username=poysmon",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.jpa.show-sql=false",
                "file.storage.location=target/test-uploads",
                "jwt.secret=DataFlowTestSecretKeyMustBeAtLeast32BytesLong!",
                "jwt.expiration-ms=3600000"
})
class PendingFileDeletionIntegrationTest {

        @Autowired
        private PendingFileDeletionService pendingFileDeletionService;

        @Autowired
        private PendingFileDeletionRepository repository;

        @Autowired
        private PendingFileDeletionPersistenceService persistenceService;

        @MockitoBean
        private FileStorageService fileStorageService;

        @Autowired
        private FileService fileService;

        @Autowired
        private FileRepository fileRepository;

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private PlatformTransactionManager transactionManager;

        @Test
        void enqueueShouldPersistPendingDeletionAndAvoidDuplicates() {
                String storedName = UUID.randomUUID() + "-pending-test.csv";

                pendingFileDeletionService.enqueue(storedName);

                var pending = repository.findByStoredName(storedName);

                assertTrue(pending.isPresent());
                assertEquals(storedName, pending.orElseThrow().getStoredName());
                assertEquals(0, pending.orElseThrow().getAttempts());

                pendingFileDeletionService.enqueue(storedName);

                assertEquals(
                                1,
                                repository.findAll().stream()
                                                .filter(task -> task.getStoredName().equals(storedName))
                                                .count());
        }

        @Test
        void recordFailureShouldPersistRetryInformation() {
                String storedName = UUID.randomUUID() + "-retry-test.csv";
                pendingFileDeletionService.enqueue(storedName);

                PendingFileDeletion pending = repository.findByStoredName(storedName)
                                .orElseThrow();

                PendingFileDeletion claimed = persistenceService.claimBatch(10).stream()
                                .filter(task -> task.getId().equals(pending.getId()))
                                .findFirst()
                                .orElseThrow(() -> new AssertionError(
                                                "La tarea debería estar disponible para reclamación"));

                LocalDateTime beforeFailure = LocalDateTime.now();

                boolean recorded = persistenceService.recordFailure(
                                claimed.getId(),
                                claimed.getClaimToken(),
                                new RuntimeException("Simulated storage failure"));

                assertTrue(recorded, "La reclamación vigente debería permitir registrar el fallo");

                PendingFileDeletion updated = repository.findByStoredName(storedName)
                                .orElseThrow();

                assertEquals(1, updated.getAttempts());
                assertEquals("Simulated storage failure", updated.getLastError());
                assertNotNull(updated.getNextAttemptAt());
                assertTrue(
                                !updated.getNextAttemptAt().isBefore(beforeFailure.plusMinutes(1)),
                                "The next attempt should be scheduled at least one minute later");
                assertEquals(null, updated.getClaimToken());
                assertEquals(null, updated.getClaimedUntil());
        }

        @Test
        void processPendingDeletionsShouldKeepTaskWhenPhysicalDeletionFails() {
                String storedName = UUID.randomUUID() + "-physical-failure.csv";

                pendingFileDeletionService.enqueue(storedName);

                PendingFileDeletion pending = repository.findByStoredName(storedName)
                                .orElseThrow();

                doThrow(new RuntimeException("Simulated disk failure"))
                                .when(fileStorageService)
                                .delete(storedName);

                pendingFileDeletionService.processPendingDeletions();

                PendingFileDeletion persisted = repository.findByStoredName(storedName)
                                .orElseThrow();

                assertEquals(1, persisted.getAttempts());
                assertEquals("Simulated disk failure", persisted.getLastError());
                assertTrue(
                                persisted.getNextAttemptAt().isAfter(LocalDateTime.now()));
        }

        @Test
        void deleteFileShouldRollbackMetadataAndPendingDeletionTogether() {
                User user = new User();
                user.setEmail("rollback-" + UUID.randomUUID() + "@dataflow.test");
                user.setPasswordHash("test-password");
                user = userRepository.saveAndFlush(user);

                File file = new File();
                file.setUser(user);
                file.setOriginalName("rollback-test.csv");
                file.setStoredName(UUID.randomUUID() + "-rollback-test.csv");
                file.setFileType("text/csv");
                file.setFileSize(100L);
                file.setStatus(FileStatus.UPLOADED);
                file = fileRepository.saveAndFlush(file);

                Long fileId = file.getId();
                String storedName = file.getStoredName();
                User persistedUser = user;

                TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

                assertThrows(RuntimeException.class, () -> transactionTemplate.executeWithoutResult(status -> {
                        fileService.deleteFile(fileId, persistedUser);
                        throw new RuntimeException("Force transaction rollback");
                }));

                assertTrue(fileRepository.findById(fileId).isPresent());
                assertTrue(repository.findByStoredName(storedName).isEmpty());

                verify(fileStorageService, never()).delete(storedName);
        }

        @Test
        void claimBatchShouldReclaimTaskWhenLeaseExpires() {
                String storedName = UUID.randomUUID() + "-expired-lease.csv";
                pendingFileDeletionService.enqueue(storedName);

                PendingFileDeletion original = repository.findByStoredName(storedName)
                                .orElseThrow();

                PendingFileDeletion firstClaim = persistenceService.claimBatch(10).stream()
                                .filter(task -> task.getId().equals(original.getId()))
                                .findFirst()
                                .orElseThrow(() -> new AssertionError(
                                                "La tarea debería reclamarse por primera vez"));

                UUID originalToken = firstClaim.getClaimToken();
                assertNotNull(originalToken);
                assertTrue(firstClaim.getClaimedUntil().isAfter(LocalDateTime.now()));

                // Simular que el trabajador dejó de responder y su concesión venció.
                PendingFileDeletion persisted = repository.findById(original.getId())
                                .orElseThrow();
                persisted.setClaimedUntil(LocalDateTime.now().minusSeconds(1));
                repository.saveAndFlush(persisted);

                PendingFileDeletion secondClaim = persistenceService.claimBatch(10).stream()
                                .filter(task -> task.getId().equals(original.getId()))
                                .findFirst()
                                .orElseThrow(() -> new AssertionError(
                                                "La tarea con concesión vencida debería volver a reclamarse"));

                assertNotNull(secondClaim.getClaimToken());
                assertTrue(!originalToken.equals(secondClaim.getClaimToken()));
                assertTrue(secondClaim.getClaimedUntil().isAfter(LocalDateTime.now()));
        }

        @Test
        void claimBatchShouldNotReclaimTaskWhileLeaseIsActive() {
                String storedName = UUID.randomUUID() + "-active-lease.csv";
                pendingFileDeletionService.enqueue(storedName);

                PendingFileDeletion original = repository.findByStoredName(storedName)
                                .orElseThrow();

                PendingFileDeletion firstClaim = persistenceService.claimBatch(10).stream()
                                .filter(task -> task.getId().equals(original.getId()))
                                .findFirst()
                                .orElseThrow(() -> new AssertionError(
                                                "La tarea debería reclamarse por primera vez"));

                UUID originalToken = firstClaim.getClaimToken();

                assertNotNull(originalToken);
                assertTrue(firstClaim.getClaimedUntil().isAfter(LocalDateTime.now()));

                var secondBatch = persistenceService.claimBatch(10);

                assertTrue(
                                secondBatch.stream().noneMatch(task -> task.getId().equals(original.getId())),
                                "Una tarea con una concesión vigente no debería reclamarse de nuevo");

                PendingFileDeletion persisted = repository.findById(original.getId())
                                .orElseThrow();

                assertEquals(originalToken, persisted.getClaimToken());
        }

}
