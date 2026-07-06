package com.example.bank.controller;

import com.example.bank.config.JwtTokenUtil;
import com.example.bank.model.Account;
import com.example.bank.model.SecurityLog;
import com.example.bank.model.SecuritySettings;
import com.example.bank.model.User;
import com.example.bank.repository.AccountRepository;
import com.example.bank.repository.SecurityLogRepository;
import com.example.bank.repository.UserRepository;
import com.example.bank.service.SecurityEventPublisher;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private SecurityLogRepository securityLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    @Autowired
    private SecurityEventPublisher securityEventPublisher;

    @PersistenceContext
    private EntityManager entityManager;

    // In-memory rate limiting map: IP -> List of failure timestamps
    private static final Map<String, List<Long>> loginFailures = new ConcurrentHashMap<>();
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long BLOCK_WINDOW_MS = 60000; // 1 minute

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String password = request.get("password");
        String fullName = request.get("fullName");
        String email = request.get("email");

        if (username == null || password == null || fullName == null || email == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing required fields"));
        }

        if (userRepository.findByUsername(username).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Username already exists"));
        }

        if (userRepository.findByEmail(email).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email already exists"));
        }

        // Save user
        User user = User.builder()
                .username(username)
                .password(passwordEncoder.encode(password))
                .passwordPlain(password)
                .fullName(fullName)
                .email(email)
                .role("USER")
                .build();
        userRepository.save(user);

        // Generate Account Number
        Random rand = new Random();
        String accNum = "ACC-" + (100000 + rand.nextInt(900000));
        while (accountRepository.findByAccountNumber(accNum).isPresent()) {
            accNum = "ACC-" + (100000 + rand.nextInt(900000));
        }

        // Create Account with default balance 5,000,000 VND
        Account account = Account.builder()
                .accountNumber(accNum)
                .balance(5000000.0)
                .currency("VND")
                .user(user)
                .build();
        accountRepository.save(account);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", "User registered successfully",
                "accountNumber", accNum
        ));
    }

    @PostMapping("/login")
    public ResponseEntity<?> loginUser(@RequestBody Map<String, String> request, HttpServletRequest servletRequest) {
        String username = request.get("username");
        String password = request.get("password");
        String clientIp = servletRequest.getRemoteAddr();

        if (username == null || password == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing username or password"));
        }

        SecuritySettings settings = SecuritySettings.getInstance();

        // 1. Rate Limiting Check (Defensive Mode only)
        if (!settings.isBruteForceEnabled()) {
            long now = System.currentTimeMillis();
            List<Long> failures = loginFailures.computeIfAbsent(clientIp, k -> new ArrayList<>());
            // Remove older failures outside the 1-minute window
            failures.removeIf(timestamp -> now - timestamp > BLOCK_WINDOW_MS);

            if (failures.size() >= MAX_FAILED_ATTEMPTS) {
                // Log the brute force attempt
                SecurityLog bruteForceLog = SecurityLog.builder()
                        .timestamp(LocalDateTime.now())
                        .attackType("BRUTE_FORCE")
                        .endpoint("POST /api/auth/login")
                        .payload("Username attempted: " + username)
                        .status("BLOCKED")
                        .clientIp(clientIp)
                        .description("IP rate limited after " + failures.size() + " failed login attempts.")
                        .build();
                securityLogRepository.save(bruteForceLog);
                securityEventPublisher.publish(bruteForceLog);

                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of(
                        "error", "Too many failed attempts. Please try again after 1 minute."
                ));
            }
        }

        User authenticatedUser = null;

        // 2. SQL Injection Simulation
        if (settings.isSqliEnabled()) {
            // Vulnerable Code: String Concatenation!
            String sql = "SELECT * FROM users WHERE username = '" + username + "' AND password_plain = '" + password + "'";

            // Even in vulnerable mode, detect and log the attack for SOC visibility
            if (isSqlInjectionPayload(username) || isSqlInjectionPayload(password)) {
                SecurityLog sqliLog = SecurityLog.builder()
                        .timestamp(LocalDateTime.now())
                        .attackType("SQL_INJECTION")
                        .endpoint("POST /api/auth/login")
                        .payload("username=" + username + ", password=" + password)
                        .status("ALLOWED")
                        .clientIp(clientIp)
                        .description("SQL Injection payload detected in authentication. Attack was ALLOWED (vulnerable mode active).")
                        .build();
                securityLogRepository.save(sqliLog);
                securityEventPublisher.publish(sqliLog);
            }

            try {
                Query query = entityManager.createNativeQuery(sql, User.class);
                List<?> results = query.getResultList();
                if (!results.isEmpty()) {
                    authenticatedUser = (User) results.get(0);
                }
            } catch (Exception e) {
                // Return unauthorized if SQL query fails syntactically
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid credentials (SQL error)"));
            }
        } else {
            // Secure Code: Prepared Statement & BCrypt Matcher
            Optional<User> userOpt = userRepository.findByUsername(username);
            if (userOpt.isPresent() && passwordEncoder.matches(password, userOpt.get().getPassword())) {
                authenticatedUser = userOpt.get();
            }

            // Track failures for rate limit
            if (authenticatedUser == null && !settings.isBruteForceEnabled()) {
                loginFailures.get(clientIp).add(System.currentTimeMillis());
            }

            // Check if login request contains malicious SQL characters in secure mode to log attack
            if (isSqlInjectionPayload(username) || isSqlInjectionPayload(password)) {
                SecurityLog sqliLog = SecurityLog.builder()
                        .timestamp(LocalDateTime.now())
                        .attackType("SQL_INJECTION")
                        .endpoint("POST /api/auth/login")
                        .payload("username=" + username + ", password=" + password)
                        .status("BLOCKED")
                        .clientIp(clientIp)
                        .description("Blocked SQL Injection attempt in authentication parameters.")
                        .build();
                securityLogRepository.save(sqliLog);
                securityEventPublisher.publish(sqliLog);
            }
        }

        if (authenticatedUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid credentials"));
        }

        // Login Success: Reset rate limits
        if (!settings.isBruteForceEnabled()) {
            loginFailures.remove(clientIp);
        }

        // Generate Token
        UserDetails userDetails = new org.springframework.security.core.userdetails.User(
                authenticatedUser.getUsername(),
                authenticatedUser.getPassword(),
                new ArrayList<>()
        );
        String token = jwtTokenUtil.generateToken(userDetails);

        // Fetch Account Number
        Optional<Account> accountOpt = accountRepository.findByUser(authenticatedUser);
        String accountNumber = accountOpt.map(Account::getAccountNumber).orElse("NONE");

        return ResponseEntity.ok(Map.of(
                "token", token,
                "user", Map.of(
                        "id", authenticatedUser.getId(),
                        "username", authenticatedUser.getUsername(),
                        "fullName", authenticatedUser.getFullName(),
                        "email", authenticatedUser.getEmail(),
                        "role", authenticatedUser.getRole(),
                        "accountNumber", accountNumber
                )
        ));
    }

    private boolean isSqlInjectionPayload(String input) {
        if (input == null) return false;
        String lower = input.toLowerCase();
        return lower.contains("'") || lower.contains("--") || lower.contains("/*") || lower.contains(" or ") || lower.contains(" union ");
    }
}
