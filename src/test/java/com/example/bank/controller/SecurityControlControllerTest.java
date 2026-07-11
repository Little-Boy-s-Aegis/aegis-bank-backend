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

    private final String syncToken = "test-only-not-for-production";

    @Test
    public void testGetSecurityStatus() throws Exception {
        mockMvc.perform(get("/api/admin/security/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sqliEnabled").exists())
                .andExpect(jsonPath("$.xssEnabled").exists());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
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
    @WithMockUser(roles = "USER")
    public void testToggleSecuritySettingForbiddenForNormalUser() throws Exception {
        mockMvc.perform(post("/api/admin/security/toggle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vulnerability\":\"sqli\",\"enabled\":true}"))
                .andExpect(status().isForbidden());
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
    public void testBannedIpSyncBlocksApplicationRequests() throws Exception {
        String ip = "198.51.100.77";

        mockMvc.perform(post("/api/admin/security/banned-ips")
                        .header("X-Aegis-Token", syncToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ipAddress\":\"IP " + ip + "\",\"status\":\"active\",\"bannedBy\":\"test\",\"reason\":\"unit test\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ipAddress").value(ip))
                .andExpect(jsonPath("$.status").value("active"));

        mockMvc.perform(get("/api/admin/security/status")
                        .header("X-Real-IP", ip))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/security/banned-ips")
                        .header("X-Aegis-Token", syncToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ipAddress\":\"" + ip + "\",\"status\":\"unbanned\",\"bannedBy\":\"test\",\"reason\":\"unit test cleanup\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("unbanned"));

        mockMvc.perform(get("/api/admin/security/status")
                        .header("X-Real-IP", ip))
                .andExpect(status().isOk());
    }

    @Test
    public void testToggleSecuritySettingRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/admin/security/toggle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vulnerability\":\"sqli\",\"enabled\":true}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    public void testGetSecurityLogsAuthorizedForAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/security/logs"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    public void testClearLogs() throws Exception {
        mockMvc.perform(post("/api/admin/security/logs/clear"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Security logs cleared"));
    }

    @Test
    @WithMockUser(roles = "USER")
    public void testClearLogsForbiddenForNormalUser() throws Exception {
        mockMvc.perform(post("/api/admin/security/logs/clear"))
                .andExpect(status().isForbidden());
    }

    @Test
    public void testClearLogsRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/admin/security/logs/clear"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    public void testClearBannedIpsAdmin() throws Exception {
        mockMvc.perform(post("/api/admin/security/banned-ips/clear"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("All banned IPs cleared"));
    }

    @Test
    public void testClearBannedIpsToken() throws Exception {
        mockMvc.perform(post("/api/admin/security/banned-ips/clear")
                        .header("X-Aegis-Token", syncToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("All banned IPs cleared"));
    }

    @Test
    @WithMockUser(roles = "USER")
    public void testClearBannedIpsForbiddenForNormalUser() throws Exception {
        mockMvc.perform(post("/api/admin/security/banned-ips/clear"))
                .andExpect(status().isForbidden());
    }

    @Test
    public void testClearBannedIpsRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/admin/security/banned-ips/clear"))
                .andExpect(status().isUnauthorized());
    }
}
