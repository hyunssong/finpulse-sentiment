package com.hyunprojects.finpulse.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import org.springframework.data.redis.core.HashOperations
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ZSetOperations
import java.util.concurrent.TimeUnit

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SentimentCacheServiceTest {

    @Mock private lateinit var redisTemplate: StringRedisTemplate

    @Suppress("UNCHECKED_CAST")
    private val hashOps: HashOperations<String, String, String> =
        mock(HashOperations::class.java) as HashOperations<String, String, String>

    @Suppress("UNCHECKED_CAST")
    private val zsetOps: ZSetOperations<String, String> =
        mock(ZSetOperations::class.java) as ZSetOperations<String, String>

    private lateinit var cacheService: SentimentCacheService

    private val ttlDays = 7L
    private val shards = 3

    @BeforeEach
    fun setUp() {
        `when`(redisTemplate.opsForHash<String, String>()).thenReturn(hashOps)
        `when`(redisTemplate.opsForZSet()).thenReturn(zsetOps)
        cacheService = SentimentCacheService(redisTemplate, ttlDays, shards)
    }

    @Test
    fun `positive sentiment increments all fields, sets TTL, and writes to all shards`() {
        cacheService.update("AAPL", "positive", 0.8)

        verify(hashOps).increment("finpulse:sentiment:AAPL", "total", 1L)
        verify(hashOps).increment("finpulse:sentiment:AAPL", "weightedSum", 0.8)
        verify(hashOps).increment("finpulse:sentiment:AAPL", "positiveSum", 0.8)
        verify(hashOps).increment("finpulse:sentiment:AAPL", "positiveCount", 1L)
        verify(redisTemplate).expire("finpulse:sentiment:AAPL", ttlDays, TimeUnit.DAYS)
        // one write per shard — every shard gets the complete trending score
        verify(zsetOps, times(shards)).add(anyString(), eq("AAPL"), anyDouble())
    }

    @Test
    fun `negative sentiment uses negative weighted sum, sets TTL, and skips trending`() {
        cacheService.update("TSLA", "negative", 0.3)

        verify(hashOps).increment("finpulse:sentiment:TSLA", "total", 1L)
        verify(hashOps).increment("finpulse:sentiment:TSLA", "weightedSum", -0.3)
        verify(redisTemplate).expire("finpulse:sentiment:TSLA", ttlDays, TimeUnit.DAYS)
        verify(hashOps, never()).increment(anyString(), eq("positiveSum"), anyDouble())
        verifyNoInteractions(zsetOps)
    }

    @Test
    fun `neutral sentiment uses zero weighted sum, sets TTL, and skips trending`() {
        cacheService.update("GOOGL", "neutral", 0.5)

        verify(hashOps).increment("finpulse:sentiment:GOOGL", "total", 1L)
        verify(hashOps).increment("finpulse:sentiment:GOOGL", "weightedSum", 0.0)
        verify(redisTemplate).expire("finpulse:sentiment:GOOGL", ttlDays, TimeUnit.DAYS)
        verify(hashOps, never()).increment(anyString(), eq("positiveSum"), anyDouble())
        verifyNoInteractions(zsetOps)
    }

    @Test
    fun `topTrending reads from one of the sharded keys`() {
        `when`(zsetOps.reverseRange(anyString(), anyLong(), anyLong()))
            .thenReturn(setOf("AAPL", "TSLA", "NVDA"))

        val result = cacheService.topTrending(5)

        assertEquals(setOf("AAPL", "TSLA", "NVDA"), result)
        // exactly one shard read, key must be one of finpulse:trending:0..2
        verify(zsetOps, times(1)).reverseRange(anyString(), eq(0L), eq(4L))
    }

    @Test
    fun `topTrending returns empty set when zset returns null`() {
        `when`(zsetOps.reverseRange(anyString(), anyLong(), anyLong())).thenReturn(null)

        val result = cacheService.topTrending(10)

        assertEquals(emptySet<String>(), result)
    }
}
