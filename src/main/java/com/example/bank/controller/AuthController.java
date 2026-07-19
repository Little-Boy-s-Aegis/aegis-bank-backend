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

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

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

        // Trim values
        username = username.trim();
        password = password.trim();
        fullName = fullName.trim();
        email = email.trim();

        if (username.isEmpty() || password.isEmpty() || fullName.isEmpty() || email.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Fields cannot be blank"));
        }

        // Validate Username (alphanumeric, underscores, hyphens, periods, 3-30 chars)
        if (!username.matches("^[a-zA-Z0-9_.-]{3,30}$")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Username must be 3-30 characters and contain only letters, numbers, underscores, hyphens, or periods"));
        }

        // Validate Password (min 6 chars)
        if (password.length() < 6) {
            return ResponseEntity.badRequest().body(Map.of("error", "Password must be at least 6 characters long"));
        }

        // Validate Email format
        if (!email.matches("^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid email format"));
        }

        if (userRepository.findByUsernameIgnoreCase(username).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Username already exists"));
        }

        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email already exists"));
        }

        // Save user
        User user = User.builder()
                .username(username)
                .password(passwordEncoder.encode(password))
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

        // Create Account with default balance 20,000,000 VND
        Account account = Account.builder()
                .accountNumber(accNum)
                .balance(20000000.0)
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
        String clientIp = getClientIp(servletRequest);

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
            String sql = "SELECT * FROM users WHERE username = '" + username + "' AND password = '" + password + "'";

            // Even in vulnerable mode, detect and log the attack for SOC visibility
            if (isXssPayload(username) || isXssPayload(password)) {
                SecurityLog xssLog = SecurityLog.builder()
                        .timestamp(LocalDateTime.now())
                        .attackType("XSS")
                        .endpoint("POST /api/auth/login")
                        .payload("username=" + username + ", password=" + password)
                        .status("ALLOWED")
                        .clientIp(clientIp)
                        .description("Cross-Site Scripting (XSS) payload detected in authentication. Attack was ALLOWED (vulnerable mode active).")
                        .build();
                securityLogRepository.save(xssLog);
                securityEventPublisher.publish(xssLog);
            } else if (isSqlInjectionPayload(username) || isSqlInjectionPayload(password)) {
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
                List<User> results = jdbcTemplate.query(sql, (rs, rowNum) -> new User(
                    rs.getLong("id"),
                    rs.getString("username"),
                    rs.getString("password"),
                    rs.getString("full_name"),
                    rs.getString("email"),
                    rs.getString("role")
                ));
                if (!results.isEmpty()) {
                    authenticatedUser = results.get(0);
                }
            } catch (Exception e) {
                // Return unauthorized if SQL query fails syntactically
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid credentials (SQL error)"));
            }
        } else {
            // Secure Code: Prepared Statement & BCrypt Matcher
            System.out.println("DEBUG LOGIN: secure mode. Username requested: '" + username + "'");
            Optional<User> userOpt = userRepository.findByUsername(username);
            System.out.println("DEBUG LOGIN: userOpt.isPresent() = " + userOpt.isPresent());
            if (userOpt.isPresent()) {
                boolean pwMatch = passwordEncoder.matches(password, userOpt.get().getPassword());
                System.out.println("DEBUG LOGIN: password match result = " + pwMatch + " (Input: '" + password + "', Stored: '" + userOpt.get().getPassword() + "')");
                if (pwMatch) {
                    authenticatedUser = userOpt.get();
                }
            }

            // Track failures for rate limit
            if (authenticatedUser == null && !settings.isBruteForceEnabled()) {
                loginFailures.get(clientIp).add(System.currentTimeMillis());
            }

            // Check if login request contains malicious SQL/XSS characters in secure mode to log attack
            if (isXssPayload(username) || isXssPayload(password)) {
                SecurityLog xssLog = SecurityLog.builder()
                        .timestamp(LocalDateTime.now())
                        .attackType("XSS")
                        .endpoint("POST /api/auth/login")
                        .payload("username=" + username + ", password=" + password)
                        .status("BLOCKED")
                        .clientIp(clientIp)
                        .description("Blocked Cross-Site Scripting (XSS) attempt in authentication parameters.")
                        .build();
                securityLogRepository.save(xssLog);
                securityEventPublisher.publish(xssLog);
            } else if (isSqlInjectionPayload(username) || isSqlInjectionPayload(password)) {
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

    @PostMapping("/logout")
    public ResponseEntity<?> logoutUser(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            jwtTokenUtil.blacklistToken(token);
        }
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    private boolean isSqlInjectionPayload(String input) {
        if (input == null) return false;
        String lower = input.toLowerCase();
        return lower.contains("'") || lower.contains("--") || lower.contains("/*") || lower.contains(" or ") || lower.contains(" union ");
    }

    private boolean isXssPayload(String input) {
        if (input == null) return false;
        String lower = input.toLowerCase();
        return lower.contains("<") || lower.contains(">") || lower.contains("script") || lower.contains("alert(") || lower.contains("javascript:") || lower.contains("onload=") || lower.contains("onerror=");
    }

    private String getClientIp(jakarta.servlet.http.HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        String xri = request.getHeader("X-Real-IP");
        if (xri != null && !xri.isBlank()) {
            return xri.trim();
        }
        return request.getRemoteAddr();
    }
}
