package com.hyunprojects.finpulse.api.dto

import java.time.Instant

data class SentimentAlert(
    val ticker: String,
    val title: String,
    val sentiment: String,
    val sentimentScore: Double,
    val publishedAt: Instant
)
