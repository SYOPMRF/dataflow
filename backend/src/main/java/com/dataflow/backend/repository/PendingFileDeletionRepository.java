package com.dataflow.backend.repository;

import com.dataflow.backend.entity.PendingFileDeletion;

import jakarta.persistence.LockModeType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PendingFileDeletionRepository
                extends JpaRepository<PendingFileDeletion, Long> {

        Optional<PendingFileDeletion> findByStoredName(String storedName);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("SELECT p FROM PendingFileDeletion p WHERE p.id = :id")
        Optional<PendingFileDeletion> findByIdForUpdate(
                        @Param("id") Long id);

        @Query(value = """
                        SELECT *
                        FROM pending_file_deletions
                        WHERE next_attempt_at <= :now
                          AND (
                              claim_token IS NULL
                              OR claimed_until <= :now
                          )
                        ORDER BY next_attempt_at ASC, created_at ASC
                        LIMIT :batchSize
                        FOR UPDATE SKIP LOCKED
                        """, nativeQuery = true)
        List<PendingFileDeletion> findAvailableForClaim(
                        @Param("now") LocalDateTime now,
                        @Param("batchSize") int batchSize);

        @Modifying
        @Query(value = """
                        INSERT INTO pending_file_deletions (
                            stored_name,
                            created_at,
                            next_attempt_at,
                            attempts
                        )
                        VALUES (
                            :storedName,
                            CURRENT_TIMESTAMP,
                            CURRENT_TIMESTAMP,
                            0
                        )
                        ON CONFLICT (stored_name) DO NOTHING
                        """, nativeQuery = true)
        int enqueueIfAbsent(@Param("storedName") String storedName);
}
