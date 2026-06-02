package com.hyunprojects.finpulse.kafka

import com.hyunprojects.finpulse.kafka.dto.AnalyzedArticleEvent
import com.hyunprojects.finpulse.kafka.dto.ArticleEvent
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

@Component
class SentimentProcessor(
    private val objectMapper: ObjectMapper,
    private val kafkaTemplate: KafkaTemplate<String, String>
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(topics = ["raw-articles"], groupId = "finpulse-sentiment")
    fun process(message: String) {
        try {
            val event = objectMapper.readValue(message, ArticleEvent::class.java)
            val text = "${event.title} ${event.summary}".lowercase()
            val score = score(text)
            val sentiment = when {
                score >= 0.6 -> "positive"
                score < 0.4  -> "negative"
                else         -> "neutral"
            }
            val analyzed = AnalyzedArticleEvent(
                title = event.title,
                summary = event.summary,
                source = event.source,
                url = event.url,
                publishedAt = event.publishedAt,
                ticker = event.ticker,
                sentiment = sentiment,
                sentimentScore = score
            )
            val json = objectMapper.writeValueAsString(analyzed)
            kafkaTemplate.send("analyzed-articles", event.ticker, json)
            log.debug("Scored: ticker={} sentiment={} score={:.3f}", event.ticker, sentiment, score)
        } catch (e: Exception) {
            log.error("Failed to process raw article", e)
        }
    }

    private fun score(text: String): Double {
        val pos = POSITIVE_WORDS.count { text.contains(it) }
        val neg = NEGATIVE_WORDS.count { text.contains(it) }
        val total = pos + neg
        return if (total == 0) 0.5 else pos.toDouble() / total
    }

    companion object {
        private val POSITIVE_WORDS = setOf(
            "beat", "beats", "record", "surge", "surged", "surging", "rally", "rallied",
            "gain", "gains", "gained", "growth", "grew", "grow", "profit", "profits",
            "profitable", "revenue", "revenues", "exceed", "exceeds", "exceeded",
            "outperform", "outperformed", "upgrade", "upgraded", "buy", "bullish",
            "strong", "strength", "positive", "optimistic", "opportunity", "opportunities",
            "expand", "expansion", "breakthrough", "innovation", "innovative", "dividend",
            "dividends", "recovery", "recover", "recovered", "boost", "boosted", "boosts",
            "soar", "soared", "soaring", "rise", "rises", "risen", "higher", "high",
            "upbeat", "upside", "momentum", "accelerate", "accelerating", "success",
            "successful", "win", "wins", "winning", "hire", "hiring", "partnership",
            "deal", "deals", "acquisition", "acquires", "launch", "launches", "launched"
        )

        private val NEGATIVE_WORDS = setOf(
            "miss", "misses", "missed", "loss", "losses", "decline", "declined",
            "declining", "fall", "falls", "fell", "fallen", "drop", "drops", "dropped",
            "plunge", "plunged", "plunging", "crash", "crashed", "crashing", "sell",
            "bearish", "weak", "weakness", "negative", "pessimistic", "risk", "risks",
            "risky", "concern", "concerns", "worried", "worry", "uncertain", "uncertainty",
            "downgrade", "downgraded", "layoff", "layoffs", "cut", "cuts", "cutting",
            "reduce", "reduced", "reducing", "shrink", "shrinking", "debt", "default",
            "bankrupt", "bankruptcy", "lawsuit", "investigation", "probe", "fraud",
            "scandal", "penalty", "fine", "fined", "recall", "recalled", "warning",
            "warn", "warns", "disappointing", "disappoint", "disappointed", "below",
            "miss", "slump", "slumped", "slumping", "downturn", "pressure", "struggle",
            "struggling", "halt", "halted", "suspend", "suspended", "delay", "delayed"
        )
    }
}
