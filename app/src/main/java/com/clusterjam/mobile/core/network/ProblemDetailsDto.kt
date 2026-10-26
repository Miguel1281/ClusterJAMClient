package com.clusterjam.mobile.core.network

data class ProblemDetailsDto(
    val type: String? = null,
    val title: String? = null,
    val status: Int? = null,
    val detail: String? = null,
    val instance: String? = null,
    val code: String? = null,
    val errors: List<String?>? = null,
    val traceId: String? = null
)
