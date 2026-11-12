package com.dataflow.backend.service;

import com.dataflow.backend.dto.AnalysisResult;
import com.dataflow.backend.dto.ColumnAnalysis;
import com.dataflow.backend.dto.DataQualityIssue;
import com.dataflow.backend.dto.DuplicateAnalysis;
import com.dataflow.backend.dto.DistributionAnalysis;
import com.dataflow.backend.dto.ColumnDistribution;
import com.dataflow.backend.dto.DistributionBin;
import com.dataflow.backend.dto.DistributionCategory;
import com.dataflow.backend.dto.CorrelationAnalysis;
import com.dataflow.backend.dto.CandidateKey;
import com.dataflow.backend.dto.DuplicateGroup;
import com.dataflow.backend.dto.DuplicateValueExample;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
                                0,
                                29.75,
                                53.75,
                                24.0,
                                20.35,
                                22.7,
                                60.3,
                                62.65,
                                0,
                                0);

                DataQualityIssue invalidIssue = new DataQualityIssue(
                                null,
                                "MISSING_VALUE",
                                "HIGH",
                                "Test issue that must fail persistence");

                AnalysisResult result = new AnalysisResult(
                                2,
                                1,
                                0,
                                0,
                                List.of(column),
                                List.of(invalidIssue));

                assertThrows(
                                RuntimeException.class,
                                () -> analysisPersistenceService.persistAnalysis(fileId, result));

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
                                List.of(FileStatus.UPLOADED, FileStatus.FAILED));

                int secondClaim = fileRepository.claimForProcessing(
                                fileId,
                                user,
                                FileStatus.PROCESSING,
                                List.of(FileStatus.UPLOADED, FileStatus.FAILED));

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
                                0,
                                29.75,
                                53.75,
                                24.0,
                                20.35,
                                22.7,
                                60.3,
                                62.65,
                                0,
                                2);

                DataQualityIssue issue = new DataQualityIssue(
                                "age",
                                "OUTLIER",
                                "LOW",
                                "Value outside the expected range",
                                List.of("age", "salary"),
                                2);

                DuplicateAnalysis duplicateAnalysis = new DuplicateAnalysis(
                                List.of(new CandidateKey(
                                                List.of("id"),
                                                "SINGLE",
                                                2,
                                                2,
                                                100.0)),
                                List.of(new DuplicateGroup(
                                                List.of("name", "age"),
                                                1,
                                                1,
                                                2,
                                                List.of(new DuplicateValueExample(
                                                                List.of("John", 25),
                                                                2)))),
                                1,
                                false,
                                false,
                                false,
                                false);

                DistributionAnalysis distributionAnalysis = new DistributionAnalysis(
                                List.of(new ColumnDistribution(
                                                "age",
                                                "NUMERIC",
                                                2,
                                                0,
                                                2,
                                                List.of(
                                                                new DistributionBin(18.0, 40.0, 1, 50.0),
                                                                new DistributionBin(40.0, 65.0, 1, 50.0)),
                                                null,
                                                List.of(),
                                                false)));

                CorrelationAnalysis correlationAnalysis = new CorrelationAnalysis(
                                List.of("age", "salary"),
                                List.of(
                                                List.of(1.0, 0.95),
                                                List.of(0.95, 1.0)),
                                List.of(
                                                List.of(1.0, 0.9),
                                                List.of(0.9, 1.0)),
                                List.of(
                                                List.of(2, 2),
                                                List.of(2, 2)),
                                false);

                AnalysisResult result = new AnalysisResult(
                                2,
                                1,
                                0,
                                0,
                                List.of(column),
                                List.of(issue),
                                duplicateAnalysis,
                                distributionAnalysis,
                                correlationAnalysis);

                analysisPersistenceService.persistAnalysis(fileId, result);

                var persistedAnalysis = analysisResultRepository.findByFileId(fileId).orElseThrow();

                assertNotNull(persistedAnalysis.getDuplicateAnalysis());

                assertEquals(
                                1,
                                persistedAnalysis.getDuplicateAnalysis()
                                                .path("evaluatedCombinations").asInt());

                assertEquals(
                                "SINGLE",
                                persistedAnalysis.getDuplicateAnalysis()
                                                .path("candidateKeys").get(0)
                                                .path("keyType").asText());

                assertEquals(
                                2,
                                persistedAnalysis.getDuplicateAnalysis()
                                                .path("duplicateGroups").get(0)
                                                .path("examples").get(0)
                                                .path("frequency").asInt());

                assertEquals(
                                "John",
                                persistedAnalysis.getDuplicateAnalysis()
                                                .path("duplicateGroups").get(0)
                                                .path("examples").get(0)
                                                .path("values").get(0).asText());

                assertEquals(
                                25,
                                persistedAnalysis.getDuplicateAnalysis()
                                                .path("duplicateGroups").get(0)
                                                .path("examples").get(0)
                                                .path("values").get(1).asInt());

                assertNotNull(persistedAnalysis.getDistributionAnalysis());
                assertEquals(
                                "NUMERIC",
                                persistedAnalysis.getDistributionAnalysis()
                                                .path("columns").get(0)
                                                .path("distributionType").asText());
                assertEquals(
                                2,
                                persistedAnalysis.getDistributionAnalysis()
                                                .path("columns").get(0)
                                                .path("validCount").asInt());
                assertEquals(
                                50.0,
                                persistedAnalysis.getDistributionAnalysis()
                                                .path("columns").get(0)
                                                .path("bins").get(0)
                                                .path("percentage").asDouble());

                assertNotNull(persistedAnalysis.getCorrelationAnalysis());
                assertEquals(
                                0.95,
                                persistedAnalysis.getCorrelationAnalysis()
                                                .path("pearson").get(0).get(1).asDouble());
                assertEquals(
                                0.9,
                                persistedAnalysis.getCorrelationAnalysis()
                                                .path("spearman").get(0).get(1).asDouble());
                assertEquals(
                                2,
                                persistedAnalysis.getCorrelationAnalysis()
                                                .path("observationCounts").get(0).get(1).asInt());

                var persistedColumns = fileColumnRepository.findAllByFileId(fileId);
                var persistedIssues = dataQualityIssueRepository.findAllByFileId(fileId);
                File persistedFile = fileRepository.findById(fileId).orElseThrow();

                assertEquals(2, persistedAnalysis.getRowCount());
                assertEquals(1, persistedAnalysis.getColumnCount());
                assertEquals(1, persistedColumns.size());
                assertEquals("age", persistedColumns.get(0).getName());
                assertEquals("DFJSON1:18", persistedColumns.get(0).getMinValue());
                assertEquals("DFJSON1:65", persistedColumns.get(0).getMaxValue());
                assertEquals(Double.valueOf(29.75), persistedColumns.get(0).getQ1Value());
                assertEquals(Double.valueOf(53.75), persistedColumns.get(0).getQ3Value());
                assertEquals(Double.valueOf(24.0), persistedColumns.get(0).getIqrValue());
                assertEquals(Double.valueOf(20.35), persistedColumns.get(0).getP5Value());
                assertEquals(Double.valueOf(22.7), persistedColumns.get(0).getP10Value());
                assertEquals(Double.valueOf(60.3), persistedColumns.get(0).getP90Value());
                assertEquals(Double.valueOf(62.65), persistedColumns.get(0).getP95Value());
                assertEquals(Integer.valueOf(0), persistedColumns.get(0).getOutlierCount());
                assertEquals(Integer.valueOf(2), persistedColumns.get(0).getZScoreOutlierCount());
                assertEquals(1, persistedIssues.size());
                assertEquals("OUTLIER", persistedIssues.get(0).getIssueType());
                assertEquals(
                                List.of("age", "salary"),
                                persistedIssues.get(0).getRelatedColumns());

                assertEquals(2, persistedIssues.get(0).getAffectedRows());
                assertEquals(FileStatus.COMPLETED, persistedFile.getStatus());
                assertTrue(persistedFile.getProcessedAt() != null);
        }
}
