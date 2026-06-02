package com.hyunprojects.finpulse.dto

interface MoverProjection {
    fun getTicker(): String
    fun getRecentScore(): Double
    fun getPreviousScore(): Double
}
