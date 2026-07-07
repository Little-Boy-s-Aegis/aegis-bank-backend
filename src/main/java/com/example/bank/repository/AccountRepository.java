package com.example.bank.repository;

import com.example.bank.model.Account;
import com.example.bank.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {
    Optional<Account> findByAccountNumber(String accountNumber);
    Optional<Account> findByUser(User user);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select a from Account a where a.accountNumber = :accountNumber")
    Optional<Account> findByAccountNumberWithLock(@org.springframework.data.repository.query.Param("accountNumber") String accountNumber);
}
