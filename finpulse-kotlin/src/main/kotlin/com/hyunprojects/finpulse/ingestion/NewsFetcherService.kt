package com.hyunprojects.finpulse.ingestion

import com.hyunprojects.finpulse.kafka.NewsProducer
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

@Service
class NewsFetcherService(
    private val clients: List<ApiClient>,
    private val newsProducer: NewsProducer,
    private val symbolRegistry: SymbolRegistryService,
    // Delay between each symbol request per client — keeps us under API rate limits.
    // Finnhub free tier: 60 req/min → 1000ms safe, 150ms with headroom for bursts.
    @Value("\${finpulse.fetch.request-delay-ms:150}") private val requestDelayMs: Long
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val seenUrls: MutableSet<String> = ConcurrentHashMap.newKeySet()

    // initialDelayString gives the ApplicationReadyEvent (which loads the registry) time to complete.
    // @Scheduled fires before ApplicationReadyEvent so without a delay the first run uses the fallback list.
    @Scheduled(fixedDelayString = "PT15M", initialDelayString = "PT30S")
    fun fetchAll() {
        fetchForSymbols(symbolRegistry.symbols())
    }

    fun fetchForTicker(ticker: String) {
        fetchForSymbols(listOf(ticker.uppercase()))
    }

    private fun fetchForSymbols(symbols: List<String>) {
        val to = LocalDate.now()
        val from = to.minusDays(1)
        log.info("Starting fetch: clients={} symbols={} from={} to={}", clients.size, symbols.size, from, to)

        val articles = clients.flatMap { client ->
            symbols.flatMap { symbol ->
                if (requestDelayMs > 0) Thread.sleep(requestDelayMs)
                runCatching { client.fetchArticles(symbol, from, to) }
                    .onFailure { log.error("Fetch failed: client={} symbol={}", client::class.simpleName, symbol, it) }
                    .getOrDefault(emptyList())
            }
        }
            .distinctBy { it.url }
            .filter { seenUrls.add(it.url) }

        log.info("Fetched {} new articles across {} symbols, sending to Kafka", articles.size, symbols.size)
        articles.forEach { newsProducer.send(it) }
    }
}
