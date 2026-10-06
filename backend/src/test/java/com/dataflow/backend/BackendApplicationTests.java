package com.dataflow.backend;

import com.dataflow.backend.entity.User;
import com.dataflow.backend.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class BackendApplicationTests {

    @Autowired
    private UserService userService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void shouldCreateUserWithEncryptedPassword() {

        String email = "bcrypt-test-" + System.currentTimeMillis() + "@dataflow.com";
        String rawPassword = "MySecurePassword123";

        User user = userService.createUser(email, rawPassword);

        assertNotNull(user.getId());
        assertNotNull(user.getCreatedAt());
        assertNotNull(user.getUpdatedAt());

        assertEquals(email, user.getEmail());

        // The stored password must not be the original password
        assertNotEquals(rawPassword, user.getPasswordHash());

        // BCrypt must be able to verify the original password
        assertTrue(
                passwordEncoder.matches(
                        rawPassword,
                        user.getPasswordHash()
                )
        );
    }
}