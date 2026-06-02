package com.hyunprojects.finpulse.dto

interface TrendingProjection {
    fun getTicker(): String
    fun getMentionCount(): Long
}
