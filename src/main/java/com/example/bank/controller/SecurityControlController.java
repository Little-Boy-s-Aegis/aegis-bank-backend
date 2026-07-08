package com.example.bank.controller;

import com.example.bank.model.SecurityLog;
import com.example.bank.model.SecuritySettings;
import com.example.bank.repository.SecurityLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/security")
public class SecurityControlController {

    @org.springframework.beans.factory.annotation.Value("${aegis.security.sync-token}")
    private String syncToken;

    @Autowired
    private SecurityLogRepository securityLogRepository;

    @GetMapping("/status")
    public ResponseEntity<SecuritySettings> getSecurityStatus() {
        return ResponseEntity.ok(SecuritySettings.getInstance());
    }

    private ResponseEntity<?> adminRequired(org.springframework.security.core.Authentication auth, String message) {
        if (isAdmin(auth)) {
            return null;
        }
        org.springframework.http.HttpStatus status = isAnonymous(auth)
                ? org.springframework.http.HttpStatus.UNAUTHORIZED
                : org.springframework.http.HttpStatus.FORBIDDEN;
        return ResponseEntity.status(status).body(Map.of("error", message));
    }

    private boolean isAdmin(org.springframework.security.core.Authentication auth) {
        return auth != null && auth.isAuthenticated() && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    private boolean isAnonymous(org.springframework.security.core.Authentication auth) {
        return auth == null || !auth.isAuthenticated() || auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken;
    }

    @PostMapping("/toggle")
    public ResponseEntity<?> toggleSecuritySetting(@RequestBody Map<String, Object> payload) {
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        ResponseEntity<?> adminCheck = adminRequired(auth, "Admin role required to toggle security settings.");
        if (adminCheck != null) {
            return adminCheck;
        }

        String vulnerability = (String) payload.get("vulnerability");
        Boolean enabled = (Boolean) payload.get("enabled");

        if (vulnerability == null || enabled == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing vulnerability or enabled fields"));
        }

        SecuritySettings settings = SecuritySettings.getInstance();
        switch (vulnerability.toUpperCase()) {
            case "SQLI":
                settings.setSqliEnabled(enabled);
                break;
            case "XSS":
                settings.setXssEnabled(enabled);
                break;
            case "IDOR":
                settings.setIdorEnabled(enabled);
                break;
            case "PARAM_TAMPERING":
                settings.setParamTamperingEnabled(enabled);
                break;
            case "BRUTE_FORCE":
                settings.setBruteForceEnabled(enabled);
                break;
            default:
                return ResponseEntity.badRequest().body(Map.of("error", "Unknown vulnerability type"));
        }

        return ResponseEntity.ok(Map.of(
                "vulnerability", vulnerability,
                "enabled", enabled
        ));
    }

    @GetMapping("/logs")
    public ResponseEntity<?> getSecurityLogs(@RequestHeader(value = "X-Aegis-Token", required = false) String token) {
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        boolean validSyncToken = token != null && token.equals(syncToken);
        if (!validSyncToken && !isAdmin(auth)) {
            org.springframework.http.HttpStatus status = isAnonymous(auth)
                    ? org.springframework.http.HttpStatus.UNAUTHORIZED
                    : org.springframework.http.HttpStatus.FORBIDDEN;
            return ResponseEntity.status(status)
                    .body(Map.of("error", "Admin role or valid Aegis Sync Token required."));
        }
        return ResponseEntity.ok(securityLogRepository.findAllByOrderByTimestampDesc());
    }

    @PostMapping("/logs/clear")
    public ResponseEntity<?> clearLogs() {
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        ResponseEntity<?> adminCheck = adminRequired(auth, "Admin role required to clear logs.");
        if (adminCheck != null) {
            return adminCheck;
        }
        securityLogRepository.deleteAll();
        return ResponseEntity.ok(Map.of("message", "Security logs cleared"));
    }
}
