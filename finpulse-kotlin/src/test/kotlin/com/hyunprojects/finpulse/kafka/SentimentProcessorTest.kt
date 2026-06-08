package com.hyunprojects.finpulse.kafka

import com.hyunprojects.finpulse.kafka.dto.AnalyzedArticleEvent
import com.hyunprojects.finpulse.kafka.dto.ArticleEvent
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.kafka.core.KafkaTemplate
import tools.jackson.databind.ObjectMapper
import java.time.Instant

@ExtendWith(MockitoExtension::class)
class SentimentProcessorTest {

    @Mock
    private lateinit var objectMapper: ObjectMapper

    @Mock
    private lateinit var kafkaTemplate: KafkaTemplate<String, String>

    private lateinit var processor: SentimentProcessor

    @BeforeEach
    fun setUp() {
        processor = SentimentProcessor(objectMapper, kafkaTemplate, saltBuckets = 3)
    }

    private fun event(title: String, summary: String = "") = ArticleEvent(
        title = title,
        summary = summary,
        source = "testSource",
        url = "https://example.com/${title.hashCode()}",
        publishedAt = Instant.EPOCH,
        ticker = "AAPL"
    )

    @Suppress("UNCHECKED_CAST")
    private fun stubReadValue(evt: ArticleEvent) {
        `when`(objectMapper.readValue(anyString(), any(Class::class.java))).thenReturn(evt)
    }

    @Test
    fun `positive-heavy text produces positive sentiment`() {
        // beats, record, surged, profit, gain, growth = 6 positive, 0 negative → score 1.0
        val evt = event("AAPL beats record surged profit gain growth")
        stubReadValue(evt)
        val captor = ArgumentCaptor.forClass(Any::class.java)
        `when`(objectMapper.writeValueAsString(captor.capture())).thenReturn("{}")

        processor.process("{}")

        val analyzed = captor.value as AnalyzedArticleEvent
        assertEquals("positive", analyzed.sentiment)
        assertTrue(analyzed.sentimentScore >= 0.6)
        verify(kafkaTemplate).send(eq("analyzed-articles"), anyString(), eq("{}"))
    }

    @Test
    fun `negative-heavy text produces negative sentiment`() {
        // misses, loss, declined, crashed, layoffs, warning = 0 positive, 6 negative → score 0.0
        val evt = event("AAPL misses loss declined crashed layoffs warning")
        stubReadValue(evt)
        val captor = ArgumentCaptor.forClass(Any::class.java)
        `when`(objectMapper.writeValueAsString(captor.capture())).thenReturn("{}")

        processor.process("{}")

        val analyzed = captor.value as AnalyzedArticleEvent
        assertEquals("negative", analyzed.sentiment)
        assertTrue(analyzed.sentimentScore < 0.4)
    }

    @Test
    fun `text with no matching words defaults to neutral with score 0_5`() {
        val evt = event("Company provides a quarterly market update")
        stubReadValue(evt)
        val captor = ArgumentCaptor.forClass(Any::class.java)
        `when`(objectMapper.writeValueAsString(captor.capture())).thenReturn("{}")

        processor.process("{}")

        val analyzed = captor.value as AnalyzedArticleEvent
        assertEquals("neutral", analyzed.sentiment)
        assertEquals(0.5, analyzed.sentimentScore)
    }

    @Test
    fun `equal positive and negative word counts produce neutral`() {
        // beats + record (pos=2) vs misses + loss (neg=2) → score = 0.5 → neutral
        val evt = event("AAPL beats record but misses loss")
        stubReadValue(evt)
        val captor = ArgumentCaptor.forClass(Any::class.java)
        `when`(objectMapper.writeValueAsString(captor.capture())).thenReturn("{}")

        processor.process("{}")

        val analyzed = captor.value as AnalyzedArticleEvent
        assertEquals("neutral", analyzed.sentiment)
        assertEquals(0.5, analyzed.sentimentScore)
    }

    @Test
    fun `parse error is swallowed and kafka is never called`() {
        `when`(objectMapper.readValue(anyString(), any(Class::class.java)))
            .thenThrow(RuntimeException("malformed json"))

        assertDoesNotThrow { processor.process("bad-json") }
        verifyNoInteractions(kafkaTemplate)
    }
}
