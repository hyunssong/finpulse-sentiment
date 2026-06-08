package com.hyunprojects.finpulse.api

import com.hyunprojects.finpulse.api.dto.*
import com.hyunprojects.finpulse.dto.ArticleRepository
import com.hyunprojects.finpulse.ingestion.SymbolRegistryService
import com.hyunprojects.finpulse.service.VolumeService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.data.domain.PageRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.time.temporal.ChronoUnit

@RestController
@RequestMapping("/api/v1/tickers")
class TickerController(
    private val articleRepository: ArticleRepository,
    private val symbolRegistry: SymbolRegistryService,
    private val volumeService: VolumeService
) {

    @GetMapping("/{symbol}/rvol")
    suspend fun getRvol(@PathVariable symbol: String): ResponseEntity<Any> {
        val data = withContext(Dispatchers.IO) { volumeService.getRvolData(symbol.uppercase()) }
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(data)
    }

    @GetMapping("/watched")
    fun getWatchedSymbols(
        @RequestParam(defaultValue = "") q: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "100") size: Int
    ): Map<String, Any> {
        val effectiveSize = size.coerceIn(1, 500)
        val all = symbolRegistry.symbols()
            .let { list -> if (q.isBlank()) list else list.filter { it.contains(q.uppercase()) } }
        val total = all.size
        val data = all.drop(page * effectiveSize).take(effectiveSize)
        return mapOf("symbols" to data, "total" to total, "page" to page, "size" to effectiveSize)
    }

    @GetMapping("/{symbol}/articles")
    suspend fun getTickerArticles(
        @PathVariable symbol: String,
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestParam(defaultValue = "20") limit: Int,
        @RequestParam(defaultValue = "publishedAt_desc") sort: String
    ): PagedResponse<ArticleResponse> {
        val effectiveLimit = limit.coerceIn(1, 100)
        val page = if (effectiveLimit > 0) offset / effectiveLimit else 0
        val pageable = PageRequest.of(page, effectiveLimit, parseSort(sort))
        val result = withContext(Dispatchers.IO) {
            articleRepository.findByTicker(symbol.uppercase(), pageable)
        }
        return PagedResponse(
            data = result.content.map { it.toResponse() },
            offset = offset,
            limit = effectiveLimit,
            total = result.totalElements
        )
    }

    @GetMapping("/{symbol}/sentiment")
    suspend fun getDailySentiment(@PathVariable symbol: String): DailySentimentResponse {
        val since = Instant.now().minus(30, ChronoUnit.DAYS)
        val rows = withContext(Dispatchers.IO) {
            articleRepository.findDailySentiment(symbol.uppercase(), since)
        }
        return DailySentimentResponse(
            ticker = symbol.uppercase(),
            range = "30d",
            data = rows.map { row ->
                DailySentimentPoint(
                    date = row.getDate().toString(),
                    avgScore = row.getAvgScore(),
                    articleCount = row.getArticleCount(),
                    positive = row.getPositiveCount(),
                    negative = row.getNegativeCount(),
                    neutral = row.getNeutralCount()
                )
            }
        )
    }

    @GetMapping("/trending")
    suspend fun getTrending(@RequestParam(defaultValue = "10") limit: Int): TrendingResponse {
        val since = Instant.now().minus(24, ChronoUnit.HOURS)
        val effectiveLimit = limit.coerceIn(1, 50)
        val tickers = withContext(Dispatchers.IO) {
            articleRepository.findTrendingByMentions(since, PageRequest.of(0, effectiveLimit))
                .map { TrendingTicker(it.getTicker(), it.getMentionCount(), volumeService.calculateRvol(it.getTicker())) }
        }
        return TrendingResponse(tickers)
    }

    @GetMapping("/movers")
    suspend fun getMovers(@RequestParam(defaultValue = "10") limit: Int): MoverResponse {
        val now = Instant.now()
        val recentFrom = now.minus(24, ChronoUnit.HOURS)
        val prevFrom = now.minus(48, ChronoUnit.HOURS)
        val effectiveLimit = limit.coerceIn(1, 50)
        val rows = withContext(Dispatchers.IO) {
            articleRepository.findMovers(recentFrom, prevFrom, PageRequest.of(0, effectiveLimit))
        }
        return MoverResponse(rows.map { row ->
            Mover(
                symbol = row.getTicker(),
                recentScore = row.getRecentScore(),
                previousScore = row.getPreviousScore(),
                shift = row.getRecentScore() - row.getPreviousScore()
            )
        })
    }
}
