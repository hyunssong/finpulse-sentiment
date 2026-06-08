package com.hyunprojects.finpulse.api

import com.hyunprojects.finpulse.api.dto.ArticleResponse
import com.hyunprojects.finpulse.api.dto.Mover
import com.hyunprojects.finpulse.api.dto.TrendingTicker
import com.hyunprojects.finpulse.api.dto.toResponse
import com.hyunprojects.finpulse.dto.ArticleRepository
import com.hyunprojects.finpulse.ingestion.SymbolRegistryService
import com.hyunprojects.finpulse.service.VolumeService
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.domain.Sort.Direction.DESC
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import java.time.Instant
import java.time.temporal.ChronoUnit

@Controller
class DashboardController(
    private val articleRepository: ArticleRepository,
    private val symbolRegistry: SymbolRegistryService,
    private val volumeService: VolumeService
) {

    @GetMapping("/")
    fun home(model: Model): String {
        val since24h = Instant.now().minus(24, ChronoUnit.HOURS)
        val prev = Instant.now().minus(48, ChronoUnit.HOURS)

        val trending = articleRepository.findTrendingByMentions(since24h, PageRequest.of(0, 10))
            .map { TrendingTicker(it.getTicker(), it.getMentionCount(), volumeService.calculateRvol(it.getTicker())) }

        val movers = articleRepository.findMovers(since24h, prev, PageRequest.of(0, 10))
            .map { Mover(it.getTicker(), it.getRecentScore(), it.getPreviousScore(), it.getRecentScore() - it.getPreviousScore()) }

        model.addAttribute("trending", trending)
        model.addAttribute("movers", movers)
        return "index"
    }

    @GetMapping("/symbols")
    fun symbols(model: Model): String {
        model.addAttribute("symbols", symbolRegistry.symbols())
        model.addAttribute("total", symbolRegistry.symbols().size)
        return "symbols"
    }

    @GetMapping("/search")
    fun search(@RequestParam symbol: String): String =
        "redirect:/ticker/${symbol.uppercase().trim()}"

    @GetMapping("/ticker/{symbol}")
    fun ticker(@PathVariable symbol: String, model: Model): String {
        val upper = symbol.uppercase()
        val since30d = Instant.now().minus(30, ChronoUnit.DAYS)
        val pageable = PageRequest.of(0, 20, Sort.by(DESC, "publishedAt"))

        val articlesPage = articleRepository.findByTicker(upper, pageable)
        val articles: List<ArticleResponse> = articlesPage.content.map { it.toResponse() }
        val totalArticles = articlesPage.totalElements

        val sentimentRows = articleRepository.findDailySentiment(upper, since30d)
        val signal = computeSignal(
            totalPositive = sentimentRows.sumOf { it.getPositiveCount() },
            totalArticles = sentimentRows.sumOf { it.getArticleCount() }
        )

        val chartData = sentimentRows.map {
            mapOf("date" to it.getDate().toString(), "avgScore" to it.getAvgScore())
        }

        model.addAttribute("symbol", upper)
        model.addAttribute("articles", articles)
        model.addAttribute("signal", signal.label)
        model.addAttribute("signalColor", signal.color)
        model.addAttribute("sentimentData", chartData)
        model.addAttribute("totalArticles", totalArticles)
        model.addAttribute("currentOffset", 0)
        model.addAttribute("limit", 20)
        return "ticker"
    }

    private data class Signal(val label: String, val color: String)

    private fun computeSignal(totalPositive: Long, totalArticles: Long): Signal {
        if (totalArticles == 0L) return Signal("Neutral", "warning")
        val ratio = totalPositive.toDouble() / totalArticles
        return when {
            ratio >= 0.6 -> Signal("Bullish", "success")
            ratio < 0.4  -> Signal("Bearish", "danger")
            else         -> Signal("Neutral", "warning")
        }
    }
}
