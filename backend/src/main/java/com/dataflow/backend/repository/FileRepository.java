package com.dataflow.backend.repository;

import com.dataflow.backend.entity.File;
import com.dataflow.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FileRepository extends JpaRepository<File, Long> {

    List<File> findAllByUserOrderByCreatedAtDesc(User user);

    Optional<File> findByIdAndUser(Long id, User user);
}