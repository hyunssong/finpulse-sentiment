package com.hyunprojects.finpulse.ingestion

import com.hyunprojects.finpulse.kafka.dto.ArticleEvent  // or wherever this class lives
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import tools.jackson.databind.ObjectMapper
import com.hyunprojects.finpulse.ingestion.dto.FinnhubArticle
import java.time.LocalDate

@Component
class FinnhubApiClient(@Value("\${finnhub.api.key}") private val apiKey:String,
                         @Value("\${finnhub.base.url}") private val baseUrl:String,
                    private val objectMapper: ObjectMapper   // Spring auto-provides this
    ): ApiClient {

    private val restClient = RestClient.builder()
        .baseUrl(baseUrl)
        .build()

    override fun fetchArticles(symbol: String, from: LocalDate, to: LocalDate): List<ArticleEvent> {
        val json = restClient.get()
            .uri("/company-news?symbol={symbol}&from={from}&to={to}&token={apiKey}", symbol, from, to, apiKey)
            .retrieve()
            .body(String::class.java)
            ?: return emptyList()

        val articles: List<FinnhubArticle> = objectMapper.readValue(
            json,
            objectMapper.typeFactory.constructCollectionType(List::class.java, FinnhubArticle::class.java)
        )

        return articles.map { it.toArticle(symbol) }
    }
}