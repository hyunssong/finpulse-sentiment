package com.hyunprojects.finpulse.ingestion

import com.hyunprojects.finpulse.kafka.NewsProducer
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

@Service
class NewsFetcherService(
    private val clients: List<ApiClient>,
    private val newsProducer: NewsProducer,
    @Value("\${finpulse.watchlist}") watchlist: String
) {
    private val watchlistSymbols: List<String> = watchlist.split(",").map { it.trim().uppercase() }.filter { it.isNotEmpty() }
    private val log = LoggerFactory.getLogger(javaClass)
    private val seenUrls: MutableSet<String> = ConcurrentHashMap.newKeySet()

    @EventListener(ApplicationReadyEvent::class)
    @Scheduled(fixedRate = 15, timeUnit = TimeUnit.MINUTES)
    fun fetchAll() {
        fetchForSymbols(watchlistSymbols)
    }

    fun fetchForTicker(ticker: String) {
        fetchForSymbols(listOf(ticker.uppercase()))
    }

    private fun fetchForSymbols(symbols: List<String>) {
        val to = LocalDate.now()
        val from = to.minusDays(1)
        log.info("Starting fetch: clients={} symbols={} from={} to={}", clients.size, symbols, from, to)

        val articles = clients.flatMap { client ->
            symbols.flatMap { symbol ->
                runCatching { client.fetchArticles(symbol, from, to) }
                    .onFailure { log.error("Fetch failed: client={} symbol={}", client::class.simpleName, symbol, it) }
                    .getOrDefault(emptyList())
            }
        }
            .distinctBy { it.url }
            .filter { seenUrls.add(it.url) }

        log.info("Fetched {} new articles for {}, sending to Kafka", articles.size, symbols)
        articles.forEach { newsProducer.send(it) }
    }
}
