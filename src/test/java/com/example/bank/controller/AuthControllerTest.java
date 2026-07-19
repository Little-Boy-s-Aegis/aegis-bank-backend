package com.example.bank.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private com.example.bank.repository.UserRepository userRepository;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Test
    public void testDebugDatabaseState() throws Exception {
        System.out.println("=== DEBUG DATABASE STATE ===");
        long userCount = userRepository.count();
        System.out.println("Total users in DB: " + userCount);
        userRepository.findAll().forEach(u -> {
            boolean pwMatches = passwordEncoder.matches("password123", u.getPassword());
            System.out.println("User: username=" + u.getUsername() + ", email=" + u.getEmail() + ", role=" + u.getRole() + ", pwMatches=" + pwMatches);
        });
        System.out.println("============================");
    }

    @Test
    public void testLogoutAndTokenRevocation() throws Exception {
        // 1. Login to get a valid token
        String responseContent = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String token = com.jayway.jsonpath.JsonPath.read(responseContent, "$.token");

        // 2. Access details with the token should succeed
        mockMvc.perform(get("/api/accounts/ACC-123456/details")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // 3. Call logout to revoke the token
        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logged out successfully"));

        // 4. Accessing details again with the same token must fail (401 Unauthorized)
        mockMvc.perform(get("/api/accounts/ACC-123456/details")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void testRegisterUserSuccess() throws Exception {
        String uniqueUser = "user_" + System.currentTimeMillis();
        String uniqueEmail = uniqueUser + "@example.com";

        String registerBody = "{" +
                "\"username\":\"" + uniqueUser + "\"," +
                "\"password\":\"password123\"," +
                "\"fullName\":\"Test User\"," +
                "\"email\":\"" + uniqueEmail + "\"" +
                "}";

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("User registered successfully"));
    }

    @Test
    public void testRegisterUserMissingFields() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"onlyusername\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testRegisterUserDuplicateUsername() throws Exception {
        // 'alice' is already seeded
        String registerBody = "{" +
                "\"username\":\"alice\"," +
                "\"password\":\"password123\"," +
                "\"fullName\":\"Alice Smith\"," +
                "\"email\":\"alice_dup@example.com\"" +
                "}";

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Username already exists"));
    }

    @Test
    public void testRegisterUserValidation() throws Exception {
        // Test malformed email
        String bodyMalformedEmail = "{" +
                "\"username\":\"new_user_1\"," +
                "\"password\":\"password123\"," +
                "\"fullName\":\"New User\"," +
                "\"email\":\"invalid-email-format\"" +
                "}";
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyMalformedEmail))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid email format"));

        // Test duplicate email (case-insensitive)
        String bodyDuplicateEmail = "{" +
                "\"username\":\"new_user_2\"," +
                "\"password\":\"password123\"," +
                "\"fullName\":\"New User\"," +
                "\"email\":\"ALICE@demo-bank.com\"" + // alice@demo-bank.com is seeded
                "}";
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyDuplicateEmail))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Email already exists"));

        // Test duplicate username (case-insensitive)
        String bodyDuplicateUsername = "{" +
                "\"username\":\"ALICE\"," + // alice is seeded
                "\"password\":\"password123\"," +
                "\"fullName\":\"New User\"," +
                "\"email\":\"new_user_email@example.com\"" +
                "}";
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyDuplicateUsername))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Username already exists"));
    }

    @Test
    public void testLoginSuccessSecureMode() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.user.username").value("alice"));
    }

    @Test
    public void testLoginFailedSecureMode() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"wrongpassword\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid credentials"));
    }
}
