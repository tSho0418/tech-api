package com.example.techbox.repository;

import com.example.techbox.domain.BatchLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BatchLogRepository extends JpaRepository<BatchLog, UUID> {

    Optional<BatchLog> findFirstByOrderByStartedAtDesc();

    @Query("SELECT b FROM BatchLog b ORDER BY b.startedAt DESC")
    List<BatchLog> findRecentLogs(Pageable pageable);
}
