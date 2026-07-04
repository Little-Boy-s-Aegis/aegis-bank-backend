package com.example.bank.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "security_logs")
public class SecurityLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime timestamp;
    private String attackType;
    private String endpoint;
    
    @Column(length = 2000)
    private String payload;
    
    private String status;
    private String clientIp;
    
    @Column(length = 1000)
    private String description;

    public SecurityLog() {}

    public SecurityLog(Long id, LocalDateTime timestamp, String attackType, String endpoint, String payload, String status, String clientIp, String description) {
        this.id = id;
        this.timestamp = timestamp;
        this.attackType = attackType;
        this.endpoint = endpoint;
        this.payload = payload;
        this.status = status;
        this.clientIp = clientIp;
        this.description = description;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public String getAttackType() { return attackType; }
    public void setAttackType(String attackType) { this.attackType = attackType; }

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getClientIp() { return clientIp; }
    public void setClientIp(String clientIp) { this.clientIp = clientIp; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    // Builder
    public static class SecurityLogBuilder {
        private Long id;
        private LocalDateTime timestamp;
        private String attackType;
        private String endpoint;
        private String payload;
        private String status;
        private String clientIp;
        private String description;

        public SecurityLogBuilder id(Long id) { this.id = id; return this; }
        public SecurityLogBuilder timestamp(LocalDateTime timestamp) { this.timestamp = timestamp; return this; }
        public SecurityLogBuilder attackType(String attackType) { this.attackType = attackType; return this; }
        public SecurityLogBuilder endpoint(String endpoint) { this.endpoint = endpoint; return this; }
        public SecurityLogBuilder payload(String payload) { this.payload = payload; return this; }
        public SecurityLogBuilder status(String status) { this.status = status; return this; }
        public SecurityLogBuilder clientIp(String clientIp) { this.clientIp = clientIp; return this; }
        public SecurityLogBuilder description(String description) { this.description = description; return this; }

        public SecurityLog build() {
            return new SecurityLog(id, timestamp, attackType, endpoint, payload, status, clientIp, description);
        }
    }

    public static SecurityLogBuilder builder() {
        return new SecurityLogBuilder();
    }
}
