package com.hyunprojects.finpulse.ingestion

import com.hyunprojects.finpulse.kafka.NewsProducer
import com.hyunprojects.finpulse.kafka.dto.ArticleEvent
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import java.time.Instant
import java.time.LocalDate

@ExtendWith(MockitoExtension::class)
class NewsFetcherServiceTest {

    @Mock private lateinit var client: ApiClient
    @Mock private lateinit var newsProducer: NewsProducer
    @Mock private lateinit var symbolRegistry: SymbolRegistryService

    private fun service(symbols: List<String>): NewsFetcherService {
        whenever(symbolRegistry.symbols()).thenReturn(symbols)
        return NewsFetcherService(listOf(client), newsProducer, symbolRegistry, requestDelayMs = 0L)
    }

    private fun article(url: String, ticker: String = "AAPL") = ArticleEvent(
        title = "Headline",
        summary = "Summary",
        source = "Reuters",
        url = url,
        publishedAt = Instant.EPOCH,
        ticker = ticker
    )

    @Test
    fun `registry symbols are used for each fetch`() {
        val svc = service(listOf("AAPL", "TSLA", "GOOGL"))
        whenever(client.fetchArticles(any(), any(), any())).thenReturn(emptyList())

        svc.fetchAll()

        verify(client).fetchArticles(eq("AAPL"), any<LocalDate>(), any<LocalDate>())
        verify(client).fetchArticles(eq("TSLA"), any<LocalDate>(), any<LocalDate>())
        verify(client).fetchArticles(eq("GOOGL"), any<LocalDate>(), any<LocalDate>())
    }

    @Test
    fun `fetchForTicker uppercases symbol and sends articles to kafka`() {
        val art = article("https://example.com/tsla-news", "TSLA")
        whenever(client.fetchArticles(eq("TSLA"), any<LocalDate>(), any<LocalDate>()))
            .thenReturn(listOf(art))

        service(listOf("AAPL")).fetchForTicker("tsla")

        verify(newsProducer).send(art)
    }

    @Test
    fun `same url is not sent to kafka on a second fetch`() {
        val svc = service(listOf("AAPL"))
        val art = article("https://example.com/dup")
        whenever(client.fetchArticles(any(), any(), any())).thenReturn(listOf(art))

        svc.fetchAll()
        svc.fetchAll()

        verify(newsProducer, times(1)).send(art)
    }

    @Test
    fun `duplicate urls within a single response are deduplicated`() {
        val svc = service(listOf("AAPL"))
        val art = article("https://example.com/same")
        whenever(client.fetchArticles(any(), any(), any())).thenReturn(listOf(art, art))

        svc.fetchAll()

        verify(newsProducer, times(1)).send(art)
    }

    @Test
    fun `client failure for one symbol does not prevent others from being fetched`() {
        val svc = service(listOf("AAPL", "TSLA"))
        val tslaArticle = article("https://example.com/tsla", "TSLA")
        whenever(client.fetchArticles(eq("AAPL"), any<LocalDate>(), any<LocalDate>()))
            .thenThrow(RuntimeException("API error"))
        whenever(client.fetchArticles(eq("TSLA"), any<LocalDate>(), any<LocalDate>()))
            .thenReturn(listOf(tslaArticle))

        assertDoesNotThrow { svc.fetchAll() }
        verify(newsProducer).send(tslaArticle)
    }

    @Test
    fun `empty symbol list results in no client calls`() {
        service(emptyList()).fetchAll()

        verifyNoInteractions(client)
        verifyNoInteractions(newsProducer)
    }
}
