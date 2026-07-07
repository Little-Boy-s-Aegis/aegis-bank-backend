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
public class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @org.junit.jupiter.api.BeforeEach
    public void setup() {
        com.example.bank.model.SecuritySettings settings = com.example.bank.model.SecuritySettings.getInstance();
        settings.setParamTamperingEnabled(false);
        settings.setXssEnabled(false);
        settings.setIdorEnabled(false);
        settings.setSqliEnabled(false);
        settings.setBruteForceEnabled(false);
    }

    @Test
    @WithMockUser(username = "alice")
    public void testTransferMoneyTargetNotFound() throws Exception {
        String body = "{" +
                "\"sourceAccountNumber\":\"ACC-123456\"," +
                "\"targetAccountNumber\":\"ACC-NONEXISTENT\"," +
                "\"amount\":500.00," +
                "\"description\":\"Test payment\"" +
                "}";

        mockMvc.perform(post("/api/transactions/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Target account not found"));
    }

    @Test
    @WithMockUser(username = "alice")
    public void testTransferMoneySourceOwnershipFailure() throws Exception {
        // ACC-987654 belongs to Bob, not Alice
        String body = "{" +
                "\"sourceAccountNumber\":\"ACC-987654\"," +
                "\"targetAccountNumber\":\"ACC-123456\"," +
                "\"amount\":500.00," +
                "\"description\":\"Test parameter tampering\"" +
                "}";

        mockMvc.perform(post("/api/transactions/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden: You do not own the source account."));
    }

    @Test
    @WithMockUser(username = "alice")
    public void testTransferMoneyNegativeAmountBlocked() throws Exception {
        // Enforced by TransferRequest DTO Validation
        String body = "{" +
                "\"sourceAccountNumber\":\"ACC-123456\"," +
                "\"targetAccountNumber\":\"ACC-987654\"," +
                "\"amount\":-100.00," +
                "\"description\":\"Test negative amount\"" +
                "}";

        mockMvc.perform(post("/api/transactions/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @WithMockUser(username = "alice")
    public void testTransferMoneySuccess() throws Exception {
        String body = "{" +
                "\"sourceAccountNumber\":\"ACC-123456\"," +
                "\"targetAccountNumber\":\"ACC-987654\"," +
                "\"amount\":100.00," +
                "\"description\":\"Secure test transfer\"" +
                "}";

        mockMvc.perform(post("/api/transactions/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Transfer completed successfully"));
     }

    @Test
    @WithMockUser(username = "alice")
    public void testTransferMoneyIdempotent() throws Exception {
        String key = "idemp-key-" + System.currentTimeMillis();
        String body = "{" +
                "\"sourceAccountNumber\":\"ACC-123456\"," +
                "\"targetAccountNumber\":\"ACC-987654\"," +
                "\"amount\":10.00," +
                "\"description\":\"Idempotent transfer\"" +
                "}";

        // First transfer request
        String response1 = mockMvc.perform(post("/api/transactions/transfer")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Transfer completed successfully"))
                .andReturn().getResponse().getContentAsString();

        String txId1 = com.jayway.jsonpath.JsonPath.read(response1, "$.transactionId").toString();

        // Second replayed transfer request
        String response2 = mockMvc.perform(post("/api/transactions/transfer")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Transfer completed successfully"))
                .andReturn().getResponse().getContentAsString();

        String txId2 = com.jayway.jsonpath.JsonPath.read(response2, "$.transactionId").toString();

        // Assert that both responses return identical transaction ID (since it was handled idempotently)
        org.junit.jupiter.api.Assertions.assertEquals(txId1, txId2);
    }

    @Test
    @WithMockUser(username = "alice")
    public void testTransferMoneyFingerprintIdempotentWithoutHeader() throws Exception {
        String body = "{" +
                "\"sourceAccountNumber\":\"ACC-123456\"," +
                "\"targetAccountNumber\":\"ACC-987654\"," +
                "\"amount\":20.00," +
                "\"description\":\"Fingerprint test\"" +
                "}";

        // First transfer request
        String response1 = mockMvc.perform(post("/api/transactions/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Transfer completed successfully"))
                .andReturn().getResponse().getContentAsString();

        String txId1 = com.jayway.jsonpath.JsonPath.read(response1, "$.transactionId").toString();

        // Second replayed transfer request without any header
        String response2 = mockMvc.perform(post("/api/transactions/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Transfer completed successfully"))
                .andReturn().getResponse().getContentAsString();

        String txId2 = com.jayway.jsonpath.JsonPath.read(response2, "$.transactionId").toString();

        // Assert that both responses return identical transaction ID (handled by fingerprint)
        org.junit.jupiter.api.Assertions.assertEquals(txId1, txId2);
    }

    @Test
    @WithMockUser(username = "alice")
    public void testGetTransactionHistoryIDORBlocked() throws Exception {
        // ACC-987654 belongs to Bob, not Alice
        mockMvc.perform(get("/api/transactions/history")
                        .param("accountNumber", "ACC-987654"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Access denied."));
    }

    @Test
    @WithMockUser(username = "alice")
    public void testGetTransactionHistorySuccess() throws Exception {
        mockMvc.perform(get("/api/transactions/history")
                        .param("accountNumber", "ACC-123456"))
                .andExpect(status().isOk());
    }
}
