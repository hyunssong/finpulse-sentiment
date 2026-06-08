package com.hyunprojects.finpulse.kafka

import com.hyunprojects.finpulse.api.dto.SentimentAlert
import com.hyunprojects.finpulse.dto.AnalyzedArticle
import com.hyunprojects.finpulse.dto.ArticleRepository
import com.hyunprojects.finpulse.kafka.dto.AnalyzedArticleEvent
import com.hyunprojects.finpulse.service.SentimentAlertBus
import com.hyunprojects.finpulse.service.SentimentCacheService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import tools.jackson.databind.ObjectMapper
import java.time.Instant

@ExtendWith(MockitoExtension::class)
class NewsConsumerTest {

    @Mock private lateinit var objectMapper: ObjectMapper
    @Mock private lateinit var articleRepository: ArticleRepository
    @Mock private lateinit var sentimentCacheService: SentimentCacheService
    @Mock private lateinit var alertBus: SentimentAlertBus

    private lateinit var consumer: NewsConsumer

    @BeforeEach
    fun setUp() {
        consumer = NewsConsumer(objectMapper, articleRepository, sentimentCacheService, alertBus)
    }

    private fun stubEvent(event: AnalyzedArticleEvent) {
        whenever(objectMapper.readValue(any<String>(), any<Class<*>>())).thenReturn(event)
    }

    private fun analyzedEvent(
        url: String = "https://example.com/article",
        ticker: String = "AAPL",
        sentiment: String = "positive",
        score: Double = 0.7
    ) = AnalyzedArticleEvent(
        title = "Test Headline",
        summary = "Test summary",
        source = "Reuters",
        url = url,
        publishedAt = Instant.EPOCH,
        ticker = ticker,
        sentiment = sentiment,
        sentimentScore = score
    )

    @Test
    fun `new article is saved and cache is updated`() {
        val event = analyzedEvent()
        stubEvent(event)
        whenever(articleRepository.existsByUrl(event.url)).thenReturn(false)

        consumer.consume("{}")

        verify(articleRepository).save(any<AnalyzedArticle>())
        verify(sentimentCacheService).update("AAPL", "positive", 0.7)
    }

    @Test
    fun `duplicate url skips save but still updates cache`() {
        val event = analyzedEvent()
        stubEvent(event)
        whenever(articleRepository.existsByUrl(event.url)).thenReturn(true)

        consumer.consume("{}")

        verify(articleRepository, never()).save(any())
        verify(sentimentCacheService).update("AAPL", "positive", 0.7)
    }

    @Test
    fun `score at threshold 0_85 triggers sentiment alert`() {
        val event = analyzedEvent(score = 0.85)
        stubEvent(event)
        whenever(articleRepository.existsByUrl(event.url)).thenReturn(false)

        consumer.consume("{}")

        val captor = argumentCaptor<SentimentAlert>()
        verify(alertBus).tryEmitNext(captor.capture())
        assertEquals("AAPL", captor.firstValue.ticker)
        assertEquals(0.85, captor.firstValue.sentimentScore)
    }

    @Test
    fun `score above threshold triggers alert`() {
        val event = analyzedEvent(score = 0.95)
        stubEvent(event)
        whenever(articleRepository.existsByUrl(event.url)).thenReturn(false)

        consumer.consume("{}")

        verify(alertBus).tryEmitNext(any())
    }

    @Test
    fun `score below threshold does not trigger alert`() {
        val event = analyzedEvent(score = 0.84)
        stubEvent(event)
        whenever(articleRepository.existsByUrl(event.url)).thenReturn(false)

        consumer.consume("{}")

        verifyNoInteractions(alertBus)
    }

    @Test
    fun `parse error is swallowed and repository is never called`() {
        whenever(objectMapper.readValue(any<String>(), any<Class<*>>()))
            .thenThrow(RuntimeException("parse error"))

        assertDoesNotThrow { consumer.consume("bad-json") }
        verifyNoInteractions(articleRepository)
        verifyNoInteractions(sentimentCacheService)
    }
}
