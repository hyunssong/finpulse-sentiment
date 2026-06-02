package com.hyunprojects.finpulse.api.dto

data class Mover(
    val symbol: String,
    val recentScore: Double,
    val previousScore: Double,
    val shift: Double
)

data class MoverResponse(val movers: List<Mover>)
