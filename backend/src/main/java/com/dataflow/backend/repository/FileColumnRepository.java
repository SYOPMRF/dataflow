package com.dataflow.backend.repository;

import com.dataflow.backend.entity.FileColumn;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FileColumnRepository extends JpaRepository<FileColumn, Long> {

    List<FileColumn> findAllByFileId(Long fileId);
}