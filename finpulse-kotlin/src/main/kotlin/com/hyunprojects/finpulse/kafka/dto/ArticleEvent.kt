package com.hyunprojects.finpulse.kafka.dto

import java.time.Instant

/**
 * Class that represents the articles to be published to Kafka
 */
data class ArticleEvent(
    val title:String,
    val summary:String,
    val source:String,
    val url:String,
    val publishedAt: Instant,
    var ticker:String
)