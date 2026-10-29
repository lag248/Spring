package com.example.hotelbooking.booking.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.hotelbooking.booking.config.SecurityConfig;
import com.example.hotelbooking.booking.dto.AuthRequest;
import com.example.hotelbooking.booking.dto.AuthResponse;
import com.example.hotelbooking.booking.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "security.jwt.secret=U3VwZXJTZWNyZXREZXZlbG9wbWVudEtleUZvckhvdGVsQm9va2luZy0yMDI2IQ==")
class AuthControllerWebTest {
    @Autowired MockMvc mockMvc;
    @MockBean AuthService authService;

    @Test
    void loginEndpointIsPublicAndReturnsBearerToken() throws Exception {
        when(authService.authenticate(any(AuthRequest.class)))
                .thenReturn(new AuthResponse("Bearer", "example.jwt.token", 3600, 7L, "demo", "USER"));

        mockMvc.perform(post("/api/user/auth")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"demo\",\"password\":\"Password123!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").value("example.jwt.token"))
                .andExpect(jsonPath("$.expiresInSeconds").value(3600));
    }
}
