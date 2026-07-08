package com.hyunprojects.finpulse.ingestion

import com.hyunprojects.finpulse.kafka.dto.ArticleEvent
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import tools.jackson.databind.ObjectMapper
import com.hyunprojects.finpulse.ingestion.dto.FinnhubArticle
import com.hyunprojects.finpulse.ingestion.dto.FinnhubSymbol
import java.time.LocalDate

/**
 * Component to fetch news from Finnhub API
 */
@Component
class FinnhubApiClient(
    // dependency injection through constructor of class
    @Value("\${finnhub.api.key}") private val apiKey: String,
    @Value("\${finnhub.base.url}") private val baseUrl: String,
    private val objectMapper: ObjectMapper
) : ApiClient {

    private val restClient = RestClient.builder()
        .baseUrl(baseUrl)
        .build()

    /**
     * Fetch news articles related to a specific ticker
     */
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

    /**
     * Fetch all symbols for US (expanding on the default tickers)
     */
    fun fetchSymbols(exchange: String = "US"): List<FinnhubSymbol> {
        val json = restClient.get()
            .uri("/stock/symbol?exchange={exchange}&token={apiKey}", exchange, apiKey)
            .retrieve()
            .body(String::class.java)
            ?: return emptyList()

        return objectMapper.readValue(
            json,
            objectMapper.typeFactory.constructCollectionType(List::class.java, FinnhubSymbol::class.java)
        )
    }
}