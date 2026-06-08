package com.hyunprojects.finpulse.dto

import jakarta.persistence.*
import java.time.Instant
import java.time.LocalDate

@Entity
@Table(
    name = "volume_records",
    uniqueConstraints = [UniqueConstraint(columnNames = ["ticker", "date"])]
)
class VolumeRecord(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, length = 20)
    val ticker: String,

    @Column(nullable = false)
    val date: LocalDate,

    @Column(nullable = false)
    var totalVolume: Long,

    @Column(nullable = false)
    var updatedAt: Instant = Instant.now()
)
