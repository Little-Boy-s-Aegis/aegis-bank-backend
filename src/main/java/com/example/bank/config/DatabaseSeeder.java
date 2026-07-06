package com.example.bank.config;

import com.example.bank.model.Account;
import com.example.bank.model.Transaction;
import com.example.bank.model.User;
import com.example.bank.repository.AccountRepository;
import com.example.bank.repository.TransactionRepository;
import com.example.bank.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

@Component
public class DatabaseSeeder implements CommandLineRunner {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        if (userRepository.count() == 0) {
            // Create Admin
            User admin = User.builder()
                    .username("admin")
                    .password(passwordEncoder.encode("admin123"))
                    .fullName("System Administrator")
                    .email("admin@demo-bank.com")
                    .role("ADMIN")
                    .build();
            userRepository.save(admin);

            // Create Alice
            User alice = User.builder()
                    .username("alice")
                    .password(passwordEncoder.encode("password123"))
                    .fullName("Alice Smith")
                    .email("alice@demo-bank.com")
                    .role("USER")
                    .build();
            userRepository.save(alice);

            Account aliceAccount = Account.builder()
                    .accountNumber("ACC-123456")
                    .balance(15000000.00)
                    .currency("VND")
                    .user(alice)
                    .build();
            accountRepository.save(aliceAccount);

            // Create Bob
            User bob = User.builder()
                    .username("bob")
                    .password(passwordEncoder.encode("password123"))
                    .fullName("Bob Jones")
                    .email("bob@demo-bank.com")
                    .role("USER")
                    .build();
            userRepository.save(bob);

            Account bobAccount = Account.builder()
                    .accountNumber("ACC-987654")
                    .balance(25000000.00)
                    .currency("VND")
                    .user(bob)
                    .build();
            accountRepository.save(bobAccount);

            // Add initial transactions
            Transaction t1 = Transaction.builder()
                    .sourceAccountNumber("ACC-123456")
                    .targetAccountNumber("ACC-987654")
                    .amount(500000.00)
                    .description("Tra tien an toi qua")
                    .timestamp(LocalDateTime.now().minusDays(3))
                    .status("SUCCESS")
                    .build();
            transactionRepository.save(t1);

            Transaction t2 = Transaction.builder()
                    .sourceAccountNumber("ACC-987654")
                    .targetAccountNumber("ACC-123456")
                    .amount(1000000.00)
                    .description("Chuyen tien qua sinh nhat Alice")
                    .timestamp(LocalDateTime.now().minusDays(2))
                    .status("SUCCESS")
                    .build();
            transactionRepository.save(t2);

            Transaction t3 = Transaction.builder()
                    .sourceAccountNumber("ACC-123456")
                    .targetAccountNumber("ACC-000999") // Electricity grid
                    .amount(320000.00)
                    .description("Thanh toan tien dien thang 6")
                    .timestamp(LocalDateTime.now().minusDays(1))
                    .status("SUCCESS")
                    .build();
            transactionRepository.save(t3);

            System.out.println("Demo database seeded successfully!");
        }
    }
}
