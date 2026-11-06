package com.dataflow.backend.service;

import com.dataflow.backend.enums.StorageProvider;
import org.springframework.web.multipart.MultipartFile;

public interface FileStorage {

    void store(String storedName, MultipartFile file);

    byte[] read(String storedName);

    void delete(String storedName);

    StorageProvider getProvider();
}