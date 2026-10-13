package com.dataflow.backend.repository;

import com.dataflow.backend.entity.File;
import com.dataflow.backend.entity.User;
import com.dataflow.backend.enums.FileStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface FileRepository extends JpaRepository<File, Long> {

    List<File> findAllByUserOrderByCreatedAtDesc(User user);

    Optional<File> findByIdAndUser(Long id, User user);
    @Transactional
    @Modifying
    @Query("""
            UPDATE File f
            SET f.status = :processingStatus
            WHERE f.id = :fileId
              AND f.user = :user
              AND f.status IN :allowedStatuses
            """)
    int claimForProcessing(
            @Param("fileId") Long fileId,
            @Param("user") User user,
            @Param("processingStatus") FileStatus processingStatus,
            @Param("allowedStatuses") List<FileStatus> allowedStatuses
    );
    @Transactional
    @Modifying
    @Query("""
            UPDATE File f
            SET f.status = :failedStatus
            WHERE f.id = :fileId
              AND f.status = :processingStatus
            """)
    int markAsFailedIfProcessing(
            @Param("fileId") Long fileId,
            @Param("processingStatus") FileStatus processingStatus,
            @Param("failedStatus") FileStatus failedStatus
    );
}