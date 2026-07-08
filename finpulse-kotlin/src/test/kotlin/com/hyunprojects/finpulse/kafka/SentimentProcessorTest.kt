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

    @Mock private lateinit var objectMapper: ObjectMapper
    @Mock private lateinit var kafkaTemplate: KafkaTemplate<String, String>
    @Mock private lateinit var finbertClient: FinbertClient

    private lateinit var processor: SentimentProcessor

    @BeforeEach
    fun setUp() {
        processor = SentimentProcessor(objectMapper, kafkaTemplate, finbertClient, saltBuckets = 3)
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
    fun `positive finbert result is forwarded to analyzed-articles`() {
        val evt = event("AAPL beats estimates", "Revenue exceeded expectations")
        stubReadValue(evt)
        `when`(finbertClient.analyze(evt.title, evt.summary))
            .thenReturn(FinbertClient.AnalysisResult("positive", 0.9823))
        val captor = ArgumentCaptor.forClass(Any::class.java)
        `when`(objectMapper.writeValueAsString(captor.capture())).thenReturn("{}")

        processor.process("{}")

        val analyzed = captor.value as AnalyzedArticleEvent
        assertEquals("positive", analyzed.sentiment)
        assertEquals(0.9823, analyzed.sentimentScore)
        verify(kafkaTemplate).send(eq("analyzed-articles"), anyString(), eq("{}"))
    }

    @Test
    fun `negative finbert result is forwarded`() {
        val evt = event("AAPL misses earnings, guidance cut")
        stubReadValue(evt)
        `when`(finbertClient.analyze(evt.title, evt.summary))
            .thenReturn(FinbertClient.AnalysisResult("negative", 0.8741))
        val captor = ArgumentCaptor.forClass(Any::class.java)
        `when`(objectMapper.writeValueAsString(captor.capture())).thenReturn("{}")

        processor.process("{}")

        val analyzed = captor.value as AnalyzedArticleEvent
        assertEquals("negative", analyzed.sentiment)
        assertEquals(0.8741, analyzed.sentimentScore)
    }

    @Test
    fun `neutral finbert result is forwarded`() {
        val evt = event("Company provides quarterly market update")
        stubReadValue(evt)
        `when`(finbertClient.analyze(evt.title, evt.summary))
            .thenReturn(FinbertClient.AnalysisResult("neutral", 0.6102))
        val captor = ArgumentCaptor.forClass(Any::class.java)
        `when`(objectMapper.writeValueAsString(captor.capture())).thenReturn("{}")

        processor.process("{}")

        val analyzed = captor.value as AnalyzedArticleEvent
        assertEquals("neutral", analyzed.sentiment)
        assertEquals(0.6102, analyzed.sentimentScore)
    }

    @Test
    fun `parse error is swallowed and kafka is never called`() {
        `when`(objectMapper.readValue(anyString(), any(Class::class.java)))
            .thenThrow(RuntimeException("malformed json"))

        assertDoesNotThrow { processor.process("bad-json") }
        verifyNoInteractions(kafkaTemplate)
    }

    @Test
    fun `finbert client error is swallowed and kafka is never called`() {
        val evt = event("AAPL news")
        stubReadValue(evt)
        `when`(finbertClient.analyze(anyString(), anyString()))
            .thenThrow(RuntimeException("HTTP 503 Service Unavailable"))

        assertDoesNotThrow { processor.process("{}") }
        verifyNoInteractions(kafkaTemplate)
    }
}
