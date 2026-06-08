package com.hyunprojects.finpulse.service

import com.hyunprojects.finpulse.dto.ArticleRepository
import com.hyunprojects.finpulse.dto.VolumeRecord
import com.hyunprojects.finpulse.dto.VolumeRepository
import com.hyunprojects.finpulse.ingestion.TwelveDataClient
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

@Service
class VolumeService(
    private val twelveDataClient: TwelveDataClient,
    private val volumeRepository: VolumeRepository,
    private val articleRepository: ArticleRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // Refresh volume for the top 20 trending tickers every hour.
    @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.HOURS)
    fun refreshTrendingVolumes() {
        val since = Instant.now().minus(24, ChronoUnit.HOURS)
        val tickers = articleRepository
            .findTrendingByMentions(since, PageRequest.of(0, 20))
            .map { it.getTicker() }

        log.info("Refreshing volume for {} trending tickers", tickers.size)
        tickers.forEach { ticker ->
            runCatching { fetchAndStore(ticker) }
                .onFailure { log.error("Volume refresh failed: ticker={}", ticker, it) }
        }
    }

    @Transactional
    fun fetchAndStore(ticker: String) {
        // One API call returns today + 30 days of history sorted descending.
        val dailyVolumes = twelveDataClient.fetchDailyVolumes(ticker)
        if (dailyVolumes.isEmpty()) return

        dailyVolumes.forEach { (date, volume) -> upsertVolume(ticker, date, volume) }
        log.info("Volume stored: ticker={} days={} latestVolume={}", ticker, dailyVolumes.size, dailyVolumes.first().second)
    }

    fun calculateRvol(ticker: String): Double? {
        val today = LocalDate.now()
        val todayRecord = volumeRepository.findByTickerAndDate(ticker, today) ?: return null
        val history = volumeRepository.findHistory(ticker, today.minusDays(31), today.minusDays(1))
        if (history.isEmpty()) return null
        val avg = history.map { it.totalVolume }.average()
        if (avg <= 0) return null
        return intradayNormalized(todayRecord.totalVolume, today) / avg
    }

    fun getRvolData(ticker: String): RvolData? {
        val today = LocalDate.now()
        val todayRecord = volumeRepository.findByTickerAndDate(ticker, today) ?: return null
        val history = volumeRepository.findHistory(ticker, today.minusDays(31), today.minusDays(1))
        val avgVolume = if (history.isEmpty()) 0L else history.map { it.totalVolume }.average().toLong()
        val projectedVolume = intradayNormalized(todayRecord.totalVolume, today)
        val rvol = if (avgVolume > 0) projectedVolume / avgVolume else null
        return RvolData(
            ticker = ticker,
            todayVolume = todayRecord.totalVolume,
            projectedVolume = projectedVolume.toLong(),
            avgVolume30d = avgVolume,
            rvol = rvol,
            daysOfHistory = history.size,
            updatedAt = todayRecord.updatedAt
        )
    }

    // Projects today's partial intraday volume to a full-day equivalent.
    // Historical records are already full-day so they pass through unchanged.
    // US market hours: 9:30 AM – 4:00 PM ET = 390 minutes.
    private fun intradayNormalized(volume: Long, date: LocalDate): Double {
        if (date != LocalDate.now()) return volume.toDouble()
        val et = ZoneId.of("America/New_York")
        val now = ZonedDateTime.now(et)
        val open  = now.toLocalDate().atTime(9, 30).atZone(et)
        val close = now.toLocalDate().atTime(16, 0).atZone(et)
        if (!now.isAfter(open))  return volume.toDouble() // pre-market: no adjustment
        if (now.isAfter(close))  return volume.toDouble() // market closed: full day already
        val elapsed = ChronoUnit.MINUTES.between(open, now).coerceAtLeast(1).toDouble()
        return volume * (390.0 / elapsed)
    }

    private fun upsertVolume(ticker: String, date: LocalDate, volume: Long) {
        val existing = volumeRepository.findByTickerAndDate(ticker, date)
        if (existing != null) {
            existing.totalVolume = volume
            existing.updatedAt = Instant.now()
            volumeRepository.save(existing)
        } else {
            volumeRepository.save(VolumeRecord(ticker = ticker, date = date, totalVolume = volume))
        }
    }
}

data class RvolData(
    val ticker: String,
    val todayVolume: Long,
    val projectedVolume: Long,
    val avgVolume30d: Long,
    val rvol: Double?,
    val daysOfHistory: Int,
    val updatedAt: Instant
)
