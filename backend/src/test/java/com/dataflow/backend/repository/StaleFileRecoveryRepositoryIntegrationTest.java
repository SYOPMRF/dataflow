package com.dataflow.backend.repository;

import com.dataflow.backend.entity.File;
import com.dataflow.backend.entity.User;
import com.dataflow.backend.enums.FileStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
class StaleFileRecoveryRepositoryIntegrationTest {

    @Autowired
    private FileRepository fileRepository;

    @Autowired
    private UserRepository userRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void shouldRecoverOnlyProcessingFilesOlderThanCutoff() {
        User user = new User();
        user.setEmail("recovery-" + UUID.randomUUID() + "@dataflow.test");
        user.setPasswordHash("test-password");
        user = userRepository.saveAndFlush(user);

        File staleFile = createFile(
                user, FileStatus.PROCESSING, LocalDateTime.now().minusMinutes(20));

        File recentFile = createFile(
                user, FileStatus.PROCESSING, LocalDateTime.now().minusMinutes(5));

        File fileWithoutStartTime = createFile(
                user, FileStatus.PROCESSING, null);

        File uploadedFile = createFile(
                user, FileStatus.UPLOADED, LocalDateTime.now().minusMinutes(20));

        int recovered = fileRepository.recoverStaleProcessingFiles(
                FileStatus.PROCESSING,
                FileStatus.FAILED,
                LocalDateTime.now().minusMinutes(10));

        entityManager.clear();

        assertEquals(1, recovered);

        File persistedStaleFile = fileRepository.findById(staleFile.getId()).orElseThrow();
        assertEquals(FileStatus.FAILED, persistedStaleFile.getStatus());
        assertNull(persistedStaleFile.getProcessingStartedAt());

        File persistedRecentFile = fileRepository.findById(recentFile.getId()).orElseThrow();
        assertEquals(FileStatus.PROCESSING, persistedRecentFile.getStatus());
        assertEquals(
                recentFile.getProcessingStartedAt(),
                persistedRecentFile.getProcessingStartedAt());

        File persistedFileWithoutStartTime = fileRepository.findById(fileWithoutStartTime.getId()).orElseThrow();
        assertEquals(
                FileStatus.PROCESSING,
                persistedFileWithoutStartTime.getStatus());
        assertNull(persistedFileWithoutStartTime.getProcessingStartedAt());

        File persistedUploadedFile = fileRepository.findById(uploadedFile.getId()).orElseThrow();
        assertEquals(FileStatus.UPLOADED, persistedUploadedFile.getStatus());
    }

    private File createFile(
            User user,
            FileStatus status,
            LocalDateTime processingStartedAt) {

        File file = new File();
        file.setUser(user);
        file.setOriginalName("recovery-test.csv");
        file.setStoredName(UUID.randomUUID() + "-recovery-test.csv");
        file.setFileType("text/csv");
        file.setFileSize(100L);
        file.setStatus(status);
        file.setProcessingStartedAt(
                processingStartedAt == null
                        ? null
                        : processingStartedAt.truncatedTo(ChronoUnit.MICROS));

        return fileRepository.saveAndFlush(file);
    }
}