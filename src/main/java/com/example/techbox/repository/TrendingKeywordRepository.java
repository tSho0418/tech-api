package com.example.techbox.repository;

import com.example.techbox.domain.TrendingKeyword;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrendingKeywordRepository extends JpaRepository<TrendingKeyword, UUID> {

    List<TrendingKeyword> findByDateOrderByCountDesc(LocalDate date);

    Optional<TrendingKeyword> findByKeywordAndDate(String keyword, LocalDate date);

    @Query("SELECT t FROM TrendingKeyword t WHERE t.keyword = :keyword ORDER BY t.date DESC")
    List<TrendingKeyword> findHistoryByKeyword(@Param("keyword") String keyword);

    @Query("SELECT t FROM TrendingKeyword t WHERE t.date >= :since ORDER BY t.date DESC, t.count DESC")
    List<TrendingKeyword> findByDateSince(@Param("since") LocalDate since);

    // 期間集計 (keyword ごとに count を合算し降順)
    @Query("SELECT t.keyword, SUM(t.count) FROM TrendingKeyword t WHERE t.date >= :since GROUP BY t.keyword ORDER BY SUM(t.count) DESC")
    List<Object[]> sumCountByKeywordSince(@Param("since") LocalDate since, Pageable pageable);
}
