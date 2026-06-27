package com.example.techbox.repository;

import com.example.techbox.domain.Source;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SourceRepository extends JpaRepository<Source, UUID> {

    Optional<Source> findByName(String name);

    List<Source> findByActiveTrue();
}
