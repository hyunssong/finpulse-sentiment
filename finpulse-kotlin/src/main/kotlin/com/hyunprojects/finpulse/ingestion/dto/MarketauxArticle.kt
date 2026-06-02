package com.hyunprojects.finpulse.ingestion.dto

import com.hyunprojects.finpulse.kafka.dto.ArticleEvent
import java.time.Instant

data class MarketauxResponse(
    val meta: MarketauxMeta,
    val data: List<MarketauxArticle>
)

data class MarketauxMeta(
    val found: Int,
    val returned: Int,
    val limit: Int,
    val page: Int
)
data class MarketauxArticle(
    val uuid:String,
    val title: String,
    val published_at: String,
    val description: String,
    val source: String,
    val url:String
){
    fun toArticle(symbol:String) = ArticleEvent(
        title=title,
        summary=description,
        source=source,
        url=url,
        publishedAt=Instant.parse(published_at),
        ticker=symbol
    )
}