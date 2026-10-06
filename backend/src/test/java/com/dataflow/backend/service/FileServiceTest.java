package com.dataflow.backend.service;

import com.dataflow.backend.entity.File;
import com.dataflow.backend.entity.User;
import com.dataflow.backend.enums.FileStatus;
import com.dataflow.backend.repository.FileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileServiceTest {

    @Mock
    private FileRepository fileRepository;

    @Mock
    private FileStorageService fileStorageService;

    @InjectMocks
    private FileService fileService;

    @Test
    void createFileShouldDeletePhysicalFileWhenDatabaseSaveFails() {

        User user = new User();
        user.setId(1L);
        user.setEmail("test@dataflow.com");

        RuntimeException databaseException =
                new RuntimeException("Database error");

        when(fileRepository.save(any(File.class)))
                .thenThrow(databaseException);

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> fileService.createFile(
                        user,
                        "test.csv",
                        "stored-file.csv",
                        "text/csv",
                        100L
                )
        );

        verify(fileStorageService)
                .delete("stored-file.csv");

        verify(fileRepository)
                .save(any(File.class));

        verifyNoMoreInteractions(fileStorageService);

        assert exception == databaseException;
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

        when(fileRepository.save(any(File.class)))
                .thenReturn(savedFile);

        File result = fileService.createFile(
                user,
                "test.csv",
                "stored-file.csv",
                "text/csv",
                100L
        );

        verify(fileRepository)
                .save(any(File.class));

        verifyNoInteractions(fileStorageService);

        assert result == savedFile;
    }
}