package com.hyunprojects.finpulse.api.dto

data class PagedResponse<T>(
    val data: List<T>,
    val offset: Int,
    val limit: Int,
    val total: Long
)
