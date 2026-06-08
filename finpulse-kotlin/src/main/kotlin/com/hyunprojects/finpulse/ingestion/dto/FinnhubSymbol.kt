package com.hyunprojects.finpulse.ingestion.dto

data class FinnhubSymbol(
    val currency: String = "",
    val description: String = "",
    val displaySymbol: String = "",
    val figi: String? = null,
    val mic: String = "",
    val symbol: String = "",
    val type: String = ""
)
