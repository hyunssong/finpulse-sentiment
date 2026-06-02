package com.hyunprojects.finpulse.service

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service

/**
 * A service that is called on every consumed analyzed articles
 * Based on the sentiment,
 * 1) updates the Redis cache weighted sum score that represents average sentiment.
 * 2) updates the positive sentiment score sum, positive count of analyzed articles
 */
@Service
class SentimentCacheService(private val redisTemplate: StringRedisTemplate) {

    companion object { // similar to java static class vars
        private const val TRENDING_KEY = "finpulse:trending"
        private fun sentimentKey(ticker: String) = "finpulse:sentiment:$ticker"
    }

    fun update(ticker: String, sentiment: String, score: Double) {
        val key = sentimentKey(ticker)
        val hash = redisTemplate.opsForHash<String, String>()

        hash.increment(key, "total", 1L)

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
            redisTemplate.opsForZSet().add(TRENDING_KEY, ticker, posSum / posCount)
        }
    }

    fun topTrending(n: Long = 10): Set<String> =
        redisTemplate.opsForZSet().reverseRange(TRENDING_KEY, 0, n - 1) ?: emptySet()
}
