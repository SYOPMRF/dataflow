package com.dataflow.backend.service;

import com.dataflow.backend.entity.File;
import com.dataflow.backend.entity.User;
import com.dataflow.backend.enums.FileStatus;
import com.dataflow.backend.enums.StorageProvider;
import com.dataflow.backend.exception.ResourceNotFoundException;
import com.dataflow.backend.repository.AnalysisResultRepository;
import com.dataflow.backend.repository.DataQualityIssueRepository;
import com.dataflow.backend.repository.FileColumnRepository;
import com.dataflow.backend.repository.FileRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileServiceTest {

        @Mock
        private FileRepository fileRepository;

        @Mock
        private FileStorageService fileStorageService;

        @Mock
        private AnalysisResultRepository analysisResultRepository;

        @Mock
        private FileColumnRepository fileColumnRepository;

        @Mock
        private DataQualityIssueRepository dataQualityIssueRepository;

        @Mock
        private PendingFileDeletionService pendingFileDeletionService;

        @InjectMocks
        private FileService fileService;

        @Test
        void createFileShouldDeletePhysicalFileWhenDatabaseSaveFails() {

                User user = new User();
                user.setId(1L);
                user.setEmail("test@dataflow.com");

                RuntimeException databaseException = new RuntimeException("Database error");

                when(fileRepository.save(any(File.class)))
                                .thenThrow(databaseException);

                RuntimeException exception = assertThrows(
                                RuntimeException.class,
                                () -> fileService.createFile(
                                                user,
                                                "test.csv",
                                                "stored-file.csv",
                                                "text/csv",
                                                100L,
                                                StorageProvider.LOCAL));

                verify(fileStorageService).delete(
                                "stored-file.csv",
                                StorageProvider.LOCAL);

                verify(fileRepository)
                                .save(any(File.class));

                verifyNoMoreInteractions(fileStorageService);

                assertSame(databaseException, exception);
        }

        @Test
        void createFileShouldNotDeletePhysicalFileWhenDatabaseSaveSucceeds() {

                User user = new User();
                user.setId(1L);
                user.setEmail("test@dataflow.com");

                File savedFile = new File();
                savedFile.setId(1L);
                savedFile.setUser(user);
                savedFile.setOriginalName("test.csv");
                savedFile.setStoredName("stored-file.csv");
                savedFile.setFileType("text/csv");
                savedFile.setFileSize(100L);
                savedFile.setStatus(FileStatus.UPLOADED);
                savedFile.setStorageProvider(StorageProvider.LOCAL);

                when(fileRepository.save(any(File.class)))
                                .thenReturn(savedFile);

                File result = fileService.createFile(
                                user,
                                "test.csv",
                                "stored-file.csv",
                                "text/csv",
                                100L,
                                StorageProvider.LOCAL);

                ArgumentCaptor<File> fileCaptor = ArgumentCaptor.forClass(File.class);

                verify(fileRepository).save(fileCaptor.capture());

                assertEquals(
                                StorageProvider.LOCAL,
                                fileCaptor.getValue().getStorageProvider());
                assertSame(savedFile, result);
                verifyNoInteractions(fileStorageService);
        }

        @Test
        void deleteFileShouldDeleteRelatedRecordsWithoutDeletingPhysicalFileBeforeCommit() {
                User user = new User();
                user.setId(1L);
                user.setEmail("test@dataflow.com");

                File file = new File();
                file.setId(8L);
                file.setUser(user);
                file.setStoredName("stored-file.csv");
                file.setStorageProvider(StorageProvider.LOCAL);

                when(fileRepository.findByIdAndUser(8L, user))
                                .thenReturn(Optional.of(file));

                try {
                        TransactionSynchronizationManager.initSynchronization();

                        fileService.deleteFile(8L, user);

                        verify(pendingFileDeletionService).enqueue(
                                        "stored-file.csv",
                                        StorageProvider.LOCAL);
                        verify(dataQualityIssueRepository).deleteAllByFileId(8L);
                        verify(fileColumnRepository).deleteAllByFileId(8L);
                        verify(analysisResultRepository).deleteAllByFileId(8L);
                        verify(fileRepository).delete(file);

                        // El procesamiento debe esperar hasta después del commit.
                        verify(pendingFileDeletionService, never()).processPendingDeletions();
                        verifyNoInteractions(fileStorageService);

                } finally {
                        TransactionSynchronizationManager.clearSynchronization();
                }
        }

        @Test
        void deleteFileShouldProcessPendingDeletionsAfterCommit() {
                User user = new User();
                user.setId(1L);
                user.setEmail("test@dataflow.com");

                File file = new File();
                file.setId(8L);
                file.setUser(user);
                file.setStoredName("stored-file.csv");
                file.setStorageProvider(StorageProvider.LOCAL);

                when(fileRepository.findByIdAndUser(8L, user))
                                .thenReturn(Optional.of(file));

                try {
                        TransactionSynchronizationManager.initSynchronization();

                        fileService.deleteFile(8L, user);

                        verifyNoInteractions(fileStorageService);

                        TransactionSynchronization synchronization = TransactionSynchronizationManager
                                        .getSynchronizations().get(0);

                        synchronization.afterCommit();

                        verify(pendingFileDeletionService).enqueue(
                                        "stored-file.csv",
                                        StorageProvider.LOCAL);
                        verify(pendingFileDeletionService).processPendingDeletions();
                        verifyNoInteractions(fileStorageService);

                } finally {
                        TransactionSynchronizationManager.clearSynchronization();
                }
        }

        @Test
        void deleteFileShouldNotPropagateProcessingFailureAfterCommit() {
                User user = new User();
                user.setId(1L);
                user.setEmail("test@dataflow.com");

                File file = new File();
                file.setId(8L);
                file.setUser(user);
                file.setStoredName("stored-file.csv");
                file.setStorageProvider(StorageProvider.LOCAL);

                when(fileRepository.findByIdAndUser(8L, user))
                                .thenReturn(Optional.of(file));

                doThrow(new RuntimeException("Storage unavailable"))
                                .when(pendingFileDeletionService)
                                .processPendingDeletions();

                try {
                        TransactionSynchronizationManager.initSynchronization();

                        fileService.deleteFile(8L, user);

                        TransactionSynchronization synchronization = TransactionSynchronizationManager
                                        .getSynchronizations().get(0);

                        assertDoesNotThrow(synchronization::afterCommit);

                        verify(pendingFileDeletionService).enqueue(
                                        "stored-file.csv",
                                        StorageProvider.LOCAL);
                        verify(pendingFileDeletionService).processPendingDeletions();
                        verifyNoInteractions(fileStorageService);

                } finally {
                        TransactionSynchronizationManager.clearSynchronization();
                }
        }

        @Test
        void deleteFileShouldNotDeleteAnythingWhenFileDoesNotBelongToUser() {
                User user = new User();
                user.setId(1L);
                user.setEmail("test@dataflow.com");

                when(fileRepository.findByIdAndUser(8L, user))
                                .thenReturn(Optional.empty());

                assertThrows(
                                ResourceNotFoundException.class,
                                () -> fileService.deleteFile(8L, user));

                verify(fileRepository).findByIdAndUser(8L, user);
                verifyNoInteractions(
                                dataQualityIssueRepository,
                                fileColumnRepository,
                                analysisResultRepository,
                                fileStorageService,
                                pendingFileDeletionService);

                verify(fileRepository, never()).delete(any(File.class));
        }

        @Test
        void createFileShouldPersistS3StorageProvider() {
                User user = new User();
                user.setId(1L);

                File savedFile = new File();
                savedFile.setId(1L);
                savedFile.setStorageProvider(StorageProvider.S3);

                when(fileRepository.save(any(File.class)))
                                .thenReturn(savedFile);

                File result = fileService.createFile(
                                user,
                                "test.csv",
                                "stored-file.csv",
                                "text/csv",
                                100L,
                                StorageProvider.S3);

                ArgumentCaptor<File> fileCaptor = ArgumentCaptor.forClass(File.class);

                verify(fileRepository).save(fileCaptor.capture());

                assertEquals(
                                StorageProvider.S3,
                                fileCaptor.getValue().getStorageProvider());

                assertSame(savedFile, result);
                verifyNoInteractions(fileStorageService);
        }

        @Test
        void deleteFileShouldEnqueueDeletionWithS3Provider() {
                User user = new User();
                user.setId(1L);

                File file = new File();
                file.setId(8L);
                file.setUser(user);
                file.setStoredName("stored-file.csv");
                file.setStorageProvider(StorageProvider.S3);

                when(fileRepository.findByIdAndUser(8L, user))
                                .thenReturn(Optional.of(file));

                try {
                        TransactionSynchronizationManager.initSynchronization();

                        fileService.deleteFile(8L, user);

                        verify(pendingFileDeletionService).enqueue(
                                        "stored-file.csv",
                                        StorageProvider.S3);

                        verify(fileRepository).delete(file);

                        verify(pendingFileDeletionService, never())
                                        .processPendingDeletions();

                } finally {
                        TransactionSynchronizationManager.clearSynchronization();
                }
        }
}
