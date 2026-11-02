package com.dataflow.backend.security;

import io.jsonwebtoken.MalformedJwtException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldContinueWithoutAuthenticationWhenTokenIsMissing()
            throws Exception {

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain filterChain = mock(FilterChain.class);

        jwtAuthenticationFilter.doFilterInternal(
                request,
                response,
                filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(jwtService);

        assertNull(
                SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void shouldAuthenticateWhenTokenIsValid() throws Exception {

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-token");

        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain filterChain = mock(FilterChain.class);

        when(jwtService.extractEmail("valid-token"))
                .thenReturn("test@dataflow.com");

        jwtAuthenticationFilter.doFilterInternal(
                request,
                response,
                filterChain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        assertNotNull(authentication);
        assertEquals("test@dataflow.com", authentication.getPrincipal());
        assertTrue(authentication.isAuthenticated());

        verify(jwtService).extractEmail("valid-token");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldContinueWithoutAuthenticationWhenTokenIsInvalid()
            throws Exception {

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer invalid-token");

        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain filterChain = mock(FilterChain.class);

        when(jwtService.extractEmail("invalid-token"))
                .thenThrow(new MalformedJwtException("Invalid JWT"));

        jwtAuthenticationFilter.doFilterInternal(
                request,
                response,
                filterChain);

        assertNull(
                SecurityContextHolder.getContext().getAuthentication());

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldPropagateUnexpectedErrors() throws Exception {

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-token");

        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain filterChain = mock(FilterChain.class);

        when(jwtService.extractEmail("valid-token"))
                .thenThrow(new IllegalStateException("Unexpected error"));

        assertThrows(
                IllegalStateException.class,
                () -> jwtAuthenticationFilter.doFilterInternal(
                        request,
                        response,
                        filterChain));

        verify(filterChain, never()).doFilter(request, response);
    }
}