package com.dataflow.backend.service;

import com.dataflow.backend.dto.AnalysisResult;
import com.dataflow.backend.dto.ColumnAnalysis;
import com.dataflow.backend.dto.DataQualityIssue;
import com.dataflow.backend.entity.AnalysisResultEntity;
import com.dataflow.backend.entity.FileColumn;
import com.dataflow.backend.entity.DataQualityIssueEntity;
import com.dataflow.backend.exception.ConflictException;
import com.dataflow.backend.exception.ResourceNotFoundException;
import com.dataflow.backend.entity.File;
import com.dataflow.backend.entity.User;
import com.dataflow.backend.enums.FileStatus;
import com.dataflow.backend.repository.AnalysisResultRepository;
import com.dataflow.backend.repository.DataQualityIssueRepository;
import com.dataflow.backend.repository.FileColumnRepository;
import com.dataflow.backend.repository.FileRepository;
import com.dataflow.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.junit.jupiter.api.AfterEach;
import com.dataflow.backend.exception.FileProcessingException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileAnalysisServiceTest {

        @Mock
        private FileRepository fileRepository;

        @Mock
        private FileStorageService fileStorageService;

        @Mock
        private DataServiceClient dataServiceClient;

        @Mock
        private AnalysisResultRepository analysisResultRepository;

        @Mock
        private FileColumnRepository fileColumnRepository;

        @Mock
        private UserRepository userRepository;

        @Mock
        private DataQualityIssueRepository dataQualityIssueRepository;

        @Mock
        private AnalysisPersistenceService analysisPersistenceService;

        @InjectMocks
        private FileAnalysisService fileAnalysisService;

        @AfterEach
        void clearSecurityContext() {
                SecurityContextHolder.clearContext();
        }

        @Test
        void processFileShouldCompleteAndPersistAnalysis() throws Exception {
                User user = new User();
                user.setId(1L);
                user.setEmail("test@dataflow.com");

                File file = new File();
                file.setId(1L);
                file.setUser(user);
                file.setOriginalName("test.csv");
                file.setStoredName("stored-test.csv");
                file.setFileType("text/csv");
                file.setFileSize(100L);
                file.setStatus(FileStatus.UPLOADED);

                Path filePath = Files.createTempFile("dataflow-test-", ".csv");
                Files.writeString(filePath, "name,age\nJohn,25\nJane,30\n");

                AnalysisResult analysisResult = new AnalysisResult(
                                2,
                                2,
                                0,
                                0,
                                List.of(),
                                List.of());

                when(userRepository.findByEmail("test@dataflow.com"))
                                .thenReturn(Optional.of(user));

                when(fileRepository.findByIdAndUser(1L, user))
                                .thenReturn(Optional.of(file));
                when(fileRepository.claimForProcessing(
                                eq(1L),
                                eq(user),
                                eq(FileStatus.PROCESSING),
                                eq(List.of(FileStatus.UPLOADED, FileStatus.FAILED)))).thenReturn(1);

                when(fileStorageService.getFilePath("stored-test.csv"))
                                .thenReturn(filePath);

                when(dataServiceClient.analyzeFile(
                                any(byte[].class),
                                eq("test.csv"))).thenReturn(analysisResult);

                Authentication authentication = new UsernamePasswordAuthenticationToken(
                                "test@dataflow.com",
                                null);

                SecurityContextHolder.getContext()
                                .setAuthentication(authentication);

                fileAnalysisService.analyzeFile(1L);

                verify(fileRepository).claimForProcessing(
                                eq(1L),
                                eq(user),
                                eq(FileStatus.PROCESSING),
                                eq(List.of(FileStatus.UPLOADED, FileStatus.FAILED)));

                verify(analysisPersistenceService)
                                .persistAnalysis(1L, analysisResult);

                verify(dataServiceClient).analyzeFile(
                                any(byte[].class),
                                eq("test.csv"));

                Files.deleteIfExists(filePath);
        }

        @Test
        void processFileShouldMarkFileAsFailedWhenDataServiceFails() throws Exception {
                User user = new User();
                user.setId(1L);
                user.setEmail("test@dataflow.com");

                File file = new File();
                file.setId(1L);
                file.setUser(user);
                file.setOriginalName("test.csv");
                file.setStoredName("stored-test.csv");
                file.setFileType("text/csv");
                file.setFileSize(100L);
                file.setStatus(FileStatus.UPLOADED);

                Path filePath = Files.createTempFile("dataflow-test-", ".csv");
                Files.writeString(filePath, "name,age\nJohn,25\nJane,30\n");

                when(userRepository.findByEmail("test@dataflow.com"))
                                .thenReturn(Optional.of(user));

                when(fileRepository.findByIdAndUser(1L, user))
                                .thenReturn(Optional.of(file));

                when(fileRepository.claimForProcessing(
                                eq(1L),
                                eq(user),
                                eq(FileStatus.PROCESSING),
                                eq(List.of(FileStatus.UPLOADED, FileStatus.FAILED)))).thenReturn(1);

                when(fileStorageService.getFilePath("stored-test.csv"))
                                .thenReturn(filePath);

                when(dataServiceClient.analyzeFile(
                                any(byte[].class),
                                eq("test.csv"))).thenThrow(new RuntimeException("Data service unavailable"));

                Authentication authentication = new UsernamePasswordAuthenticationToken(
                                "test@dataflow.com",
                                null);

                SecurityContextHolder.getContext()
                                .setAuthentication(authentication);

                assertThrows(
                                FileProcessingException.class,
                                () -> fileAnalysisService.analyzeFile(1L));

                verify(fileRepository).markAsFailedIfProcessing(
                                1L,
                                FileStatus.PROCESSING,
                                FileStatus.FAILED);

                verify(dataServiceClient).analyzeFile(
                                any(byte[].class),
                                eq("test.csv"));

                verifyNoInteractions(analysisResultRepository);
                verifyNoInteractions(fileColumnRepository);

                Files.deleteIfExists(filePath);
        }

        @Test
        void processFileShouldMarkFileAsFailedWhenPersistenceFails() throws Exception {
                User user = new User();
                user.setId(1L);
                user.setEmail("test@dataflow.com");

                File file = new File();
                file.setId(1L);
                file.setUser(user);
                file.setOriginalName("test.csv");
                file.setStoredName("stored-test.csv");
                file.setFileType("text/csv");
                file.setFileSize(100L);
                file.setStatus(FileStatus.UPLOADED);

                Path filePath = Files.createTempFile("dataflow-test-", ".csv");
                try {
                        Files.writeString(filePath, "name,age\nJohn,25\nJane,30\n");

                        AnalysisResult analysisResult = new AnalysisResult(
                                        2, 2, 0, 0, List.of(), List.of());

                        when(userRepository.findByEmail("test@dataflow.com"))
                                        .thenReturn(Optional.of(user));
                        when(fileRepository.findByIdAndUser(1L, user))
                                        .thenReturn(Optional.of(file));
                        when(fileRepository.claimForProcessing(
                                        eq(1L),
                                        eq(user),
                                        eq(FileStatus.PROCESSING),
                                        eq(List.of(FileStatus.UPLOADED, FileStatus.FAILED)))).thenReturn(1);
                        when(fileStorageService.getFilePath("stored-test.csv"))
                                        .thenReturn(filePath);
                        when(dataServiceClient.analyzeFile(any(byte[].class), eq("test.csv")))
                                        .thenReturn(analysisResult);
                        doThrow(new RuntimeException("Persistence failed"))
                                        .when(analysisPersistenceService)
                                        .persistAnalysis(1L, analysisResult);

                        SecurityContextHolder.getContext().setAuthentication(
                                        new UsernamePasswordAuthenticationToken(
                                                        "test@dataflow.com", null));

                        assertThrows(
                                        FileProcessingException.class,
                                        () -> fileAnalysisService.analyzeFile(1L));

                        verify(analysisPersistenceService).persistAnalysis(1L, analysisResult);
                        verify(fileRepository).markAsFailedIfProcessing(
                                        1L,
                                        FileStatus.PROCESSING,
                                        FileStatus.FAILED);
                } finally {
                        Files.deleteIfExists(filePath);
                }
        }

        @Test
        void processCompletedFileShouldThrowConflictException() {
                User user = new User();
                user.setId(1L);
                user.setEmail("test@dataflow.com");

                File file = new File();
                file.setId(1L);
                file.setUser(user);
                file.setStatus(FileStatus.COMPLETED);

                when(userRepository.findByEmail("test@dataflow.com"))
                                .thenReturn(Optional.of(user));
                when(fileRepository.findByIdAndUser(1L, user))
                                .thenReturn(Optional.of(file));
                when(fileRepository.claimForProcessing(
                                eq(1L),
                                eq(user),
                                eq(FileStatus.PROCESSING),
                                eq(List.of(FileStatus.UPLOADED, FileStatus.FAILED)))).thenReturn(0);

                SecurityContextHolder.getContext().setAuthentication(
                                new UsernamePasswordAuthenticationToken(
                                                "test@dataflow.com",
                                                null));

                ConflictException exception = assertThrows(
                                ConflictException.class,
                                () -> fileAnalysisService.analyzeFile(1L));

                assertEquals("File has already been processed", exception.getMessage());
                verifyNoInteractions(dataServiceClient);
                verifyNoInteractions(analysisPersistenceService);
        }

        @Test
        void processFileAlreadyBeingProcessedShouldThrowConflictException() {
                User user = new User();
                user.setId(1L);
                user.setEmail("test@dataflow.com");

                File file = new File();
                file.setId(1L);
                file.setUser(user);
                file.setStatus(FileStatus.PROCESSING);

                when(userRepository.findByEmail("test@dataflow.com"))
                                .thenReturn(Optional.of(user));
                when(fileRepository.findByIdAndUser(1L, user))
                                .thenReturn(Optional.of(file));
                when(fileRepository.claimForProcessing(
                                eq(1L),
                                eq(user),
                                eq(FileStatus.PROCESSING),
                                eq(List.of(FileStatus.UPLOADED, FileStatus.FAILED)))).thenReturn(0);

                SecurityContextHolder.getContext().setAuthentication(
                                new UsernamePasswordAuthenticationToken(
                                                "test@dataflow.com",
                                                null));

                ConflictException exception = assertThrows(
                                ConflictException.class,
                                () -> fileAnalysisService.analyzeFile(1L));

                assertEquals("File is already being processed", exception.getMessage());
                verifyNoInteractions(dataServiceClient);
                verifyNoInteractions(analysisPersistenceService);
        }

        @Test
        void getAnalysisShouldReturnPersistedSummaryAndColumnMetrics() {
                User user = new User();
                user.setId(1L);
                user.setEmail("test@dataflow.com");

                File file = new File();
                file.setId(1L);
                file.setUser(user);
                file.setOriginalName("test.csv");
                file.setStoredName("stored-test.csv");
                file.setFileType("text/csv");
                file.setFileSize(100L);
                file.setStatus(FileStatus.COMPLETED);

                AnalysisResultEntity analysisEntity = new AnalysisResultEntity();
                analysisEntity.setFile(file);
                analysisEntity.setRowCount(10);
                analysisEntity.setColumnCount(2);
                analysisEntity.setMissingCount(3);
                analysisEntity.setDuplicateCount(1);

                FileColumn column = new FileColumn();
                column.setFile(file);
                column.setName("age");
                column.setDataType("integer");
                column.setNullCount(2);
                column.setUniqueCount(8);
                column.setUniquePercentage(80.0);
                column.setDuplicateCount(1);
                column.setDuplicatedUniqueCount(1);
                column.setMissingPercentage(20.0);
                column.setCompletelyEmpty(false);
                column.setMinValue("18");
                column.setMaxValue("65");
                column.setMeanValue(35.5);
                column.setMedianValue(34.0);
                column.setStdDeviation(12.5);
                column.setZeroCount(0);
                column.setNegativeCount(0);

                when(userRepository.findByEmail("test@dataflow.com"))
                                .thenReturn(Optional.of(user));
                when(fileRepository.findByIdAndUser(1L, user))
                                .thenReturn(Optional.of(file));
                when(analysisResultRepository.findByFileId(1L))
                                .thenReturn(Optional.of(analysisEntity));
                when(fileColumnRepository.findAllByFileId(1L))
                                .thenReturn(List.of(column));
                when(dataQualityIssueRepository.findAllByFileId(1L))
                                .thenReturn(List.of());

                Authentication authentication = new UsernamePasswordAuthenticationToken(
                                "test@dataflow.com",
                                null);

                SecurityContextHolder.getContext()
                                .setAuthentication(authentication);

                AnalysisResult result = fileAnalysisService.getAnalysis(1L);

                assertEquals(10, result.rowCount());
                assertEquals(2, result.columnCount());
                assertEquals(3, result.missingCount());
                assertEquals(1, result.duplicateCount());
                assertEquals(1, result.columns().size());

                ColumnAnalysis resultColumn = result.columns().get(0);

                assertEquals("age", resultColumn.name());
                assertEquals("integer", resultColumn.dataType());
                assertEquals(2, resultColumn.nullCount());
                assertEquals(8, resultColumn.uniqueCount());
                assertEquals(80.0, resultColumn.uniquePercentage());
                assertEquals(1, resultColumn.duplicateCount());
                assertEquals(1, resultColumn.duplicatedUniqueCount());
                assertEquals(20.0, resultColumn.missingPercentage());
                assertEquals(false, resultColumn.isCompletelyEmpty());
                assertEquals("18", resultColumn.minValue());
                assertEquals("65", resultColumn.maxValue());
                assertEquals(35.5, resultColumn.meanValue());
                assertEquals(34.0, resultColumn.medianValue());
                assertEquals(12.5, resultColumn.stdDeviation());
                assertEquals(0, resultColumn.zeroCount());
                assertEquals(0, resultColumn.negativeCount());

                assertEquals(0, result.qualityIssues().size());
        }

        @Test
        void getAnalysisShouldReturnPersistedQualityIssues() {
                User user = new User();
                user.setId(1L);
                user.setEmail("test@dataflow.com");

                File file = new File();
                file.setId(1L);
                file.setUser(user);
                file.setStatus(FileStatus.COMPLETED);

                AnalysisResultEntity analysisEntity = new AnalysisResultEntity();
                analysisEntity.setFile(file);
                analysisEntity.setRowCount(10);
                analysisEntity.setColumnCount(2);
                analysisEntity.setMissingCount(2);
                analysisEntity.setDuplicateCount(0);

                DataQualityIssueEntity issue = new DataQualityIssueEntity();
                issue.setFile(file);
                issue.setColumnName("age");
                issue.setIssueType("MISSING_VALUES");
                issue.setSeverity("MEDIUM");
                issue.setMessage("La columna contiene valores faltantes.");

                when(userRepository.findByEmail("test@dataflow.com"))
                                .thenReturn(Optional.of(user));
                when(fileRepository.findByIdAndUser(1L, user))
                                .thenReturn(Optional.of(file));
                when(analysisResultRepository.findByFileId(1L))
                                .thenReturn(Optional.of(analysisEntity));
                when(fileColumnRepository.findAllByFileId(1L))
                                .thenReturn(List.of());
                when(dataQualityIssueRepository.findAllByFileId(1L))
                                .thenReturn(List.of(issue));

                Authentication authentication = new UsernamePasswordAuthenticationToken(
                                "test@dataflow.com",
                                null);

                SecurityContextHolder.getContext()
                                .setAuthentication(authentication);

                AnalysisResult result = fileAnalysisService.getAnalysis(1L);

                assertEquals(1, result.qualityIssues().size());

                DataQualityIssue resultIssue = result.qualityIssues().get(0);

                assertEquals("age", resultIssue.columnName());
                assertEquals("MISSING_VALUES", resultIssue.issueType());
                assertEquals("MEDIUM", resultIssue.severity());
                assertEquals(
                                "La columna contiene valores faltantes.",
                                resultIssue.message());
        }

        @Test
        void getAnalysisShouldRejectFileOwnedByAnotherUser() {
                User user = new User();
                user.setId(49L);
                user.setEmail("analysis-test@dataflow.com");

                when(userRepository.findByEmail("analysis-test@dataflow.com"))
                                .thenReturn(Optional.of(user));

                when(fileRepository.findByIdAndUser(6L, user))
                                .thenReturn(Optional.empty());

                Authentication authentication = new UsernamePasswordAuthenticationToken(
                                "analysis-test@dataflow.com",
                                null);

                SecurityContextHolder.getContext()
                                .setAuthentication(authentication);

                assertThrows(
                                ResourceNotFoundException.class,
                                () -> fileAnalysisService.getAnalysis(6L));

                verify(analysisResultRepository, never()).findByFileId(any());
                verify(fileColumnRepository, never()).findAllByFileId(any());
                verify(dataQualityIssueRepository, never()).findAllByFileId(any());
        }

        @Test
        void getAnalysisShouldThrowWhenAnalysisDoesNotExist() {
                User user = new User();
                user.setId(1L);
                user.setEmail("test@dataflow.com");

                File file = new File();
                file.setId(1L);
                file.setUser(user);
                file.setStatus(FileStatus.COMPLETED);

                when(userRepository.findByEmail("test@dataflow.com"))
                                .thenReturn(Optional.of(user));

                when(fileRepository.findByIdAndUser(1L, user))
                                .thenReturn(Optional.of(file));

                when(analysisResultRepository.findByFileId(1L))
                                .thenReturn(Optional.empty());

                Authentication authentication = new UsernamePasswordAuthenticationToken(
                                "test@dataflow.com",
                                null);

                SecurityContextHolder.getContext()
                                .setAuthentication(authentication);

                ResourceNotFoundException exception = assertThrows(
                                ResourceNotFoundException.class,
                                () -> fileAnalysisService.getAnalysis(1L));

                assertEquals("Analysis not found", exception.getMessage());

                verify(fileColumnRepository, never()).findAllByFileId(any());
                verify(dataQualityIssueRepository, never()).findAllByFileId(any());
        }
}
