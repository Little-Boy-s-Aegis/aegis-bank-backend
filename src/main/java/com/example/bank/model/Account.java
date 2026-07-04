package com.example.bank.model;

import jakarta.persistence.*;

@Entity
@Table(name = "accounts")
public class Account {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String accountNumber;

    @Column(nullable = false)
    private Double balance;

    @Column(nullable = false)
    private String currency; // e.g., VND

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", referencedColumnName = "id")
    private User user;

    public Account() {}

    public Account(Long id, String accountNumber, Double balance, String currency, User user) {
        this.id = id;
        this.accountNumber = accountNumber;
        this.balance = balance;
        this.currency = currency;
        this.user = user;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }

    public Double getBalance() { return balance; }
    public void setBalance(Double balance) { this.balance = balance; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    // Builder
    public static class AccountBuilder {
        private Long id;
        private String accountNumber;
        private Double balance;
        private String currency;
        private User user;

        public AccountBuilder id(Long id) { this.id = id; return this; }
        public AccountBuilder accountNumber(String accountNumber) { this.accountNumber = accountNumber; return this; }
        public AccountBuilder balance(Double balance) { this.balance = balance; return this; }
        public AccountBuilder currency(String currency) { this.currency = currency; return this; }
        public AccountBuilder user(User user) { this.user = user; return this; }

        public Account build() {
            return new Account(id, accountNumber, balance, currency, user);
        }
    }

    public static AccountBuilder builder() {
        return new AccountBuilder();
    }
}
