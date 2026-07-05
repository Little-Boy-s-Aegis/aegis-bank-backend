package com.example.bank.controller;

import com.example.bank.model.Account;
import com.example.bank.model.SecurityLog;
import com.example.bank.model.SecuritySettings;
import com.example.bank.repository.AccountRepository;
import com.example.bank.repository.SecurityLogRepository;
import com.example.bank.service.SecurityEventPublisher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/accounts")
@CrossOrigin(origins = "*")
public class AccountController {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private SecurityLogRepository securityLogRepository;

    @Autowired
    private SecurityEventPublisher securityEventPublisher;

    @GetMapping("/{accountNumber}/details")
    public ResponseEntity<?> getAccountDetails(@PathVariable String accountNumber, HttpServletRequest servletRequest) {
        String clientIp = servletRequest.getRemoteAddr();
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();

        Account account = accountRepository.findByAccountNumber(accountNumber).orElse(null);
        if (account == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Account not found"));
        }

        SecuritySettings settings = SecuritySettings.getInstance();

        if (settings.isIdorEnabled()) {
            // Vulnerable Mode: IDOR/BOLA is enabled.
            // Directly return account details without verifying ownership.
            return ResponseEntity.ok(Map.of(
                    "accountNumber", account.getAccountNumber(),
                    "fullName", account.getUser().getFullName(),
                    "balance", account.getBalance(),
                    "currency", account.getCurrency(),
                    "email", account.getUser().getEmail()
            ));
        } else {
            // Secure Mode: Verify if the logged-in user owns the requested account number.
            if (!account.getUser().getUsername().equalsIgnoreCase(currentUsername)) {
                // Log BOLA/IDOR attack
                SecurityLog idorLog = SecurityLog.builder()
                        .timestamp(LocalDateTime.now())
                        .attackType("IDOR/BOLA")
                        .endpoint("GET /api/accounts/" + accountNumber + "/details")
                        .payload("Requested: " + accountNumber + " | Authenticated: " + currentUsername)
                        .status("BLOCKED")
                        .clientIp(clientIp)
                        .description("BOLA/IDOR blocked. User '" + currentUsername + "' tried to access '" + accountNumber + "' without ownership.")
                        .build();
                securityLogRepository.save(idorLog);
                securityEventPublisher.publish(idorLog);

                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                        "error", "Access denied. You do not own this account."
                ));
            }

            return ResponseEntity.ok(Map.of(
                    "accountNumber", account.getAccountNumber(),
                    "fullName", account.getUser().getFullName(),
                    "balance", account.getBalance(),
                    "currency", account.getCurrency(),
                    "email", account.getUser().getEmail()
            ));
        }
    }
}
