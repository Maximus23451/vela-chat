package com.vela.chat

import com.vela.chat.data.a2a.A2aClient
import org.junit.Assert.assertEquals
import org.junit.Test

/** The bearer token must only go to the configured gateway's origin, whatever the card says. */
class A2aEndpointTest {

    private val base = "http://192.0.2.10:9900/"

    @Test
    fun `card url on the same origin is used`() {
        assertEquals("http://192.0.2.10:9900/a2a", A2aClient.resolveEndpoint("http://192.0.2.10:9900/a2a", base))
    }

    @Test
    fun `card url on another host falls back to the base url`() {
        assertEquals(base, A2aClient.resolveEndpoint("https://attacker.example/", base))
    }

    @Test
    fun `card url on another port or scheme falls back`() {
        assertEquals(base, A2aClient.resolveEndpoint("http://192.0.2.10:8080/", base))
        val httpsBase = "https://192.0.2.10:9900/"
        assertEquals(httpsBase, A2aClient.resolveEndpoint("http://192.0.2.10:9900/", httpsBase))
    }

    @Test
    fun `gateway describing itself as localhost falls back`() {
        assertEquals(base, A2aClient.resolveEndpoint("http://localhost:9900/", base))
    }

    @Test
    fun `missing or malformed card url falls back`() {
        assertEquals(base, A2aClient.resolveEndpoint(null, base))
        assertEquals(base, A2aClient.resolveEndpoint("not a url", base))
    }
}
