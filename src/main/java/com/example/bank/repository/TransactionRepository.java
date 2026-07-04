package com.example.bank.repository;

import com.example.bank.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findBySourceAccountNumberOrTargetAccountNumberOrderByTimestampDesc(
            String sourceAccountNumber, String targetAccountNumber);

    @Query("SELECT t FROM Transaction t WHERE (t.sourceAccountNumber = :acc OR t.targetAccountNumber = :acc) " +
           "AND LOWER(t.description) LIKE LOWER(CONCAT('%', :search, '%')) ORDER BY t.timestamp DESC")
    List<Transaction> searchTransactionsSecure(@Param("acc") String accountNumber, @Param("search") String search);
}
