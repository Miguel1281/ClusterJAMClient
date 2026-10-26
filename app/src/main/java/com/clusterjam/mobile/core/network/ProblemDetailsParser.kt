package com.clusterjam.mobile.core.network

import com.google.gson.Gson
import okhttp3.ResponseBody

class ProblemDetailsParser(private val gson: Gson = Gson()) {
    fun parse(body: ResponseBody?): ProblemDetailsDto? = try {
        body?.use { gson.fromJson(it.string(), ProblemDetailsDto::class.java) }
    } catch (_: Exception) {
        null
    }
}
