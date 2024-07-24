package ru.somarov.auth.tests.integration

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import ru.somarov.auth.base.BaseIntegrationTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ApplicationTest : BaseIntegrationTest() {
    @Test
    fun `Healthcheck test`() = execute { builder ->
        val response = builder.client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("UP", response.bodyAsText())
    }

    @Test
    fun `Healthcheck test two`() = execute { builder ->
        val response = builder.client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("UP", response.bodyAsText())
    }
}
