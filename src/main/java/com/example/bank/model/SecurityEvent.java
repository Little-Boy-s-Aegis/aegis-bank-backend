package com.example.bank.model;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO representing a security event published to Kafka.
 * Decoupled from the JPA entity SecurityLog to allow independent evolution
 * of the messaging contract and database schema.
 */
public class SecurityEvent {
    private String eventId;
    private String timestamp;
    private String attackType;
    private String endpoint;
    private String payload;
    private String status;
    private String clientIp;
    private String description;
    private String sourceService;

    public SecurityEvent() {}

    public SecurityEvent(String eventId, String timestamp, String attackType, String endpoint,
                         String payload, String status, String clientIp, String description, String sourceService) {
        this.eventId = eventId;
        this.timestamp = timestamp;
        this.attackType = attackType;
        this.endpoint = endpoint;
        this.payload = payload;
        this.status = status;
        this.clientIp = clientIp;
        this.description = description;
        this.sourceService = sourceService;
    }

    /**
     * Factory method to create a SecurityEvent from a SecurityLog entity.
     */
    public static SecurityEvent fromSecurityLog(SecurityLog log) {
        return new SecurityEvent(
                UUID.randomUUID().toString(),
                log.getTimestamp() != null ? log.getTimestamp().toString() : LocalDateTime.now().toString(),
                log.getAttackType(),
                log.getEndpoint(),
                log.getPayload(),
                log.getStatus(),
                log.getClientIp(),
                log.getDescription(),
                "aegis-bank-backend"
        );
    }

    // Getters and Setters
    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

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

    public String getSourceService() { return sourceService; }
    public void setSourceService(String sourceService) { this.sourceService = sourceService; }
}
