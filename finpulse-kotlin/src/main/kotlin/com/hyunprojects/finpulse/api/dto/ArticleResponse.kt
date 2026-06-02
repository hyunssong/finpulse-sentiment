package com.hyunprojects.finpulse.api.dto

import com.hyunprojects.finpulse.dto.AnalyzedArticle
import java.time.Instant

data class ArticleResponse(
    val id: Long,
    val title: String,
    val summary: String,
    val source: String,
    val url: String,
    val publishedAt: Instant,
    val ticker: String,
    val sentiment: String,
    val sentimentScore: Double
)

fun AnalyzedArticle.toResponse() = ArticleResponse(
    id = id, title = title, summary = summary, source = source, url = url,
    publishedAt = publishedAt, ticker = ticker, sentiment = sentiment, sentimentScore = sentimentScore
)
