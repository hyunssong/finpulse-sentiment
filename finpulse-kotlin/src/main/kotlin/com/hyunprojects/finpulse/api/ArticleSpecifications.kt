package com.hyunprojects.finpulse.api

import com.hyunprojects.finpulse.dto.AnalyzedArticle
import org.springframework.data.jpa.domain.Specification

/**
 * Specification helps to build a WHERE clause as an object
 * Used in ArticleController to return data dynamically with filters
 */
object ArticleSpecifications {

    fun withTicker(ticker: String?): Specification<AnalyzedArticle> =
        Specification { root, _, cb ->
            ticker?.let { cb.equal(root.get<String>("ticker"), it.uppercase()) }
        }

    fun withSentiment(sentiment: String?): Specification<AnalyzedArticle> =
        Specification { root, _, cb ->
            sentiment?.let { cb.equal(root.get<String>("sentiment"), it.lowercase()) }
        }

    fun withKeyword(q: String?): Specification<AnalyzedArticle> =
        Specification { root, _, cb ->
            q?.let {
                val pattern = "%$it%"
                cb.or(
                    cb.like(root.get("title"), pattern),
                    cb.like(root.get("summary"), pattern)
                )
            }
        }
}
