package com.hyunprojects.finpulse.ingestion

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import tools.jackson.databind.ObjectMapper
import java.time.LocalDate

@Component
class TwelveDataClient(
    @Value("\${twelvedata.api.key}") private val apiKey: String,
    @Value("\${twelvedata.base.url:https://api.twelvedata.com}") private val baseUrl: String,
    // 31 days: index 0 = today, indices 1-30 = 30-day history for average
    @Value("\${finpulse.volume.history-days:31}") private val historyDays: Int,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val restClient = RestClient.builder().build()

    // Returns (date, volume) pairs sorted descending (latest first), as returned by the API.
    fun fetchDailyVolumes(ticker: String): List<Pair<LocalDate, Long>> {
        val json = restClient.get()
            .uri("$baseUrl/time_series?symbol=$ticker&interval=1day&outputsize=$historyDays&apikey=$apiKey")
            .retrieve()
            .body(String::class.java) ?: return emptyList()

        val node = objectMapper.readTree(json)
        if (node["status"]?.asText() != "ok") {
            log.warn("Twelve Data non-ok status for {}: {}", ticker, node["message"]?.asText())
            return emptyList()
        }

        val values = node["values"] ?: return emptyList()
        return values.mapNotNull { entry ->
            val datetimeStr = entry["datetime"]?.asText() ?: return@mapNotNull null
            val volume = entry["volume"]?.asText()?.toLongOrNull() ?: return@mapNotNull null
            // datetime is "YYYY-MM-DD" for 1day interval; take first 10 chars to be safe
            val date = runCatching { LocalDate.parse(datetimeStr.take(10)) }.getOrNull()
                ?: return@mapNotNull null
            date to volume
        }
    }
}
