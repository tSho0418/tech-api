package com.example.techbox.repository;

import com.example.techbox.domain.Article;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface ArticleRepository extends JpaRepository<Article, UUID> {

    boolean existsByUrl(String url);

    @Query("SELECT a FROM Article a WHERE a.fetchedAt >= :start AND a.fetchedAt < :end ORDER BY a.score DESC NULLS LAST")
    List<Article> findTodayArticles(@Param("start") OffsetDateTime start, @Param("end") OffsetDateTime end);

    @Query("SELECT a FROM Article a WHERE a.fetchedAt >= :start AND a.fetchedAt < :end")
    Page<Article> findByDateRange(@Param("start") OffsetDateTime start, @Param("end") OffsetDateTime end, Pageable pageable);

    @Query("SELECT a FROM Article a JOIN a.source s WHERE s.name = :sourceName AND a.fetchedAt >= :start AND a.fetchedAt < :end")
    Page<Article> findBySourceNameAndDateRange(@Param("sourceName") String sourceName,
                                               @Param("start") OffsetDateTime start,
                                               @Param("end") OffsetDateTime end,
                                               Pageable pageable);

    @Query("SELECT a FROM Article a WHERE a.notified = false ORDER BY a.score DESC NULLS LAST")
    List<Article> findUnnotifiedOrderByScoreDesc(Pageable pageable);

    // ソース別件数（trending/sources 用）
    @Query("SELECT a.source.name, COUNT(a) FROM Article a WHERE a.fetchedAt >= :start AND a.fetchedAt < :end GROUP BY a.source.name")
    List<Object[]> countBySourceForPeriod(@Param("start") OffsetDateTime start, @Param("end") OffsetDateTime end);

    // ダッシュボード統計用
    long countByFetchedAtBetween(OffsetDateTime start, OffsetDateTime end);
}
