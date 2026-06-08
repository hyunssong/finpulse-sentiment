package com.hyunprojects.finpulse.dto

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.LocalDate

interface VolumeRepository : JpaRepository<VolumeRecord, Long> {

    fun findByTickerAndDate(ticker: String, date: LocalDate): VolumeRecord?

    @Query("SELECT v FROM VolumeRecord v WHERE v.ticker = :ticker AND v.date BETWEEN :from AND :to ORDER BY v.date ASC")
    fun findHistory(ticker: String, from: LocalDate, to: LocalDate): List<VolumeRecord>
}
