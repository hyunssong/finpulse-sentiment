package com.hyunprojects.finpulse.dto

interface DailySentimentProjection {
    fun getDate(): java.time.LocalDate
    fun getAvgScore(): Double
    fun getArticleCount(): Long
    fun getPositiveCount(): Long
    fun getNegativeCount(): Long
    fun getNeutralCount(): Long
}
