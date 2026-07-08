package com.hyunprojects.finpulse.ingestion

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.core.annotation.Order
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

/**
 * Service class to fetch all the symbols from exchange
 */
@Service
class SymbolRegistryService(
    private val finnhubApiClient: FinnhubApiClient,
    // MIC codes (trading venues representation) for major US exchange: NASDAQ, NYSE, NYSE American
    @Value("\${finpulse.symbols.exchanges:XNGS,XNYS,XASE}") exchangesRaw: String,
    @Value("\${finpulse.watchlist:AAPL,TSLA,GOOGL,MSFT,AMZN,NVDA,META,NFLX,AMD,INTC}") fallbackWatchlist: String
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val allowedExchanges: Set<String> = exchangesRaw.split(",").map { it.trim().uppercase() }.toSet()
    private val fallback: List<String> = fallbackWatchlist.split(",").map { it.trim().uppercase() }.filter { it.isNotEmpty() }

    @Volatile // modifications to this variable is visible to all other threads
    private var symbols: List<String> = fallback

    // @EventListener(ApplicationReadyEvent::class) fires before NewsFetcherService.fetchAll() Scheduled annotated method
    // this allows fetch from NewsFetcherService to see the full dynamic list rather than the fallback.
    @EventListener(ApplicationReadyEvent::class)
    fun loadOnStartup() = refresh()

    // Refresh daily
    // initialDelay avoids a second fire right at startup (loadOnStartup already covers that).
    @Scheduled(fixedRate = 24, initialDelay = 24, timeUnit = TimeUnit.HOURS)
    fun refresh() {
        runCatching {
            val fetched = finnhubApiClient.fetchSymbols()
                .filter {  it.type == "Common Stock" && it.mic in allowedExchanges }
                .map { it.displaySymbol }
                .filter { it.isNotEmpty() }

            if (fetched.isNotEmpty()) {
                symbols = fetched
                log.info("Symbol registry refreshed: {} symbols across exchanges {}", fetched.size, allowedExchanges)
            } else {
                log.warn("Symbol registry returned empty list; retaining {} existing symbols", symbols.size)
            }
        }.onFailure {
            log.error("Symbol registry refresh failed; retaining {} symbols (fallback or previous)", symbols.size, it)
        }
    }

    fun symbols(): List<String> = symbols
}
