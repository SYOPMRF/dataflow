package com.dataflow.backend.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginRequestTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        validatorFactory.close();
    }

    @Test
    void shouldAcceptPasswordWithExactly72Utf8Bytes() {
        LoginRequest request = createRequest("a".repeat(72));

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void shouldRejectPasswordWithMoreThan72Utf8Bytes() {
        LoginRequest request = createRequest("a".repeat(73));

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void shouldAcceptUnicodePasswordWithin72Utf8Bytes() {
        LoginRequest request = createRequest("á".repeat(36));

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void shouldRejectUnicodePasswordExceeding72Utf8Bytes() {
        LoginRequest request = createRequest("á".repeat(37));

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void shouldNotRequireRegistrationMinimumPasswordLength() {
        LoginRequest request = createRequest("short");

        assertTrue(validator.validate(request).isEmpty());
    }

    private LoginRequest createRequest(String password) {
        LoginRequest request = new LoginRequest();
        request.setEmail("usuario@gmail.com");
        request.setPassword(password);

        return request;
    }
}
