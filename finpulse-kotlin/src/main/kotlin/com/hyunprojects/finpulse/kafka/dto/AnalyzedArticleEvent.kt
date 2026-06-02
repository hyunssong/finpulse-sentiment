package com.hyunprojects.finpulse.kafka.dto

import com.hyunprojects.finpulse.dto.AnalyzedArticle
import java.time.Instant

data class AnalyzedArticleEvent(
    val title: String,
    val summary: String,
    val source: String,
    val url: String,
    val publishedAt: Instant,
    val ticker: String,
    val sentiment: String, // positive or negative
    val sentimentScore: Double
) {
    fun toEntity() = AnalyzedArticle(
        title = title,
        summary = summary,
        source = source,
        url = url,
        publishedAt = publishedAt,
        ticker = ticker,
        sentiment = sentiment,
        sentimentScore = sentimentScore
    )
}
