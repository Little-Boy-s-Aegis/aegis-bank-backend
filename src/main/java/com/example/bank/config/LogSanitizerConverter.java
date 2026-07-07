package com.example.bank.config;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

public class LogSanitizerConverter extends ClassicConverter {
    private static final String DB_PASSWORD = System.getenv("SPRING_DATASOURCE_PASSWORD");
    private static final String JWT_SECRET = System.getenv("JWT_SECRET");
    private static final String SYNC_TOKEN = System.getenv("AEGIS_SECURITY_SYNC_TOKEN");

    @Override
    public String convert(ILoggingEvent event) {
        String msg = event.getFormattedMessage();
        if (msg == null) {
            return "";
        }

        // Redact actual active secret values
        if (DB_PASSWORD != null && !DB_PASSWORD.trim().isEmpty() && DB_PASSWORD.length() > 3) {
            msg = msg.replace(DB_PASSWORD, "[REDACTED_DB_PASSWORD]");
        }
        if (JWT_SECRET != null && !JWT_SECRET.trim().isEmpty() && JWT_SECRET.length() > 3) {
            msg = msg.replace(JWT_SECRET, "[REDACTED_JWT_SECRET]");
        }
        if (SYNC_TOKEN != null && !SYNC_TOKEN.trim().isEmpty() && SYNC_TOKEN.length() > 3) {
            msg = msg.replace(SYNC_TOKEN, "[REDACTED_SYNC_TOKEN]");
        }

        // Redact case-insensitive sensitive terms
        msg = msg.replaceAll("(?i)password", "p*ssword");
        msg = msg.replaceAll("(?i)token", "t*ken");
        msg = msg.replaceAll("(?i)secret", "s*cret");

        return msg;
    }
}
