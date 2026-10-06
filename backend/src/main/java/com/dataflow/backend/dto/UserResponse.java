package com.dataflow.backend.dto;

import com.dataflow.backend.entity.User;

import java.time.LocalDateTime;

public class UserResponse {

    private Long id;
    private String email;
    private LocalDateTime createdAt;

    public UserResponse(Long id, String email, LocalDateTime createdAt) {
        this.id = id;
        this.email = email;
        this.createdAt = createdAt;
    }

    public static UserResponse fromEntity(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getCreatedAt()
        );
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}