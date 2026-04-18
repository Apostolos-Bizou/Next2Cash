package com.next2me.next2cash.controller;

import com.next2me.next2cash.BaseIntegrationTest;
import com.next2me.next2cash.model.User;
import com.next2me.next2cash.support.TestDataBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Baseline tests for AuthController.
 *
 * These tests capture the CURRENT behavior of the auth endpoints.
 * They must pass BEFORE any Phase B security changes.
 * If they break later, it means Phase B inadvertently broke auth.
 */
class AuthControllerTest extends BaseIntegrationTest {

    @Autowired
    private TestDataBuilder tdb;

    // ─── POST /api/auth/login ─────────────────────────────────────────

    @Test
    void login_withValidCredentials_returns200AndToken() throws Exception {
        // Arrange: create an admin user with a known password
        User admin = tdb.createAdmin("apostolos");

        // Act + Assert
        String body = objectMapper.writeValueAsString(Map.of(
            "username", "apostolos",
            "password", TestDataBuilder.ADMIN_PASSWORD
        ));

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.token").isNotEmpty())
            .andExpect(jsonPath("$.user.username").value("apostolos"))
            .andExpect(jsonPath("$.user.role").value("admin"));
    }

    @Test
    void login_withWrongPassword_returns401() throws Exception {
        tdb.createAdmin("apostolos");

        String body = objectMapper.writeValueAsString(Map.of(
            "username", "apostolos",
            "password", "wrong-password"
        ));

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("Invalid credentials"));
    }

    @Test
    void login_withNonexistentUser_returns401() throws Exception {
        // No user created -> any login attempt should fail
        String body = objectMapper.writeValueAsString(Map.of(
            "username", "does-not-exist",
            "password", "any-password"
        ));

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false));
    }

    // ─── GET /api/auth/me ─────────────────────────────────────────────

    @Test
    void me_withValidJwt_returnsUserInfo() throws Exception {
        // Arrange
        User admin = tdb.createAdmin("apostolos");
        String bearerToken = tdb.bearerToken(admin);

        // Act + Assert
        mockMvc.perform(get("/api/auth/me")
                .header("Authorization", bearerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("apostolos"))
            .andExpect(jsonPath("$.role").value("admin"))
            .andExpect(jsonPath("$.id").isNotEmpty());
    }
}