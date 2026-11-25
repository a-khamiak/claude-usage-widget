package io.github.akhamiak.claudeusage

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MemoryTokens(var tokens: Tokens? = null, var pending: PendingLogin? = null) : TokenStorage {
    override fun load() = tokens
    override fun save(t: Tokens) { tokens = t }
    override fun clear() { tokens = null; pending = null }
    override fun loadPending() = pending
    override fun savePending(p: PendingLogin?) { pending = p }
}

/** MockWebServer scripts for 200 / 401 / 403 / 429+Retry-After / 500 / timeout / malformed / rotation (§10). */
class NetworkTest {
    private lateinit var server: MockWebServer
    private lateinit var store: MemoryTokens
    private lateinit var cfg: AppConfig
    private var now = 1_800_000_000_000L

    private val ok = """{"limits":[{"kind":"session","percent":38,"resets_at":"2026-11-25T22:40:00Z"},
        {"kind":"weekly_all","percent":61,"resets_at":"2026-12-02T13:00:00Z"}]}"""

    private fun tokenBody(access: String, refresh: String) =
        """{"access_token":"$access","refresh_token":"$refresh","expires_in":28800,"scope":"user:profile"}"""

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        store = MemoryTokens(Tokens("A1", "R1", now + 3_600_000, "user:profile"))
        cfg = AppConfig(
            usageUrl = server.url("/api/oauth/usage").toString(),
            tokenUrl = server.url("/v1/oauth/token").toString(),
        )
    }

    @After fun tearDown() = server.shutdown()

    private fun client(http: Http = Http(2_000)): UsageClient {
        val auth = Auth(store, http, { cfg }, { now })
        return UsageClient(store, auth, http, { cfg }, { now })
    }

    private fun json(code: Int, body: String) = MockResponse().setResponseCode(code).setBody(body)

    @Test fun okSendsHeadersAndParses() {
        server.enqueue(json(200, ok))
        val r = client().fetch() as FetchResult.Ok
        assertEquals(38, r.reading.sessionPct)
        val req = server.takeRequest()
        assertEquals("Bearer A1", req.getHeader("Authorization"))
        assertEquals("oauth-2025-04-20", req.getHeader("anthropic-beta"))
        assertEquals("application/json", req.getHeader("Accept"))
    }

    @Test fun unauthorizedRefreshesOnceRotatesAndRetries() {
        server.enqueue(json(401, "{}"))
        server.enqueue(json(200, tokenBody("A2", "R2")))
        server.enqueue(json(200, ok))
        assertTrue(client().fetch() is FetchResult.Ok)
        assertEquals("A2", store.tokens!!.access)
        assertEquals("R2", store.tokens!!.refresh)
        server.takeRequest()
        val refresh = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals("refresh_token", refresh.getString("grant_type"))
        assertEquals("R1", refresh.getString("refresh_token"))
        assertEquals("Bearer A2", server.takeRequest().getHeader("Authorization"))
    }

    @Test fun refreshRefusedMeansSignedOut() {
        server.enqueue(json(401, "{}"))
        server.enqueue(json(400, """{"error":"invalid_grant"}"""))
        assertEquals(FetchResult.SignedOut, client().fetch())
    }

    @Test fun forbiddenWithProfileOnlyScopeAsksForUpgrade() {
        server.enqueue(json(403, "{}"))
        server.enqueue(json(200, tokenBody("A2", "R2")))
        server.enqueue(json(403, "{}"))
        assertEquals(FetchResult.ScopeMissing, client().fetch())
    }

    @Test fun plain429IsRateLimitedWithoutRefresh() {
        server.enqueue(json(429, "{}").addHeader("Retry-After", "120"))
        assertEquals(FetchResult.RateLimited(120), client().fetch())
        assertEquals(1, server.requestCount)
    }

    @Test fun expiredTokenTrapRefreshesAndRetries() {
        // Token looked valid by our margin but the server's expiry passed: long Retry-After triggers the trap.
        server.enqueue(json(429, "{}").addHeader("Retry-After", "3600"))
        server.enqueue(json(200, tokenBody("A2", "R2")))
        server.enqueue(json(200, ok))
        assertTrue(client().fetch() is FetchResult.Ok)
        assertEquals(3, server.requestCount)
    }

    @Test fun trapRetryStill429IsRateLimited() {
        server.enqueue(json(429, "{}").addHeader("Retry-After", "900"))
        server.enqueue(json(200, tokenBody("A2", "R2")))
        server.enqueue(json(429, "{}").addHeader("Retry-After", "900"))
        assertEquals(FetchResult.RateLimited(900), client().fetch())
    }

    @Test fun trapRefreshFailingIsSignedOut() {
        server.enqueue(json(429, "{}").addHeader("Retry-After", "900"))
        server.enqueue(json(401, "{}"))
        assertEquals(FetchResult.SignedOut, client().fetch())
    }

    @Test fun expiredAccessRefreshesBeforeCalling() {
        store.tokens = store.tokens!!.copy(expiresAtMs = now - 1)
        server.enqueue(json(200, tokenBody("A2", "R2")))
        server.enqueue(json(200, ok))
        assertTrue(client().fetch() is FetchResult.Ok)
        assertEquals("/v1/oauth/token", server.takeRequest().path)
    }

    @Test fun tokenEndpoint429IsRateLimited() {
        store.tokens = store.tokens!!.copy(expiresAtMs = now - 1)
        server.enqueue(json(429, "{}"))
        assertTrue(client().fetch() is FetchResult.RateLimited)
    }

    @Test fun serverErrorAndMalformedAndNotFound() {
        server.enqueue(json(503, "busy").addHeader("Retry-After", "60"))
        assertEquals(FetchResult.ServerError(503, 60), client().fetch())
        server.enqueue(json(200, "{not json"))
        assertEquals(FetchResult.Invalid(200), client().fetch())
        server.enqueue(json(404, "{}"))
        assertEquals(FetchResult.Invalid(404), client().fetch())
    }

    @Test fun timeoutIsOffline() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        assertEquals(FetchResult.Offline, client(Http(300)).fetch())
    }

    @Test fun noTokensIsSignedOut() {
        store.tokens = null
        assertEquals(FetchResult.SignedOut, client().fetch())
        assertEquals(0, server.requestCount)
    }

    @Test fun redirectsAreNotFollowed() {
        server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "https://evil.example.com/"))
        assertEquals(FetchResult.Invalid(302), client().fetch())
        assertEquals(1, server.requestCount)
    }
}

class AuthTest {
    private lateinit var server: MockWebServer
    private lateinit var store: MemoryTokens
    private lateinit var auth: Auth

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        store = MemoryTokens()
        val cfg = AppConfig(tokenUrl = server.url("/v1/oauth/token").toString())
        auth = Auth(store, Http(2_000), { cfg }, { 1_000L })
    }

    @After fun tearDown() = server.shutdown()

    @Test fun loginUrlHasPkceAndState() {
        val url = auth.startLogin("user:profile")
        val p = store.pending!!
        assertTrue(url.startsWith("https://claude.com/cai/oauth/authorize?code=true&"))
        assertTrue(url.contains("code_challenge_method=S256"))
        assertTrue(url.contains("scope=user%3Aprofile"))
        assertTrue(url.contains("state=${p.state}"))
        val expected = Auth.b64url(java.security.MessageDigest.getInstance("SHA-256").digest(p.verifier.toByteArray()))
        assertTrue(url.contains("code_challenge=$expected"))
    }

    @Test fun missingOrWrongStateRejectedWithoutNetwork() {
        auth.startLogin("user:profile")
        assertEquals(LoginResult.StateMismatch, auth.completeLogin("abc"))
        assertEquals(LoginResult.StateMismatch, auth.completeLogin("abc#"))
        assertEquals(LoginResult.StateMismatch, auth.completeLogin("abc#wrong"))
        assertEquals(0, server.requestCount)
        assertNull(store.tokens)
    }

    @Test fun successfulLoginStoresTokensAndClearsPending() {
        auth.startLogin("user:profile")
        val state = store.pending!!.state
        server.enqueue(MockResponse().setBody("""{"access_token":"A","refresh_token":"R","expires_in":100}"""))
        assertEquals(LoginResult.Ok, auth.completeLogin("  thecode#$state \n"))
        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals("authorization_code", body.getString("grant_type"))
        assertEquals("thecode", body.getString("code"))
        assertNotNull(body.getString("code_verifier"))
        assertEquals("A", store.tokens!!.access)
        assertEquals(101_000L, store.tokens!!.expiresAtMs)
        assertEquals("user:profile", store.tokens!!.scope)
        assertNull(store.pending)
    }

    @Test fun rejectedCode() {
        auth.startLogin("user:profile")
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"invalid_grant"}"""))
        assertEquals(LoginResult.Rejected, auth.completeLogin("c#${store.pending!!.state}"))
    }

    @Test fun refreshKeepsOldRefreshTokenIfNotRotated() {
        store.tokens = Tokens("A", "R", 0, "user:profile")
        server.enqueue(MockResponse().setBody("""{"access_token":"B","expires_in":100}"""))
        val r = auth.refresh("A") as RefreshResult.Ok
        assertEquals("R", r.tokens.refresh)
    }

    @Test fun refreshSkipsNetworkIfAlreadyReplaced() {
        store.tokens = Tokens("NEW", "R", 10_000_000, "user:profile")
        assertTrue(auth.refresh("OLD") is RefreshResult.Ok)
        assertEquals(0, server.requestCount)
    }
}
