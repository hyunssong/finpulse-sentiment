package com.hyunprojects.finpulse.kafka

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

@Component
class FinbertClient(
    @Value("\${finpulse.finbert.url:http://localhost:8000}") baseUrl: String
) {
    data class AnalysisResult(val sentiment: String, val sentimentScore: Double)

    private val log = LoggerFactory.getLogger(javaClass)
    private val client = WebClient.builder().baseUrl(baseUrl).build()

    fun analyze(title: String, summary: String): AnalysisResult {
        log.debug("Calling FinBERT for title=\"{}\"", title)
        return client.post()
            .uri("/analyze")
            .bodyValue(mapOf("title" to title, "summary" to summary))
            .retrieve()
            .bodyToMono(AnalysisResult::class.java)
            .block() ?: error("Empty response from FinBERT service")
    }
}
