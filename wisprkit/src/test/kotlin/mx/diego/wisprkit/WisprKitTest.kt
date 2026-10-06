package mx.diego.wisprkit

import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

// Fixtures sintéticos: nada de datos reales.
class WisprKitTest {
    private val server = MockWebServer()

    @BeforeTest fun start() = server.start()
    @AfterTest fun stop() = server.close()

    private fun json(body: String, sessionId: String? = null) = MockResponse.Builder()
        .code(200)
        .addHeader("Content-Type", "application/json")
        .apply { sessionId?.let { addHeader("Mcp-Session-Id", it) } }
        .body(body)
        .build()

    private fun sse(body: String) = MockResponse.Builder()
        .code(200)
        .addHeader("Content-Type", "text/event-stream")
        .body(body)
        .build()

    private fun toolText(id: Int, text: String) =
        """{"jsonrpc":"2.0","id":$id,"result":{"content":[{"type":"text","text":${kotlinx.serialization.json.JsonPrimitive(text)}}]}}"""

    private fun handshake(sessionId: String = "s1") {
        server.enqueue(json("""{"jsonrpc":"2.0","id":1,"result":{}}""", sessionId))
        server.enqueue(MockResponse.Builder().code(202).build())
    }

    private fun service() = WisprService(MCPClient(server.url("/mcp").toString(), "tok"))

    @Test fun decodesBudgetsAndSendsHeaders() = runBlocking {
        handshake()
        server.enqueue(json(toolText(2, """{"currency":"MXN","budgets":[{"id":"b","name":"Comida","period_type":"monthly",
            "current_period":{"start_date":"2026-01-01","end_date":"2026-01-31","allocated_amount":10000,
            "carried_over_amount":0,"spent_amount":12000,"remaining_amount":-2000,"processing_historical":false}}]}""")))
        val budgets = service().budgets()
        val period = budgets.budgets.single().currentPeriod!!
        assertEquals(1.2, period.spentFraction)
        assertEquals(true, period.isOver)

        val init = server.takeRequest()
        assertEquals("Bearer tok", init.headers["Authorization"])
        assertNull(init.headers["Mcp-Session-Id"])
        server.takeRequest()
        val call = server.takeRequest()
        assertEquals("s1", call.headers["Mcp-Session-Id"])
        assertEquals(MCPClient.PROTOCOL_VERSION, call.headers["MCP-Protocol-Version"])
    }

    @Test fun parsesSseAndRenegotiatesExpiredSession() = runBlocking {
        handshake()
        server.enqueue(json(toolText(2, """{"space_id":"x","accounts":[]}""")))
        server.enqueue(MockResponse.Builder().code(404).build())
        handshake("s2")
        server.enqueue(sse("event: message\ndata: ${toolText(5, """{"space_id":"x","accounts":[{"id":"a","name":"Débito","type":"checking","currency":"MXN"}]}""")}\n\n"))
        val s = service()
        s.accounts()
        assertEquals("Débito", s.accounts().accounts.single().name)
    }

    @Test fun mapsUnauthorizedAndToolErrors() = runBlocking {
        server.enqueue(MockResponse.Builder().code(401).build())
        assertFailsWith<WisprException.Unauthorized> { service().budgets() }
        handshake()
        server.enqueue(json("""{"jsonrpc":"2.0","id":2,"result":{"isError":true,"content":[{"type":"text","text":"mal"}]}}"""))
        assertFailsWith<WisprException.Tool> { service().budgets() }
        Unit
    }

    @Test fun netWorthPointHasDynamicKeys() {
        val r = WisprJson.decodeFromString<NetWorthResponse>("""{"current":{"current":5,"previous":4,"currency_code":"MXN"},
            "evolution":{"data":[{"month":"2026-02","timestamp":2,"a":7},{"month":"2026-01","timestamp":1,"a":3,"b":-9}]}}""")
        assertEquals(mapOf("a" to 7L, "b" to -9L), r.latestBalances)
    }

    @Test fun moneyUsesCurrencyExponent() {
        assertEquals("$1,234.50", Money.format(123450, "MXN").replace(" ", ""))
        assertEquals(1234L, Money.minor(java.math.BigDecimal("1234"), "CLP"))
        assertEquals(123450L, Money.minor(java.math.BigDecimal("1234.5"), "MXN"))
    }
}
