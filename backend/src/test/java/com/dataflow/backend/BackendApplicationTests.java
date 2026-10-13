package com.dataflow.backend;

import com.dataflow.backend.entity.User;
import com.dataflow.backend.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/dataflow_test",
        "spring.datasource.username=poysmon",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "file.storage.location=target/test-uploads",
        "jwt.secret=DataFlowTestSecretKeyMustBeAtLeast32BytesLong!",
        "jwt.expiration-ms=3600000"
})
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