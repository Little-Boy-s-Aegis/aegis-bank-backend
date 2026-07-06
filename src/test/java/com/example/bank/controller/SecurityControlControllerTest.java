package com.example.bank.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class SecurityControlControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final String syncToken = "aegis-secret-security-sync-token-2026";

    @Test
    public void testGetSecurityStatus() throws Exception {
        mockMvc.perform(get("/api/admin/security/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sqliEnabled").exists())
                .andExpect(jsonPath("$.xssEnabled").exists());
    }

    @Test
    @WithMockUser
    public void testToggleSecuritySetting() throws Exception {
        mockMvc.perform(post("/api/admin/security/toggle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vulnerability\":\"sqli\",\"enabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vulnerability").value("sqli"))
                .andExpect(jsonPath("$.enabled").value(true));

        mockMvc.perform(post("/api/admin/security/toggle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vulnerability\":\"invalid_vuln\",\"enabled\":true}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testGetSecurityLogsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/security/logs"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/admin/security/logs")
                        .header("X-Aegis-Token", "wrong_token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void testGetSecurityLogsAuthorized() throws Exception {
        mockMvc.perform(get("/api/admin/security/logs")
                        .header("X-Aegis-Token", syncToken))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    public void testClearLogs() throws Exception {
        mockMvc.perform(post("/api/admin/security/logs/clear"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Security logs cleared"));
    }
}
