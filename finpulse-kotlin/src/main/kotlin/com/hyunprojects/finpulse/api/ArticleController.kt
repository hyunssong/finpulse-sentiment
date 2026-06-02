package com.hyunprojects.finpulse.api

import com.hyunprojects.finpulse.api.dto.ArticleResponse
import com.hyunprojects.finpulse.api.dto.PagedResponse
import com.hyunprojects.finpulse.api.dto.toResponse
import com.hyunprojects.finpulse.dto.ArticleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.data.domain.PageRequest
import org.springframework.data.jpa.domain.Specification
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/articles")
class ArticleController(private val articleRepository: ArticleRepository) {

    @GetMapping
    suspend fun search(
        @RequestParam ticker: String?,
        @RequestParam q: String?,
        @RequestParam sentiment: String?,
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestParam(defaultValue = "20") limit: Int,
        @RequestParam(defaultValue = "publishedAt_desc") sort: String
    ): PagedResponse<ArticleResponse> {
        val effectiveLimit = limit.coerceIn(1, 100)
        val page = if (effectiveLimit > 0) offset / effectiveLimit else 0
        val pageable = PageRequest.of(page, effectiveLimit, parseSort(sort))

        val spec = Specification
            .where(ArticleSpecifications.withTicker(ticker))
            .and(ArticleSpecifications.withSentiment(sentiment))
            .and(ArticleSpecifications.withKeyword(q))

        val result = withContext(Dispatchers.IO) {
            articleRepository.findAll(spec, pageable)
        }
        return PagedResponse(
            data = result.content.map { it.toResponse() },
            offset = offset,
            limit = effectiveLimit,
            total = result.totalElements
        )
    }

    @GetMapping("/{id}")
    suspend fun getById(@PathVariable id: Long): ResponseEntity<ArticleResponse> {
        val article = withContext(Dispatchers.IO) {
            articleRepository.findById(id).orElse(null)
        } ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(article.toResponse())
    }
}
