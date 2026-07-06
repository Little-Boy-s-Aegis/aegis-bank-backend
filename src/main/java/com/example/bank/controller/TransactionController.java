package com.example.bank.controller;

import com.example.bank.model.Account;
import com.example.bank.model.SecurityLog;
import com.example.bank.model.SecuritySettings;
import com.example.bank.model.Transaction;
import com.example.bank.repository.AccountRepository;
import com.example.bank.repository.SecurityLogRepository;
import com.example.bank.repository.TransactionRepository;
import com.example.bank.service.SecurityEventPublisher;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.HtmlUtils;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private SecurityLogRepository securityLogRepository;

    @Autowired
    private SecurityEventPublisher securityEventPublisher;

    @PersistenceContext
    private EntityManager entityManager;

    private static final Map<String, ResponseEntity<?>> idempotencyMap = new java.util.concurrent.ConcurrentHashMap<>();

    @PostMapping("/transfer")
    @Transactional
    public ResponseEntity<?> transferMoney(@jakarta.validation.Valid @RequestBody com.example.bank.model.TransferRequest payload, HttpServletRequest servletRequest) {
        String clientIp = servletRequest.getRemoteAddr();
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();

        String idempotencyKey = servletRequest.getHeader("Idempotency-Key");
        if (idempotencyKey == null) {
            idempotencyKey = servletRequest.getHeader("X-Idempotency-Key");
        }

        if (idempotencyKey != null && !idempotencyKey.trim().isEmpty()) {
            ResponseEntity<?> cachedResponse = idempotencyMap.get(idempotencyKey);
            if (cachedResponse != null) {
                return cachedResponse;
            }
        }

        String sourceAccountNumber = payload.getSourceAccountNumber();
        String targetAccountNumber = payload.getTargetAccountNumber();
        Double amount = payload.getAmount();
        String description = payload.getDescription();

        SecuritySettings settings = SecuritySettings.getInstance();
        Account sourceAccount = accountRepository.findByAccountNumber(sourceAccountNumber).orElse(null);
        Account targetAccount = accountRepository.findByAccountNumber(targetAccountNumber).orElse(null);

        // 1. Validate Target Account
        if (targetAccount == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Target account not found"));
        }

        // 2. Validate Source Account
        if (sourceAccount == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Source account not found"));
        }

        // 3. Defensive Checks (Parameter Tampering Mitigation)
        if (!settings.isParamTamperingEnabled()) {
            // Secure Mode Check A: Check if the logged-in user owns the source account
            if (!sourceAccount.getUser().getUsername().equalsIgnoreCase(currentUsername)) {
                SecurityLog paramTamperLog = SecurityLog.builder()
                        .timestamp(LocalDateTime.now())
                        .attackType("PARAMETER_TAMPERING")
                        .endpoint("POST /api/transactions/transfer")
                        .payload("Source attempted: " + sourceAccountNumber + " | Authenticated: " + currentUsername)
                        .status("BLOCKED")
                        .clientIp(clientIp)
                        .description("Blocked transfer from account not owned by user. Potential parameter tampering.")
                        .build();
                securityLogRepository.save(paramTamperLog);
                securityEventPublisher.publish(paramTamperLog);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                        "error", "Forbidden: You do not own the source account."
                ));
            }

            // Secure Mode Check B: Check if transfer amount is positive
            if (amount <= 0) {
                SecurityLog amountTamperLog = SecurityLog.builder()
                        .timestamp(LocalDateTime.now())
                        .attackType("PARAMETER_TAMPERING")
                        .endpoint("POST /api/transactions/transfer")
                        .payload("Amount: " + amount)
                        .status("BLOCKED")
                        .clientIp(clientIp)
                        .description("Blocked money transfer with non-positive amount: " + amount)
                        .build();
                securityLogRepository.save(amountTamperLog);
                securityEventPublisher.publish(amountTamperLog);
                return ResponseEntity.badRequest().body(Map.of(
                        "amount", "Amount must be a positive number greater than zero",
                        "error", "Amount must be a positive number greater than zero"
                ));
            }

            // Secure Mode Check C: Check if source balance is sufficient
            if (sourceAccount.getBalance() < amount) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Insufficient balance."
                ));
            }
        }

        // 4. Stored XSS Check
        String finalDescription = description;
        if (!settings.isXssEnabled()) {
            // Secure Mode XSS Mitigation: Escape HTML output
            finalDescription = HtmlUtils.htmlEscape(description);
            if (isXssPayload(description)) {
                SecurityLog xssLog = SecurityLog.builder()
                        .timestamp(LocalDateTime.now())
                        .attackType("XSS")
                        .endpoint("POST /api/transactions/transfer")
                        .payload("description=" + description)
                        .status("DETECTED_AND_SANITIZED")
                        .clientIp(clientIp)
                        .description("Detected Stored XSS payload in description. HTML entities escaped.")
                        .build();
                securityLogRepository.save(xssLog);
                securityEventPublisher.publish(xssLog);
            }
        }

        // 5. Execute Money Transfer
        // In vulnerable mode, this can bypass the balance checks and accept negative amounts.
        sourceAccount.setBalance(sourceAccount.getBalance() - amount);
        targetAccount.setBalance(targetAccount.getBalance() + amount);

        accountRepository.save(sourceAccount);
        accountRepository.save(targetAccount);

        // Save Transaction
        Transaction tx = Transaction.builder()
                .sourceAccountNumber(sourceAccountNumber)
                .targetAccountNumber(targetAccountNumber)
                .amount(amount)
                .description(finalDescription)
                .timestamp(LocalDateTime.now())
                .status("SUCCESS")
                .build();
        transactionRepository.save(tx);

        ResponseEntity<?> successResponse = ResponseEntity.ok(Map.of(
                "message", "Transfer completed successfully",
                "transactionId", tx.getId(),
                "sourceBalance", sourceAccount.getBalance()
        ));
        if (idempotencyKey != null && !idempotencyKey.trim().isEmpty()) {
            idempotencyMap.put(idempotencyKey, successResponse);
        }
        return successResponse;
    }

    @SuppressWarnings("unchecked")
    @GetMapping("/history")
    public ResponseEntity<?> getTransactionHistory(
            @RequestParam String accountNumber,
            @RequestParam(required = false) String search,
            HttpServletRequest servletRequest) {

        String clientIp = servletRequest.getRemoteAddr();
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();

        Account myAccount = accountRepository.findByAccountNumber(accountNumber).orElse(null);
        if (myAccount == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Account not found"));
        }

        SecuritySettings settings = SecuritySettings.getInstance();

        // Security check for reading history in secure mode (IDOR prevention)
        if (!settings.isIdorEnabled()) {
            if (!myAccount.getUser().getUsername().equalsIgnoreCase(currentUsername)) {
                SecurityLog idorLog = SecurityLog.builder()
                        .timestamp(LocalDateTime.now())
                        .attackType("IDOR/BOLA")
                        .endpoint("GET /api/transactions/history?accountNumber=" + accountNumber)
                        .payload("Requested: " + accountNumber + " | Authenticated: " + currentUsername)
                        .status("BLOCKED")
                        .clientIp(clientIp)
                        .description("Blocked reading transaction history for another user's account.")
                        .build();
                securityLogRepository.save(idorLog);
                securityEventPublisher.publish(idorLog);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied."));
            }
        }

        List<Transaction> transactions = new ArrayList<>();

        if (search == null || search.trim().isEmpty()) {
            // General history lookup
            transactions = transactionRepository
                    .findBySourceAccountNumberOrTargetAccountNumberOrderByTimestampDesc(accountNumber, accountNumber);
        } else {
            // Filtered history lookup
            if (settings.isSqliEnabled()) {
                // Vulnerable Mode: Raw SQL String Concatenation!
                // This allows bypassing the account scope and query details of other accounts if search is 1' OR '1'='1
                String sql = "SELECT * FROM transactions WHERE (source_account_number = '" + accountNumber + 
                             "' OR target_account_number = '" + accountNumber + "') AND LOWER(description) LIKE '%" + search.toLowerCase() + "%' ORDER BY timestamp DESC";
                try {
                    Query query = entityManager.createNativeQuery(sql, Transaction.class);
                    transactions = query.getResultList();
                } catch (Exception e) {
                    // Log SQL error and return empty to avoid crashing
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                            "error", "Database query execution error (potential SQL injection syntax error)",
                            "sqlErrorDetails", e.getMessage()
                    ));
                }
            } else {
                // Secure Mode: Safe Query Parameters
                // Check for SQLi attempt to log and reject the request
                if (isSqlInjectionSearchPayload(search)) {
                    SecurityLog sqliLog = SecurityLog.builder()
                            .timestamp(LocalDateTime.now())
                            .attackType("SQL_INJECTION")
                            .endpoint("GET /api/transactions/history?search=" + search)
                            .payload("search=" + search)
                            .status("BLOCKED")
                            .clientIp(clientIp)
                            .description("Blocked SQL Injection attempt in transaction history search. Request was REJECTED.")
                            .build();
                    securityLogRepository.save(sqliLog);
                    securityEventPublisher.publish(sqliLog);
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid request parameter"));
                }
                transactions = transactionRepository.searchTransactionsSecure(accountNumber, search);
            }
        }

        return ResponseEntity.ok(transactions);
    }

    private boolean isXssPayload(String input) {
        if (input == null) return false;
        String lower = input.toLowerCase();
        return lower.contains("<script") || lower.contains("javascript:") || lower.contains("onload=") || lower.contains("onerror=");
    }

    private boolean isSqlInjectionSearchPayload(String input) {
        if (input == null) return false;
        String lower = input.toLowerCase();
        return lower.contains("'") || lower.contains("--") || lower.contains("/*") || lower.contains(" or ") || lower.contains(" union ");
    }
}
