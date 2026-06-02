package com.hyunprojects.finpulse.api

import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import java.time.Duration

@Component
class RateLimitFilter(private val redis: ReactiveStringRedisTemplate) : WebFilter {

    companion object {
        private const val MAX_REQUESTS = 60
        private val WINDOW = Duration.ofMinutes(1)
    }

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        if (!exchange.request.path.value().startsWith("/api/")) return chain.filter(exchange)

        val ip = exchange.request.remoteAddress?.address?.hostAddress ?: "unknown"
        val bucket = System.currentTimeMillis() / WINDOW.toMillis() // divides time into 60 seconds
        // requests that come from same ip and within 60 sec range will be in the same key
        val key = "rl:$ip:$bucket"

        return redis.opsForValue().increment(key)
            .flatMap { count ->
                // set TTL only on the first request so the key self-expires after one window
                if (count == 1L) redis.expire(key, WINDOW).thenReturn(count) else Mono.just(count)
            }
            .flatMap { count ->
                val remaining = (MAX_REQUESTS - count).coerceAtLeast(0)
                exchange.response.headers["X-RateLimit-Limit"] = MAX_REQUESTS.toString()
                exchange.response.headers["X-RateLimit-Remaining"] = remaining.toString()

                if (count > MAX_REQUESTS) {
                    exchange.response.statusCode = HttpStatus.TOO_MANY_REQUESTS
                    exchange.response.headers["Retry-After"] = WINDOW.seconds.toString()
                    exchange.response.setComplete()
                } else {
                    chain.filter(exchange)
                }
            }
    }
}
