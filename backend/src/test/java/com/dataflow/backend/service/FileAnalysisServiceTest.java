package com.dataflow.backend.service;

import com.dataflow.backend.dto.AnalysisResult;
import com.dataflow.backend.entity.File;
import com.dataflow.backend.entity.User;
import com.dataflow.backend.enums.FileStatus;
import com.dataflow.backend.repository.AnalysisResultRepository;
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
                List.of()
        );

        when(userRepository.findByEmail("test@dataflow.com"))
                .thenReturn(Optional.of(user));

        when(fileRepository.findByIdAndUser(1L, user))
                .thenReturn(Optional.of(file));

        when(fileStorageService.getFilePath("stored-test.csv"))
                .thenReturn(filePath);

        when(dataServiceClient.analyzeFile(
                any(byte[].class),
                eq("test.csv")
        )).thenReturn(analysisResult);

        Authentication authentication =
                new UsernamePasswordAuthenticationToken(
                        "test@dataflow.com",
                        null
                );

        SecurityContextHolder.getContext()
                .setAuthentication(authentication);

        fileAnalysisService.analyzeFile(1L);

        assertEquals(FileStatus.COMPLETED, file.getStatus());
        verify(fileRepository, atLeastOnce()).save(file);

        verify(analysisResultRepository).save(any());

        verify(dataServiceClient).analyzeFile(
                any(byte[].class),
                eq("test.csv")
        );

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

        when(fileStorageService.getFilePath("stored-test.csv"))
                .thenReturn(filePath);

        when(dataServiceClient.analyzeFile(
                any(byte[].class),
                eq("test.csv")
        )).thenThrow(new RuntimeException("Data service unavailable"));

        Authentication authentication =
                new UsernamePasswordAuthenticationToken(
                        "test@dataflow.com",
                        null
                );

        SecurityContextHolder.getContext()
                .setAuthentication(authentication);

        assertThrows(
                FileProcessingException.class,
                () -> fileAnalysisService.analyzeFile(1L)
        );

        assertEquals(FileStatus.FAILED, file.getStatus());

        verify(fileRepository, atLeastOnce()).save(file);

        verify(dataServiceClient).analyzeFile(
                any(byte[].class),
                eq("test.csv")
        );

        verifyNoInteractions(analysisResultRepository);
        verifyNoInteractions(fileColumnRepository);

        Files.deleteIfExists(filePath);
    }
}
