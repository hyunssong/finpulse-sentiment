package com.hyunprojects.finpulse.ingestion

import com.hyunprojects.finpulse.kafka.dto.ArticleEvent
import java.time.LocalDate

/**
 * Interface for classes to fetch articles from a News API Client
 */
interface ApiClient{

    fun fetchArticles(symbol: String, from: LocalDate, to:LocalDate) : List<ArticleEvent>
}