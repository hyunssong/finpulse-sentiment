package com.hyunprojects.finpulse.ingestion

import com.hyunprojects.finpulse.ingestion.dto.*
import com.hyunprojects.finpulse.kafka.dto.ArticleEvent
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import tools.jackson.databind.ObjectMapper
import java.time.LocalDate

@Component
class NewsDataApiClient(@Value("\${newsdata.api.key}") private val apiKey:String,
                        @Value("\${newsdata.base.url}") private val baseUrl:String,
                        private val objectMapper: ObjectMapper   // Spring auto-provides this
): ApiClient {
    private val restClient = RestClient.builder()
        .baseUrl(baseUrl)
        .build()

    override fun fetchArticles(symbol: String, from: LocalDate, to: LocalDate): List<ArticleEvent> {
        val json = restClient.get()
            .uri("?symbol={symbol}&apikey={apiKey}", symbol, apiKey)
            .retrieve()
            .body(String::class.java)
            ?: return emptyList()

        val articles: NewsDataResponse = objectMapper.readValue(
            json,
            NewsDataResponse::class.java
        )

        return articles.results.map { it.toArticle(symbol) }
    }
}