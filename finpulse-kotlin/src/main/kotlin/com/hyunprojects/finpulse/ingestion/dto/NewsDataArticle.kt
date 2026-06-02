package com.hyunprojects.finpulse.ingestion.dto

import com.hyunprojects.finpulse.kafka.dto.ArticleEvent
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

data class NewsDataResponse(
    val status: String,
    val totalResults: Int,
    val results: List<NewsDataArticle>
)

data class NewsDataArticle(
    val article_id: String,
    val title: String,
    val link: String,
    val description: String?,
    val source_id: String,
    val pubDate: String
) {
    fun toArticle(symbol: String) = ArticleEvent(
        title = title,
        summary = description ?: "",
        source = source_id,
        url = link,
        publishedAt = LocalDateTime.parse(pubDate, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                .toInstant(ZoneOffset.UTC),
        ticker = symbol
    )
}