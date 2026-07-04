package com.example.bank.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "transactions")
public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String sourceAccountNumber;
    private String targetAccountNumber;
    private Double amount;

    @Column(length = 2000)
    private String description;

    private LocalDateTime timestamp;
    private String status; // SUCCESS, FAILED

    public Transaction() {}

    public Transaction(Long id, String sourceAccountNumber, String targetAccountNumber, Double amount, String description, LocalDateTime timestamp, String status) {
        this.id = id;
        this.sourceAccountNumber = sourceAccountNumber;
        this.targetAccountNumber = targetAccountNumber;
        this.amount = amount;
        this.description = description;
        this.timestamp = timestamp;
        this.status = status;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSourceAccountNumber() { return sourceAccountNumber; }
    public void setSourceAccountNumber(String sourceAccountNumber) { this.sourceAccountNumber = sourceAccountNumber; }

    public String getTargetAccountNumber() { return targetAccountNumber; }
    public void setTargetAccountNumber(String targetAccountNumber) { this.targetAccountNumber = targetAccountNumber; }

    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    // Builder
    public static class TransactionBuilder {
        private Long id;
        private String sourceAccountNumber;
        private String targetAccountNumber;
        private Double amount;
        private String description;
        private LocalDateTime timestamp;
        private String status;

        public TransactionBuilder id(Long id) { this.id = id; return this; }
        public TransactionBuilder sourceAccountNumber(String sourceAccountNumber) { this.sourceAccountNumber = sourceAccountNumber; return this; }
        public TransactionBuilder targetAccountNumber(String targetAccountNumber) { this.targetAccountNumber = targetAccountNumber; return this; }
        public TransactionBuilder amount(Double amount) { this.amount = amount; return this; }
        public TransactionBuilder description(String description) { this.description = description; return this; }
        public TransactionBuilder timestamp(LocalDateTime timestamp) { this.timestamp = timestamp; return this; }
        public TransactionBuilder status(String status) { this.status = status; return this; }

        public Transaction build() {
            return new Transaction(id, sourceAccountNumber, targetAccountNumber, amount, description, timestamp, status);
        }
    }

    public static TransactionBuilder builder() {
        return new TransactionBuilder();
    }
}
