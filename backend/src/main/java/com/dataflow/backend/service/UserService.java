package com.dataflow.backend.service;

import com.dataflow.backend.entity.User;
import com.dataflow.backend.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.dataflow.backend.exception.InvalidCredentialsException;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User createUser(String email, String password) {

        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email is already registered");
        }

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));

        return userRepository.save(user);
    }

    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElse(null);
    }

    public User authenticate(String email, String password) {

        User user = userRepository.findByEmail(email)
                .orElse(null);

        if (user == null) {
            throw new InvalidCredentialsException();
        }

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        return user;
}
}