package com.hyunprojects.finpulse.service

import com.hyunprojects.finpulse.api.dto.SentimentAlert
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks

@Component
class SentimentAlertBus {

    private val sink: Sinks.Many<SentimentAlert> =
        Sinks.many().multicast().directBestEffort() // helpful for SSE as it multicast events to multiple subscribers

    fun asFlux(): Flux<SentimentAlert> = sink.asFlux()

    fun tryEmitNext(alert: SentimentAlert) {
        sink.tryEmitNext(alert)
    }
}
