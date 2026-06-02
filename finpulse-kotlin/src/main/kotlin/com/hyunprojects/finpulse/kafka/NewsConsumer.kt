package com.hyunprojects.finpulse.kafka

import com.hyunprojects.finpulse.api.dto.SentimentAlert
import com.hyunprojects.finpulse.dto.ArticleRepository
import com.hyunprojects.finpulse.kafka.dto.AnalyzedArticleEvent
import com.hyunprojects.finpulse.service.SentimentAlertBus
import com.hyunprojects.finpulse.service.SentimentCacheService
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

@Component
class NewsConsumer(
    private val objectMapper: ObjectMapper,
    private val articleRepository: ArticleRepository,
    private val sentimentCacheService: SentimentCacheService,
    private val alertBus: SentimentAlertBus
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(topics = ["analyzed-articles"], groupId = "finpulse-consumer")
    fun consume(message: String) {
        try {
            val event = objectMapper.readValue(message, AnalyzedArticleEvent::class.java)

            if (!articleRepository.existsByUrl(event.url)) {
                articleRepository.save(event.toEntity())
                log.info("Saved: ticker={} sentiment={} score={}", event.ticker, event.sentiment, event.sentimentScore)
                // on high sentiment score, push the event to the in-memory sink
                if (event.sentimentScore >= 0.85) {
                    alertBus.tryEmitNext(
                        SentimentAlert(
                            ticker = event.ticker,
                            title = event.title,
                            sentiment = event.sentiment,
                            sentimentScore = event.sentimentScore,
                            publishedAt = event.publishedAt
                        )
                    )
                }
            }
            sentimentCacheService.update(event.ticker, event.sentiment, event.sentimentScore)
        } catch (e: Exception) {
            log.error("Failed to process message", e)
        }
    }
}
