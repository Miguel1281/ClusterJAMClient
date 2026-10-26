package com.clusterjam.mobile.features.auth.data.mapper

import com.clusterjam.mobile.features.auth.domain.model.AuthFailure
import java.io.IOException
import okhttp3.MediaType
import okhttp3.ResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class AuthErrorMapperTest {
    @Test
    fun knownBusinessCodeDoesNotExposeServerDetail() {
        val failure = mapAuthError(
            httpError(
                401,
                """{"code":"INVALID_CREDENTIALS","detail":"Private provider diagnostic"}"""
            )
        )

        assertEquals("INVALID_CREDENTIALS", failure.code)
        assertFalse(failure.message.orEmpty().contains("Private provider diagnostic"))
    }

    @Test
    fun unknownCodeAndHttpStatusArePreserved() {
        val failure = mapAuthError(httpError(429, """{"code":"FUTURE_LIMIT_CODE"}"""))

        assertEquals("FUTURE_LIMIT_CODE", failure.code)
        assertEquals(429, failure.status)
    }

    @Test
    fun malformedOrEmptyProblemBodyFallsBackToHttpStatus() {
        for (body in listOf("<html>Bad gateway</html>", "", "null", "{}", "{\"code\":\" \"}")) {
            val failure = mapAuthError(httpError(502, body))

            assertEquals("HTTP_502", failure.code)
            assertEquals(502, failure.status)
            assertTrue(failure.validationErrors.isEmpty())
            assertNull(failure.traceId)
        }
    }

    @Test
    fun httpStatusTakesPrecedenceOverStatusInsideProblemBody() {
        val failure = mapAuthError(
            httpError(503, """{"status":200,"code":"SERVICE_UNAVAILABLE"}""")
        )

        assertEquals(503, failure.status)
        assertEquals("SERVICE_UNAVAILABLE", failure.code)
    }

    @Test
    fun optionalProblemMetadataIgnoresEmptyValues() {
        val failure = mapAuthError(
            httpError(
                422,
                """
                {
                    "code":"VALIDATION_ERROR",
                    "errors":[null," ","email must be an email"],
                    "traceId":" "
                }
                """.trimIndent()
            )
        )

        assertEquals(listOf("email must be an email"), failure.validationErrors)
        assertNull(failure.traceId)
    }

    @Test
    fun networkFailureDoesNotExposeExceptionDetails() {
        val failure = mapAuthError(IOException("Private connection details"))

        assertEquals("NETWORK_ERROR", failure.code)
        assertNull(failure.status)
        assertFalse(failure.message.orEmpty().contains("Private connection details"))
    }

    @Test
    fun domainFailureIsPreserved() {
        val original = AuthFailure("STORAGE_ERROR")

        assertSame(original, mapAuthError(original))
    }

    private fun httpError(status: Int, body: String): HttpException = HttpException(
        Response.error<Unit>(
            status,
            ResponseBody.create(MediaType.get("application/problem+json"), body)
        )
    )
}
