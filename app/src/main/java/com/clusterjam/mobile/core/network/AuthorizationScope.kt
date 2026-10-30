package com.clusterjam.mobile.core.network

import okhttp3.HttpUrl
import okhttp3.Request

internal fun isProtectedApiRequest(request: Request, baseUrl: String): Boolean {
    val base = HttpUrl.get(baseUrl)
    val url = request.url()
    if (url.scheme() != base.scheme() || url.host() != base.host() || url.port() != base.port())
        return false
    val prefix = base.pathSegments().filter(String::isNotEmpty) + listOf("api", "v1")
    val segments = url.pathSegments()
    if (segments.take(prefix.size) != prefix) return false
    val path =
        segments.drop(prefix.size).joinToString("/").trimEnd('/').lowercase(java.util.Locale.ROOT)
    return path !in
        setOf(
            "sessions",
            "session-renewals",
            "registrations",
            "registrations/email-confirmations",
            "registrations/email-confirmation-requests",
            "password-recovery-requests",
            "password-recoveries",
        )
}

internal fun isProtectedProfileRequest(request: Request, baseUrl: String): Boolean {
    val base = HttpUrl.get(baseUrl)
    val url = request.url()
    return url.scheme() == base.scheme() &&
        url.host() == base.host() &&
        url.port() == base.port() &&
        url.pathSegments() ==
            base.pathSegments().filter(String::isNotEmpty) +
                listOf("api", "v1", "profiles", "me") &&
        request.method() in setOf("GET", "PATCH")
}
