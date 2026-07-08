package com.hyunprojects.finpulse.kafka

import com.hyunprojects.finpulse.kafka.dto.AnalyzedArticleEvent
import com.hyunprojects.finpulse.kafka.dto.ArticleEvent
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import kotlin.math.abs

@Component
class SentimentProcessor(
    private val objectMapper: ObjectMapper,
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val finbertClient: FinbertClient,
    @Value("\${finpulse.kafka.salt-buckets:3}") private val saltBuckets: Int
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(topics = ["raw-articles"], groupId = "finpulse-sentiment")
    fun process(message: String) {
        try {
            val event = objectMapper.readValue(message, ArticleEvent::class.java)
            val result = finbertClient.analyze(event.title, event.summary)
            val analyzed = AnalyzedArticleEvent(
                title = event.title,
                summary = event.summary,
                source = event.source,
                url = event.url,
                publishedAt = event.publishedAt,
                ticker = event.ticker,
                sentiment = result.sentiment,
                sentimentScore = result.sentimentScore
            )
            val json = objectMapper.writeValueAsString(analyzed)
            val key = "${event.ticker}_${abs(event.url.hashCode()) % saltBuckets}"
            kafkaTemplate.send("analyzed-articles", key, json)
            log.debug("Scored: ticker={} sentiment={} score={}", event.ticker, result.sentiment, result.sentimentScore)
        } catch (e: Exception) {
            log.error("Failed to process raw article", e)
        }
    }
}
