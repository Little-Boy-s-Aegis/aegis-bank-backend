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
@CrossOrigin(origins = "*")
public class SecurityControlController {

    @Autowired
    private SecurityLogRepository securityLogRepository;

    @GetMapping("/status")
    public ResponseEntity<SecuritySettings> getSecurityStatus() {
        return ResponseEntity.ok(SecuritySettings.getInstance());
    }

    @PostMapping("/toggle")
    public ResponseEntity<?> toggleSecuritySetting(@RequestBody Map<String, Object> payload) {
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
    public ResponseEntity<List<SecurityLog>> getSecurityLogs() {
        return ResponseEntity.ok(securityLogRepository.findAllByOrderByTimestampDesc());
    }

    @PostMapping("/logs/clear")
    public ResponseEntity<?> clearLogs() {
        securityLogRepository.deleteAll();
        return ResponseEntity.ok(Map.of("message", "Security logs cleared"));
    }
}
