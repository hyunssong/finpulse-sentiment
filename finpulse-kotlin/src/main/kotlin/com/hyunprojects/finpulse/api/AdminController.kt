package com.hyunprojects.finpulse.api

import com.hyunprojects.finpulse.api.dto.FetchRequest
import com.hyunprojects.finpulse.ingestion.NewsFetcherService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/admin")
class AdminController(private val newsFetcherService: NewsFetcherService) {

    @PostMapping("/fetch")
    suspend fun fetch(@RequestBody request: FetchRequest): ResponseEntity<Void> {
        withContext(Dispatchers.IO) {
            newsFetcherService.fetchForTicker(request.ticker)
        }
        return ResponseEntity.accepted().build()
    }
}
