package com.hyunprojects.finpulse.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit
import kotlin.random.Random

@Service
class SentimentCacheService(
    private val redisTemplate: StringRedisTemplate,
    @Value("\${finpulse.redis.sentiment-ttl-days:7}") private val sentimentTtlDays: Long,
    @Value("\${finpulse.redis.trending-shards:3}") private val trendingShards: Int
) {

    companion object {
        private const val TRENDING_BASE_KEY = "finpulse:trending"
        private fun sentimentKey(ticker: String) = "finpulse:sentiment:$ticker"
    }

    fun update(ticker: String, sentiment: String, score: Double) {
        val key = sentimentKey(ticker)
        val hash = redisTemplate.opsForHash<String, String>()

        hash.increment(key, "total", 1L)
        // sliding TTL: refreshed on every write so active tickers stay warm;
        // inactive tickers expire after sentimentTtlDays to prevent unbounded growth
        redisTemplate.expire(key, sentimentTtlDays, TimeUnit.DAYS)

        val weighted = when (sentiment) {
            "positive" ->  score
            "negative" -> -score
            else -> 0.0
        }
        hash.increment(key, "weightedSum", weighted)

        if (sentiment == "positive") {
            hash.increment(key, "positiveSum", score)
            hash.increment(key, "positiveCount", 1L)

            val posSum   = hash.get(key, "positiveSum")?.toDoubleOrNull()   ?: score
            val posCount = hash.get(key, "positiveCount")?.toDoubleOrNull() ?: 1.0
            val trendingScore = posSum / posCount

            // write to every shard so each shard holds a complete view of trending scores;
            // reads are then spread across shards to avoid a single hot key
            repeat(trendingShards) { shard ->
                redisTemplate.opsForZSet().add("$TRENDING_BASE_KEY:$shard", ticker, trendingScore)
            }
        }
    }

    fun topTrending(n: Long = 10): Set<String> {
        val shard = Random.nextInt(trendingShards)
        return redisTemplate.opsForZSet().reverseRange("$TRENDING_BASE_KEY:$shard", 0, n - 1) ?: emptySet()
    }
}
