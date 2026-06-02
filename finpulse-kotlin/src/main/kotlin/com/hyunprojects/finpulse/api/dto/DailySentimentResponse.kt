package com.hyunprojects.finpulse.api.dto

data class DailySentimentPoint(
    val date: String,
    val avgScore: Double,
    val articleCount: Long,
    val positive: Long,
    val negative: Long,
    val neutral: Long
)

data class DailySentimentResponse(
    val ticker: String,
    val range: String,
    val data: List<DailySentimentPoint>
)
