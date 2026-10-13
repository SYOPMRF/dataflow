package com.dataflow.backend.repository;

import com.dataflow.backend.entity.DataQualityIssueEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DataQualityIssueRepository extends JpaRepository<DataQualityIssueEntity, Long> {

    List<DataQualityIssueEntity> findAllByFileId(Long fileId);
}
