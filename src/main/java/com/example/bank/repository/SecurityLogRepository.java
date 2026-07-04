package com.example.bank.repository;

import com.example.bank.model.SecurityLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SecurityLogRepository extends JpaRepository<SecurityLog, Long> {
    List<SecurityLog> findAllByOrderByTimestampDesc();
}
