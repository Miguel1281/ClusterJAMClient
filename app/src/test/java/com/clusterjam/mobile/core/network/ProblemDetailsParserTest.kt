package com.clusterjam.mobile.core.network

import okhttp3.MediaType
import okhttp3.ResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProblemDetailsParserTest {
    private val parser = ProblemDetailsParser()

    @Test
    fun readsCommonFieldsAndExtensionsWithoutInterpretingBusinessRules() {
        val problem = parser.parse(body("""
            {
                "type":"about:blank",
                "title":"Unprocessable Entity",
                "status":422,
                "detail":"Request validation failed.",
                "instance":"/api/v1/sessions",
                "code":"VALIDATION_ERROR",
                "errors":[null,"email must be an email"],
                "traceId":"trace-id"
            }
        """.trimIndent()))

        requireNotNull(problem)
        assertEquals("about:blank", problem.type)
        assertEquals("Unprocessable Entity", problem.title)
        assertEquals(422, problem.status)
        assertEquals("Request validation failed.", problem.detail)
        assertEquals("/api/v1/sessions", problem.instance)
        assertEquals("VALIDATION_ERROR", problem.code)
        assertEquals(listOf(null, "email must be an email"), problem.errors)
        assertEquals("trace-id", problem.traceId)
    }

    @Test
    fun absentEmptyAndMalformedBodiesAreOptional() {
        assertNull(parser.parse(null))
        for (value in listOf("", "null", "<html>Bad gateway</html>", "[]", "{invalid")) {
            assertNull(parser.parse(body(value)))
        }
    }

    private fun body(value: String): ResponseBody =
        ResponseBody.create(MediaType.get("application/problem+json"), value)
}
