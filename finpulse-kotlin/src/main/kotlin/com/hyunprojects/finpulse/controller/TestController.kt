package com.hyunprojects.finpulse.controller

import com.hyunprojects.finpulse.ingestion.FinnhubApiClient
import com.hyunprojects.finpulse.ingestion.MarketauxApiClient
import com.hyunprojects.finpulse.ingestion.NewsDataApiClient
import com.hyunprojects.finpulse.kafka.dto.ArticleEvent
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
class TestController (
    private val finnhubClient: FinnhubApiClient,
    private val marketauxApiClient: MarketauxApiClient,
    private val newsDataApiClient: NewsDataApiClient
) {
    @GetMapping("/test/fetch")
    fun testFetch() : List<ArticleEvent> {
//        return finnhubClient.fetchArticles(
//            symbol="AAPL",
//            from= LocalDate.now().minusDays(1),
//            to = LocalDate.now()
//        )
//        return marketauxApiClient.fetchArticles(
//            symbol = "AAPL",
//            from = LocalDate.now().minusDays(10),
//            to = LocalDate.now()
//        )
        return newsDataApiClient.fetchArticles(
            symbol = "AAPL",
            from = LocalDate.now().minusDays(10),
            to = LocalDate.now()
        )
    }
}