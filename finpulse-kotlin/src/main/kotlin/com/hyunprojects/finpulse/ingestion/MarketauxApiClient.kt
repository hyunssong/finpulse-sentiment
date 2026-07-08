package com.hyunprojects.finpulse.ingestion

import com.hyunprojects.finpulse.ingestion.dto.MarketauxResponse
import com.hyunprojects.finpulse.kafka.dto.ArticleEvent
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import tools.jackson.databind.ObjectMapper
import java.time.LocalDate

@Component
class MarketauxApiClient(@Value("\${marketaux.api.key}") private val apiKey:String,
                         @Value("\${marketaux.base.url}") private val baseUrl:String,
                         private val objectMapper: ObjectMapper   // Spring auto-provides this
): ApiClient {
    private val restClient = RestClient.builder()
        .baseUrl(baseUrl)
        .build()

    override fun fetchArticles(symbol: String, from: LocalDate, to: LocalDate): List<ArticleEvent> {
        val json = restClient.get()
            .uri("/news/all?symbols={symbol}&published_after={from}&published_before={to}&api_token={apiKey}", symbol, from, to, apiKey)
            .retrieve()
            .body(String::class.java)
            ?: return emptyList()

        val articles: MarketauxResponse = objectMapper.readValue(
            json,
            MarketauxResponse::class.java
        )

        return articles.data.map { it.toArticle(symbol) }
    }
}