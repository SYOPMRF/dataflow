package com.dataflow.backend;

import com.dataflow.backend.entity.User;
import com.dataflow.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
@AutoConfigureMockMvc
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void registerShouldReturnCreatedAndPersistUser() throws Exception {
        String email = "register-" + UUID.randomUUID() + "@dataflow.test";
        String password = "ValidPassword123";

        String requestBody = """
                {
                    "email": "%s",
                    "password": "%s"
                }
                """.formatted(email, password);

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        User user = userRepository.findByEmail(email).orElseThrow();

        assertTrue(passwordEncoder.matches(password, user.getPasswordHash()));
    }

    @Test
    void loginShouldReturnTokenAndUser() throws Exception {
        String email = "login-" + UUID.randomUUID() + "@dataflow.test";
        String password = "ValidPassword123";

        createUser(email, password);

        String requestBody = """
                {
                    "email": "%s",
                    "password": "%s"
                }
                """.formatted(email, password);

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value(email));
    }

    @Test
    void registerShouldRejectPasswordExceeding72Utf8Bytes() throws Exception {
        String email = "long-register-" + UUID.randomUUID() + "@dataflow.test";
        String password = "a".repeat(73);

        String requestBody = """
                {
                    "email": "%s",
                    "password": "%s"
                }
                """.formatted(email, password);

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());

        assertFalse(userRepository.existsByEmail(email));
    }

    @Test
    void loginShouldRejectPasswordExceeding72Utf8Bytes() throws Exception {
        String email = "long-login-" + UUID.randomUUID() + "@dataflow.test";

        createUser(email, "ValidPassword123");

        String requestBody = """
                {
                    "email": "%s",
                    "password": "%s"
                }
                """.formatted(email, "a".repeat(73));

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void registerShouldAcceptUnicodePasswordWithin72Utf8Bytes() throws Exception {
        String email = "unicode-register-" + UUID.randomUUID() + "@dataflow.test";
        String password = "á".repeat(36);

        String requestBody = """
                {
                    "email": "%s",
                    "password": "%s"
                }
                """.formatted(email, password);

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isCreated());

        User user = userRepository.findByEmail(email).orElseThrow();

        assertTrue(passwordEncoder.matches(password, user.getPasswordHash()));
    }

    @Test
    void loginShouldRejectUnknownUser() throws Exception {
        String email = "unknown-" + UUID.randomUUID() + "@dataflow.test";

        String requestBody = """
                {
                    "email": "%s",
                    "password": "ValidPassword123"
                }
                """.formatted(email);

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void loginShouldRejectIncorrectPassword() throws Exception {
        String email = "wrong-password-" + UUID.randomUUID() + "@dataflow.test";

        createUser(email, "ValidPassword123");

        String requestBody = """
                {
                    "email": "%s",
                    "password": "WrongPassword123"
                }
                """.formatted(email);

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").exists());
    }

    private User createUser(String email, String password) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));

        return userRepository.saveAndFlush(user);
    }
}
