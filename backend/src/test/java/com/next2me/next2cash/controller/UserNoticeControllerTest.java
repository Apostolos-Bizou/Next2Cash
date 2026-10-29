package com.next2me.next2cash.controller;

import com.next2me.next2cash.BaseIntegrationTest;
import com.next2me.next2cash.model.User;
import com.next2me.next2cash.repository.UserNoticeDismissalRepository;
import com.next2me.next2cash.support.TestDataBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** /api/notices/{key}: per-user, idempotent, self-scoped; VIEWER reads but never writes. */
class UserNoticeControllerTest extends BaseIntegrationTest {

    private static final String KEY = "polaris-opex-raises-intro";

    @Autowired private TestDataBuilder tdb;
    @Autowired private UserNoticeDismissalRepository repository;

    private User admin, user, accountant, viewer;

    @BeforeEach
    void setup() {
        admin = tdb.createAdmin("apostolos");
        user = tdb.createUser("koutsiouris");
        accountant = tdb.createAccountant("leonidas");
        viewer = tdb.createViewer("simos");
    }

    private String bearer(User u) { return tdb.bearerToken(u); }

    @Test
    @DisplayName("not dismissed -> dismiss -> dismissed, sticks, idempotent; other users unaffected")
    void dismissFlow() throws Exception {
        for (User u : new User[] { admin, user, accountant }) {
            mockMvc.perform(get("/api/notices/" + KEY).header("Authorization", bearer(u)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.key").value(KEY))
                .andExpect(jsonPath("$.data.dismissed").value(false));
        }
        mockMvc.perform(post("/api/notices/" + KEY + "/dismiss").header("Authorization", bearer(user)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.dismissed").value(true));
        mockMvc.perform(post("/api/notices/" + KEY + "/dismiss").header("Authorization", bearer(user)))
            .andExpect(status().isOk());                                       // idempotent: no duplicate row
        assertEquals(1, repository.count());

        // the dismissal is per user: a fresh token for the same user still sees it dismissed, others do not
        mockMvc.perform(get("/api/notices/" + KEY).header("Authorization", tdb.bearerToken(user)))
            .andExpect(jsonPath("$.data.dismissed").value(true));
        mockMvc.perform(get("/api/notices/" + KEY).header("Authorization", bearer(admin)))
            .andExpect(jsonPath("$.data.dismissed").value(false));
        mockMvc.perform(get("/api/notices/" + KEY).header("Authorization", bearer(accountant)))
            .andExpect(jsonPath("$.data.dismissed").value(false));
        // another key is independent
        mockMvc.perform(get("/api/notices/some-other-notice").header("Authorization", bearer(user)))
            .andExpect(jsonPath("$.data.dismissed").value(false));
    }

    @Test
    @DisplayName("VIEWER can read its status but gets 403 on dismiss (no VIEWER on writes); unauthenticated -> 403; bad key -> 400")
    void gates() throws Exception {
        mockMvc.perform(get("/api/notices/" + KEY).header("Authorization", bearer(viewer)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.dismissed").value(false));
        mockMvc.perform(post("/api/notices/" + KEY + "/dismiss").header("Authorization", bearer(viewer)))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/notices/" + KEY)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/notices/" + KEY + "/dismiss")).andExpect(status().isForbidden());
        for (String bad : new String[] { "Has%20Spaces", "UPPER", "x".repeat(61), "-leading-dash" }) {
            mockMvc.perform(get("/api/notices/" + bad).header("Authorization", bearer(admin)))
                .andExpect(status().isBadRequest());
        }
        assertEquals(0, repository.count());
    }
}
