package com.hyunprojects.finpulse.api

import org.springframework.data.domain.Sort

private val ALLOWED_SORT_FIELDS = setOf("publishedAt", "sentimentScore")

fun parseSort(sort: String): Sort {
    val lastUnderscore = sort.lastIndexOf('_')
    if (lastUnderscore < 1) return Sort.by(Sort.Direction.DESC, "publishedAt")

    val field = sort.substring(0, lastUnderscore)
    val dir = sort.substring(lastUnderscore + 1)

    if (field !in ALLOWED_SORT_FIELDS) return Sort.by(Sort.Direction.DESC, "publishedAt")
    val direction = if (dir.equals("asc", ignoreCase = true)) Sort.Direction.ASC else Sort.Direction.DESC
    return Sort.by(direction, field)
}
