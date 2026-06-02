package com.hyunprojects.finpulse.ingestion.dto

import com.hyunprojects.finpulse.kafka.dto.ArticleEvent
import java.time.Instant

data class FinnhubArticle(
    val id:Long,
    val headline: String,
    val datetime: Long,
    val summary: String,
    val source: String,
    val url:String
){
    fun toArticle(symbol:String) = ArticleEvent(
        title=headline,
        summary=summary,
        source=source,
        url=url,
        publishedAt = Instant.ofEpochSecond(datetime),
        ticker=symbol
    )
}