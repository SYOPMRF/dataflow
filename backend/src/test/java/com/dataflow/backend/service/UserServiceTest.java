package com.dataflow.backend.service;

import com.dataflow.backend.entity.User;
import com.dataflow.backend.exception.ConflictException;
import com.dataflow.backend.exception.InvalidCredentialsException;
import com.dataflow.backend.repository.UserRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Test
    void createUserShouldNormalizeEmailAndEncodePassword() {
        String normalizedEmail = "usuario@gmail.com";
        String encodedPassword = "encoded-password";

        when(userRepository.existsByEmail(normalizedEmail))
                .thenReturn(false);

        when(passwordEncoder.encode("password123"))
                .thenReturn(encodedPassword);

        User savedUser = new User();
        savedUser.setEmail(normalizedEmail);
        savedUser.setPasswordHash(encodedPassword);

        when(userRepository.save(any(User.class)))
                .thenReturn(savedUser);

        User result = userService.createUser(
                " Usuario@Gmail.com ",
                "password123");

        assertSame(savedUser, result);

        verify(userRepository).existsByEmail(normalizedEmail);
        verify(passwordEncoder).encode("password123");
        verify(userRepository).save(argThat(user -> normalizedEmail.equals(user.getEmail())
                && encodedPassword.equals(user.getPasswordHash())));
    }

    @Test
    void createUserShouldRejectDuplicateNormalizedEmail() {
        when(userRepository.existsByEmail("usuario@gmail.com"))
                .thenReturn(true);

        assertThrows(
                ConflictException.class,
                () -> userService.createUser(
                        " Usuario@Gmail.com ",
                        "password123"));

        verify(userRepository).existsByEmail("usuario@gmail.com");
        verifyNoInteractions(passwordEncoder);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void createUserShouldConvertConcurrentDuplicateEmailIntoConflict() {
        String normalizedEmail = "usuario@gmail.com";

        when(userRepository.existsByEmail(normalizedEmail))
                .thenReturn(false);

        when(passwordEncoder.encode("password123"))
                .thenReturn("encoded-password");

        when(userRepository.save(any(User.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "Unique constraint violation"));

        ConflictException exception = assertThrows(
                ConflictException.class,
                () -> userService.createUser(
                        " Usuario@Gmail.com ",
                        "password123"));

        assertEquals("Email is already registered", exception.getMessage());

        verify(userRepository).existsByEmail(normalizedEmail);
        verify(passwordEncoder).encode("password123");
        verify(userRepository).save(argThat(user -> normalizedEmail.equals(user.getEmail())
                && "encoded-password".equals(user.getPasswordHash())));
    }

    @Test
    void findByEmailShouldNormalizeEmail() {
        User user = new User();
        user.setEmail("usuario@gmail.com");

        when(userRepository.findByEmail("usuario@gmail.com"))
                .thenReturn(Optional.of(user));

        User result = userService.findByEmail(" Usuario@Gmail.com ");

        assertSame(user, result);

        verify(userRepository).findByEmail("usuario@gmail.com");
    }

    @Test
    void findByEmailShouldReturnNullWhenUserDoesNotExist() {
        when(userRepository.findByEmail("usuario@gmail.com"))
                .thenReturn(Optional.empty());

        User result = userService.findByEmail(" Usuario@Gmail.com ");

        assertNull(result);

        verify(userRepository).findByEmail("usuario@gmail.com");
    }

    @Test
    void authenticateShouldNormalizeEmailAndAuthenticateUser() {
        User user = new User();
        user.setEmail("usuario@gmail.com");
        user.setPasswordHash("encoded-password");

        when(userRepository.findByEmail("usuario@gmail.com"))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches("password123", "encoded-password"))
                .thenReturn(true);

        User result = userService.authenticate(
                " Usuario@Gmail.com ",
                "password123");

        assertSame(user, result);

        verify(userRepository).findByEmail("usuario@gmail.com");
        verify(passwordEncoder).matches(
                "password123",
                "encoded-password");
    }

    @Test
    void authenticateShouldRejectUnknownUser() {
        when(userRepository.findByEmail("usuario@gmail.com"))
                .thenReturn(Optional.empty());

        assertThrows(
                InvalidCredentialsException.class,
                () -> userService.authenticate(
                        " Usuario@Gmail.com ",
                        "password123"));

        verify(userRepository).findByEmail("usuario@gmail.com");
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void authenticateShouldRejectIncorrectPassword() {
        User user = new User();
        user.setEmail("usuario@gmail.com");
        user.setPasswordHash("encoded-password");

        when(userRepository.findByEmail("usuario@gmail.com"))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches("wrong-password", "encoded-password"))
                .thenReturn(false);

        assertThrows(
                InvalidCredentialsException.class,
                () -> userService.authenticate(
                        " Usuario@Gmail.com ",
                        "wrong-password"));

        verify(userRepository).findByEmail("usuario@gmail.com");
        verify(passwordEncoder).matches(
                "wrong-password",
                "encoded-password");
    }
}
