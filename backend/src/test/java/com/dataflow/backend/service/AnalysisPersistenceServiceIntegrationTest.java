package com.dataflow.backend.service;

import com.dataflow.backend.dto.AnalysisResult;
import com.dataflow.backend.dto.ColumnAnalysis;
import com.dataflow.backend.dto.DataQualityIssue;
import com.dataflow.backend.entity.File;
import com.dataflow.backend.entity.User;
import com.dataflow.backend.enums.FileStatus;
import com.dataflow.backend.repository.AnalysisResultRepository;
import com.dataflow.backend.repository.DataQualityIssueRepository;
import com.dataflow.backend.repository.FileColumnRepository;
import com.dataflow.backend.repository.FileRepository;
import com.dataflow.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
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
class AnalysisPersistenceServiceIntegrationTest {

    @Autowired
    private AnalysisPersistenceService analysisPersistenceService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FileRepository fileRepository;

    @Autowired
    private AnalysisResultRepository analysisResultRepository;

    @Autowired
    private FileColumnRepository fileColumnRepository;

    @Autowired
    private DataQualityIssueRepository dataQualityIssueRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void shouldRollbackAllAnalysisDataWhenQualityIssueCannotBePersisted() {
        User user = new User();
        user.setEmail("atomicity-" + UUID.randomUUID() + "@dataflow.test");
        user.setPasswordHash("test-password");
        user = userRepository.saveAndFlush(user);

        File file = new File();
        file.setUser(user);
        file.setOriginalName("atomicity-test.csv");
        file.setStoredName(UUID.randomUUID() + "-atomicity-test.csv");
        file.setFileType("text/csv");
        file.setFileSize(100L);
        file.setStatus(FileStatus.PROCESSING);
        file = fileRepository.saveAndFlush(file);

        Long fileId = file.getId();

        ColumnAnalysis column = new ColumnAnalysis(
                "age",
                "integer",
                0,
                2,
                100.0,
                0,
                0,
                0.0,
                false,
                18,
                65,
                35.5,
                35.5,
                10.0,
                0,
                0
        );

        DataQualityIssue invalidIssue = new DataQualityIssue(
                null,
                "MISSING_VALUE",
                "HIGH",
                "Test issue that must fail persistence"
        );

        AnalysisResult result = new AnalysisResult(
                2,
                1,
                0,
                0,
                List.of(column),
                List.of(invalidIssue)
        );

        assertThrows(
                RuntimeException.class,
                () -> analysisPersistenceService.persistAnalysis(fileId, result)
        );

        assertTrue(analysisResultRepository.findByFileId(fileId).isEmpty());
        assertTrue(fileColumnRepository.findAllByFileId(fileId).isEmpty());
        assertTrue(dataQualityIssueRepository.findAllByFileId(fileId).isEmpty());

        File persistedFile = fileRepository.findById(fileId).orElseThrow();
        assertEquals(FileStatus.PROCESSING, persistedFile.getStatus());
        assertNull(persistedFile.getProcessedAt());
    }

        @Test
        void shouldAllowOnlyOneClaimForProcessing() {
        User user = new User();
        user.setEmail("claim-" + UUID.randomUUID() + "@dataflow.test");
        user.setPasswordHash("test-password");
        user = userRepository.saveAndFlush(user);

        File file = new File();
        file.setUser(user);
        file.setOriginalName("claim-test.csv");
        file.setStoredName(UUID.randomUUID() + "-claim-test.csv");
        file.setFileType("text/csv");
        file.setFileSize(100L);
        file.setStatus(FileStatus.UPLOADED);
        file = fileRepository.saveAndFlush(file);

        Long fileId = file.getId();

        int firstClaim = fileRepository.claimForProcessing(
                fileId,
                user,
                FileStatus.PROCESSING,
                List.of(FileStatus.UPLOADED, FileStatus.FAILED)
        );

        int secondClaim = fileRepository.claimForProcessing(
                fileId,
                user,
                FileStatus.PROCESSING,
                List.of(FileStatus.UPLOADED, FileStatus.FAILED)
        );

        entityManager.clear();

        File persistedFile = fileRepository.findById(fileId).orElseThrow();

        assertEquals(1, firstClaim);
        assertEquals(0, secondClaim);
        assertEquals(FileStatus.PROCESSING, persistedFile.getStatus());
        }

    @Test
    void shouldPersistAnalysisAndMarkFileAsCompleted() {
        User user = new User();
        user.setEmail("success-" + UUID.randomUUID() + "@dataflow.test");
        user.setPasswordHash("test-password");
        user = userRepository.saveAndFlush(user);

        File file = new File();
        file.setUser(user);
        file.setOriginalName("success-test.csv");
        file.setStoredName(UUID.randomUUID() + "-success-test.csv");
        file.setFileType("text/csv");
        file.setFileSize(100L);
        file.setStatus(FileStatus.PROCESSING);
        file = fileRepository.saveAndFlush(file);

        Long fileId = file.getId();

        ColumnAnalysis column = new ColumnAnalysis(
                "age",
                "integer",
                0,
                2,
                100.0,
                0,
                0,
                0.0,
                false,
                18,
                65,
                35.5,
                35.5,
                10.0,
                0,
                0
        );

        DataQualityIssue issue = new DataQualityIssue(
                "age",
                "OUTLIER",
                "LOW",
                "Value outside the expected range"
        );

        AnalysisResult result = new AnalysisResult(
                2,
                1,
                0,
                0,
                List.of(column),
                List.of(issue)
        );

        analysisPersistenceService.persistAnalysis(fileId, result);

        var persistedAnalysis =
                analysisResultRepository.findByFileId(fileId).orElseThrow();
        var persistedColumns = fileColumnRepository.findAllByFileId(fileId);
        var persistedIssues = dataQualityIssueRepository.findAllByFileId(fileId);
        File persistedFile = fileRepository.findById(fileId).orElseThrow();

        assertEquals(2, persistedAnalysis.getRowCount());
        assertEquals(1, persistedAnalysis.getColumnCount());
        assertEquals(1, persistedColumns.size());
        assertEquals("age", persistedColumns.get(0).getName());
        assertEquals(1, persistedIssues.size());
        assertEquals("OUTLIER", persistedIssues.get(0).getIssueType());
        assertEquals(FileStatus.COMPLETED, persistedFile.getStatus());
        assertTrue(persistedFile.getProcessedAt() != null);
    }
}
