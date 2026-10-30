package com.clusterjam.mobile.core.network

import retrofit2.Call
import retrofit2.Response

/**
 * Shared by data repositories, never by screens. The factory builds a call without executing it.
 * GET/HEAD and unconditional DELETE may be replayed once after renewal. POST/PATCH and conditional
 * DELETE are sent at most once.
 */
interface SessionAccess {
    suspend fun <T> execute(request: (authorization: String) -> Call<T>): Result<Response<T>>
}
