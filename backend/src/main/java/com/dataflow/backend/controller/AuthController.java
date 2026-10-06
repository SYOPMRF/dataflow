package com.dataflow.backend.controller;

import com.dataflow.backend.dto.RegisterRequest;
import com.dataflow.backend.dto.UserResponse;
import com.dataflow.backend.entity.User;
import com.dataflow.backend.service.UserService;
import jakarta.validation.Valid;
import com.dataflow.backend.dto.LoginRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {

        User user = userService.createUser(
                request.getEmail(),
                request.getPassword()
        );

        return UserResponse.fromEntity(user);
    }

    @PostMapping("/login")
    public UserResponse login(@Valid @RequestBody LoginRequest request) {

        User user = userService.authenticate(
            request.getEmail(),
            request.getPassword()
        );

        if (user == null) {
            throw new IllegalArgumentException("Invalid email or password");
        }

        return UserResponse.fromEntity(user);
}
}