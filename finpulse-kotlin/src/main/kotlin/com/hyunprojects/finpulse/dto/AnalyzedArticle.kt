package com.hyunprojects.finpulse.dto

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "analyzed_articles", indexes = [Index(columnList = "ticker"), Index(columnList = "url", unique = true)])
class AnalyzedArticle(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(length = 1024)
    val title: String,

    @Column(columnDefinition = "TEXT")
    val summary: String,

    val source: String,

    @Column(unique = true, length = 1024)
    val url: String,

    val publishedAt: Instant,
    val ticker: String,
    val sentiment: String,
    val sentimentScore: Double,

    @Column(updatable = false)
    val createdAt: Instant = Instant.now()
)
