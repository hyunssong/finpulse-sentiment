package com.hyunprojects.finpulse.kafka

import com.hyunprojects.finpulse.kafka.dto.ArticleEvent
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import kotlin.math.abs

/**
 * Producer that sends the fetched article to Kafka raw-articles topic with the ticker as key
 * To avoid large message, has a char limit for the article summary field
 */
@Component
class NewsProducer(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val objectMapper: ObjectMapper,
    @Value("\${finpulse.kafka.salt-buckets:3}") private val saltBuckets: Int
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val SUMMARY_MAX_CHARS = 2_000
    }

    fun send(event: ArticleEvent) {
        val safe = if (event.summary.length > SUMMARY_MAX_CHARS)
            event.copy(summary = event.summary.take(SUMMARY_MAX_CHARS))
        else event

        val json = objectMapper.writeValueAsString(safe)
        val key = partitionKey(event.ticker, event.url)

        kafkaTemplate.send("raw-articles", key, json)
            .whenComplete { result, ex ->
                if (ex != null) {
                    log.error("Failed to send article: url={}", event.url, ex)
                } else {
                    log.debug("Sent: ticker={} key={} offset={}", event.ticker, key, result.recordMetadata.offset())
                }
            }
    }

    private fun partitionKey(ticker: String, url: String): String =
        "${ticker}_${abs(url.hashCode()) % saltBuckets}"
}
