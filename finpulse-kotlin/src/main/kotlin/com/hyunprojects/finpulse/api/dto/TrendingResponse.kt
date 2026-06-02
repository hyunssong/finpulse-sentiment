package com.hyunprojects.finpulse.api.dto

data class TrendingTicker(
    val symbol: String,
    val mentionCount: Long
)

data class TrendingResponse(val tickers: List<TrendingTicker>)
