package com.hyunprojects.finpulse.api

import com.hyunprojects.finpulse.api.dto.FetchRequest
import com.hyunprojects.finpulse.ingestion.NewsFetcherService
import com.hyunprojects.finpulse.service.VolumeService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/admin")
class AdminController(
    private val newsFetcherService: NewsFetcherService,
    private val volumeService: VolumeService
) {

    @PostMapping("/fetch")
    suspend fun fetch(@RequestBody request: FetchRequest): ResponseEntity<Void> {
        withContext(Dispatchers.IO) {
            newsFetcherService.fetchForTicker(request.ticker)
        }
        return ResponseEntity.accepted().build()
    }

    @PostMapping("/volume/{ticker}")
    suspend fun fetchVolume(@PathVariable ticker: String): ResponseEntity<Void> {
        withContext(Dispatchers.IO) {
            volumeService.fetchAndStore(ticker.uppercase())
        }
        return ResponseEntity.accepted().build()
    }
}
