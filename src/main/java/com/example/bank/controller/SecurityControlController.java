package com.example.bank.controller;

import com.example.bank.model.SecurityLog;
import com.example.bank.model.SecuritySettings;
import com.example.bank.repository.SecurityLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/security")
public class SecurityControlController {

    @org.springframework.beans.factory.annotation.Value("${aegis.security.sync-token}")
    private String syncToken;

    @Autowired
    private SecurityLogRepository securityLogRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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

    private boolean hasSyncToken(String token) {
        return token != null && token.equals(syncToken);
    }

    private ResponseEntity<?> syncOrAdminRequired(String token, org.springframework.security.core.Authentication auth) {
        if (hasSyncToken(token) || isAdmin(auth)) {
            return null;
        }
        org.springframework.http.HttpStatus status = isAnonymous(auth)
                ? org.springframework.http.HttpStatus.UNAUTHORIZED
                : org.springframework.http.HttpStatus.FORBIDDEN;
        return ResponseEntity.status(status)
                .body(Map.of("error", "Admin role or valid Aegis Sync Token required."));
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
        boolean validSyncToken = hasSyncToken(token);
        if (!validSyncToken && !isAdmin(auth)) {
            org.springframework.http.HttpStatus status = isAnonymous(auth)
                    ? org.springframework.http.HttpStatus.UNAUTHORIZED
                    : org.springframework.http.HttpStatus.FORBIDDEN;
            return ResponseEntity.status(status)
                    .body(Map.of("error", "Admin role or valid Aegis Sync Token required."));
        }
        return ResponseEntity.ok(securityLogRepository.findAllByOrderByTimestampDesc());
    }

    @GetMapping("/banned-ips")
    public ResponseEntity<?> getBannedIps(@RequestHeader(value = "X-Aegis-Token", required = false) String token) {
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        ResponseEntity<?> authCheck = syncOrAdminRequired(token, auth);
        if (authCheck != null) {
            return authCheck;
        }

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT ip_address, banned_at, banned_by, status, reason FROM banned_ips ORDER BY banned_at DESC"
        );
        return ResponseEntity.ok(rows);
    }

    @PostMapping("/banned-ips")
    public ResponseEntity<?> upsertBannedIp(
            @RequestHeader(value = "X-Aegis-Token", required = false) String token,
            @RequestBody Map<String, Object> payload
    ) {
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        ResponseEntity<?> authCheck = syncOrAdminRequired(token, auth);
        if (authCheck != null) {
            return authCheck;
        }

        String ipAddress = stringField(payload, "ipAddress");
        String status = stringField(payload, "status");
        String bannedBy = stringField(payload, "bannedBy");
        String reason = stringField(payload, "reason");

        if (ipAddress == null || ipAddress.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "ipAddress is required"));
        }
        ipAddress = normalizeIpExpression(ipAddress);
        if (ipAddress == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "ipAddress must be a valid IP or CIDR range"));
        }

        // Extract IP part from CIDR expression for private check
        String ipPart = ipAddress;
        if (ipPart.contains("/")) {
            ipPart = ipPart.split("/", 2)[0].trim();
        }
        try {
            InetAddress inet = InetAddress.getByName(ipPart);
            if (inet.isLoopbackAddress() || inet.isSiteLocalAddress() || inet.isAnyLocalAddress()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Cannot ban private or loopback IP range: " + ipAddress));
            }
        } catch (Exception ignored) {}

        if (status == null || status.isBlank()) {
            status = "active";
        }
        if (!status.equals("active") && !status.equals("unbanned")) {
            return ResponseEntity.badRequest().body(Map.of("error", "status must be active or unbanned"));
        }
        if (bannedBy == null || bannedBy.isBlank()) {
            bannedBy = hasSyncToken(token) ? "Aegis Sync" : "Admin";
        }
        if (reason == null || reason.isBlank()) {
            reason = status.equals("active") ? "Synchronized IP block" : "Synchronized IP unblock";
        }

        if ("unbanned".equals(status)) {
            jdbcTemplate.update("DELETE FROM banned_ips WHERE ip_address = ?", ipAddress);
        } else {
            int updated = jdbcTemplate.update(
                    "UPDATE banned_ips SET banned_at = ?, banned_by = ?, status = ?, reason = ? WHERE ip_address = ?",
                    LocalDateTime.now(), bannedBy, status, reason, ipAddress
            );
            if (updated == 0) {
                jdbcTemplate.update(
                        "INSERT INTO banned_ips (ip_address, banned_at, banned_by, status, reason) VALUES (?, ?, ?, ?, ?)",
                        ipAddress, LocalDateTime.now(), bannedBy, status, reason
                );
            }
        }

        return ResponseEntity.ok(Map.of(
                "ipAddress", ipAddress,
                "status", status,
                "message", status.equals("active") ? "IP ban synchronized" : "IP unban synchronized"
        ));
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

    private String stringField(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value == null ? null : value.toString().trim();
    }

    private String normalizeIpExpression(String raw) {
        String value = raw.trim();
        if (value.toLowerCase().startsWith("ip ")) {
            value = value.substring(3).trim();
        }
        if (value.contains("/")) {
            String[] parts = value.split("/", 2);
            if (parts.length != 2 || !isValidIp(parts[0].trim())) {
                return null;
            }
            try {
                int prefix = Integer.parseInt(parts[1].trim());
                int maxPrefix = InetAddress.getByName(parts[0].trim()).getAddress().length * 8;
                if (prefix < 0 || prefix > maxPrefix) {
                    return null;
                }
                return InetAddress.getByName(parts[0].trim()).getHostAddress() + "/" + prefix;
            } catch (Exception ignored) {
                return null;
            }
        }
        if (!isValidIp(value)) {
            return null;
        }
        try {
            return InetAddress.getByName(value).getHostAddress();
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean isValidIp(String value) {
        try {
            InetAddress.getByName(value);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
