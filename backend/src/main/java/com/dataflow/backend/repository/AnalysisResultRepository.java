package com.dataflow.backend.repository;

import com.dataflow.backend.entity.AnalysisResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AnalysisResultRepository extends JpaRepository<AnalysisResultEntity, Long> {

    Optional<AnalysisResultEntity> findByFileId(Long fileId);
}