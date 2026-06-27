package com.example.techbox.repository;

import com.example.techbox.domain.Summary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SummaryRepository extends JpaRepository<Summary, UUID> {

    Optional<Summary> findByArticleId(UUID articleId);
}
