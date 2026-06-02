package com.hyunprojects.finpulse.api

import com.hyunprojects.finpulse.api.dto.SentimentAlert
import com.hyunprojects.finpulse.service.SentimentAlertBus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.reactive.asFlow
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerSentEvent
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/alerts")
class AlertController(private val alertBus: SentimentAlertBus) {

    @GetMapping("/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun stream(): Flow<ServerSentEvent<SentimentAlert>> =
        alertBus.asFlux()
            .onBackpressureDrop()
            .asFlow()
            .map { alert ->
                ServerSentEvent.builder(alert)
                    .event("sentiment-alert")
                    .build()
            }
}
