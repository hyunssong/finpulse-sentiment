package com.hyunprojects.finpulse.kafka

import com.hyunprojects.finpulse.kafka.dto.ArticleEvent
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

@Component
class NewsProducer(
    // auto-created from the application properties
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun send(event: ArticleEvent) {
        val json = objectMapper.writeValueAsString(event)
        kafkaTemplate.send("raw-articles", event.ticker, json)
            .whenComplete { result, ex ->
                if (ex != null) {
                    log.error("Failed to send article: url={}", event.url, ex)
                } else {
                    log.debug("Sent: ticker={} offset={}", event.ticker, result.recordMetadata.offset())
                }
            }
    }
}
