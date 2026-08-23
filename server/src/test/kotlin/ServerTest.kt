package dev.juanvega

import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.*

class ServerTest {

    @Test
    fun `test root endpoint`() = testApplication {
        environment {
            config = io.ktor.server.config.MapApplicationConfig(
                "heavyapp.dataFile" to "",
            )
        }
        application {
            rootModule()
        }
        assertEquals(HttpStatusCode.OK, client.get("/").status)
    }
}
