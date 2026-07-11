package com.example.bank.service;

import com.example.bank.model.SecurityEvent;
import com.example.bank.model.SecurityLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Service responsible for publishing security events to Kafka.
 * Acts as the bridge between the banking backend's SecurityLog persistence
 * and the real-time event streaming infrastructure.
 *
 * Events are published to the "aegis.security.events" topic, keyed by attack type
 * for partition-level ordering of related events.
 *
 * If Kafka is not configured (e.g., in test environment), the publisher
 * gracefully degrades to a no-op logger.
 */
@Service
public class SecurityEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(SecurityEventPublisher.class);
    private static final String TOPIC = "aegis.security.events";

    private final KafkaTemplate<String, SecurityEvent> kafkaTemplate;

    /**
     * Constructor supports optional KafkaTemplate injection.
     * When Kafka auto-configuration is excluded (e.g., in tests),
     * kafkaTemplate will be null and publish() becomes a no-op.
     */
    public SecurityEventPublisher(
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            KafkaTemplate<String, SecurityEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
        if (kafkaTemplate == null) {
            log.warn("[Kafka] KafkaTemplate not available. Security events will NOT be published to Kafka.");
        } else {
            log.info("[Kafka] SecurityEventPublisher initialized. Publishing to topic: {}", TOPIC);
        }
    }

    /**
     * Publishes a security event derived from a SecurityLog entity.
     * The event is sent asynchronously; failures are logged but do not
     * propagate to the caller to avoid disrupting the main request flow.
     *
     * @param securityLog the persisted security log entry to publish
     */
    public void publish(SecurityLog securityLog) {
        log.info("[SecurityLog] Processing security event: type={} clientIp={} endpoint={} status={}",
                securityLog.getAttackType(),
                securityLog.getClientIp(),
                securityLog.getEndpoint(),
                securityLog.getStatus());

        if (kafkaTemplate == null) {
            log.debug("[Kafka] Skipping publish (KafkaTemplate not available): type={}", securityLog.getAttackType());
            return;
        }

        try {
            SecurityEvent event = SecurityEvent.fromSecurityLog(securityLog);
            CompletableFuture<SendResult<String, SecurityEvent>> future =
                    kafkaTemplate.send(TOPIC, event.getAttackType(), event);

            future.whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("[Kafka] Failed to publish security event: {}", ex.getMessage());
                } else {
                    log.info("[Kafka] Published security event [{}] type={} clientIp={} to partition={} offset={}",
                            event.getEventId(),
                            event.getAttackType(),
                            event.getClientIp(),
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset());
                }
            });
        } catch (Exception e) {
            // Kafka publish should never break the main business flow
            log.error("[Kafka] Error preparing security event for publish: {}", e.getMessage());
        }
    }
}
