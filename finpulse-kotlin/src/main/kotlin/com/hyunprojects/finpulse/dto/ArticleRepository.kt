package com.hyunprojects.finpulse.dto

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface ArticleRepository : JpaRepository<AnalyzedArticle, Long>, JpaSpecificationExecutor<AnalyzedArticle> {

    fun existsByUrl(url: String): Boolean

    fun findByTicker(ticker: String, pageable: Pageable): Page<AnalyzedArticle>

    @Query(
        value = """
            SELECT DATE(published_at) AS date,
                   AVG(sentiment_score) AS avgScore,
                   COUNT(*) AS articleCount,
                   SUM(CASE WHEN sentiment = 'positive' THEN 1 ELSE 0 END) AS positiveCount,
                   SUM(CASE WHEN sentiment = 'negative' THEN 1 ELSE 0 END) AS negativeCount,
                   SUM(CASE WHEN sentiment = 'neutral'  THEN 1 ELSE 0 END) AS neutralCount
            FROM analyzed_articles
            WHERE ticker = :ticker AND published_at >= :since
            GROUP BY DATE(published_at)
            ORDER BY DATE(published_at) ASC
        """,
        nativeQuery = true
    )
    fun findDailySentiment(
        @Param("ticker") ticker: String,
        @Param("since") since: Instant
    ): List<DailySentimentProjection>

    @Query(
        value = """
            SELECT ticker, COUNT(*) AS mentionCount
            FROM analyzed_articles
            WHERE published_at >= :since
            GROUP BY ticker
            ORDER BY mentionCount DESC
        """,
        nativeQuery = true
    )
    fun findTrendingByMentions(
        @Param("since") since: Instant,
        pageable: Pageable
    ): List<TrendingProjection>

    @Query(
        value = """
            SELECT ticker,
                   AVG(CASE WHEN published_at >= :recentFrom THEN sentiment_score END) AS recentScore,
                   AVG(CASE WHEN published_at < :recentFrom THEN sentiment_score END)  AS previousScore
            FROM analyzed_articles
            WHERE published_at >= :prevFrom
            GROUP BY ticker
            HAVING recentScore IS NOT NULL AND previousScore IS NOT NULL
            ORDER BY ABS(recentScore - previousScore) DESC
        """,
        nativeQuery = true
    )
    fun findMovers(
        @Param("recentFrom") recentFrom: Instant,
        @Param("prevFrom") prevFrom: Instant,
        pageable: Pageable
    ): List<MoverProjection>
}
