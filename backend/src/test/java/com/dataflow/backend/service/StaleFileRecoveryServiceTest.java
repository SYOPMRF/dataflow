package com.dataflow.backend.service;

import com.dataflow.backend.enums.FileStatus;
import com.dataflow.backend.repository.FileRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaleFileRecoveryServiceTest {

    @Mock
    private FileRepository fileRepository;

    @Test
    void shouldRecoverStaleFilesUsingConfiguredTimeout() {
        StaleFileRecoveryService recoveryService = new StaleFileRecoveryService(fileRepository, 10);

        LocalDateTime beforeExecution = LocalDateTime.now();

        when(fileRepository.recoverStaleProcessingFiles(
                eq(FileStatus.PROCESSING),
                eq(FileStatus.FAILED),
                any(LocalDateTime.class))).thenReturn(3);

        recoveryService.recoverStaleFiles();

        LocalDateTime afterExecution = LocalDateTime.now();

        ArgumentCaptor<LocalDateTime> cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);

        verify(fileRepository).recoverStaleProcessingFiles(
                eq(FileStatus.PROCESSING),
                eq(FileStatus.FAILED),
                cutoffCaptor.capture());

        LocalDateTime cutoff = cutoffCaptor.getValue();

        assertTrue(
                !cutoff.isBefore(beforeExecution.minusMinutes(10))
                        && !cutoff.isAfter(afterExecution.minusMinutes(10)));

        verifyNoMoreInteractions(fileRepository);
    }

    @Test
    void shouldRejectNonPositiveTimeout() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new StaleFileRecoveryService(fileRepository, 0));

        assertThrows(
                IllegalArgumentException.class,
                () -> new StaleFileRecoveryService(fileRepository, -1));
    }
}