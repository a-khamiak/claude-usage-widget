package io.github.akhamiak.claudeusage

import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

sealed class RefreshResult {
    data class Ok(val tokens: Tokens) : RefreshResult()
    object SignedOut : RefreshResult()
    data class RateLimited(val retryAfterS: Long?) : RefreshResult()
    object Offline : RefreshResult()
    data class Failed(val code: Int) : RefreshResult()
}

sealed class LoginResult {
    object Ok : LoginResult()
    object NoPendingLogin : LoginResult()
    object StateMismatch : LoginResult()
    object Rejected : LoginResult()
    object RateLimited : LoginResult()
    object Offline : LoginResult()
    data class Failed(val code: Int) : LoginResult()
}

/**
 * PKCE sign-in (manual paste-the-code flow) and token refresh.
 * Ported from Claude Pulse's TokenManager (MIT, see NOTICE) with the §5 fixes:
 * state is mandatory, URLs/client ID come from config, refresh is single-flight across the process.
 */
class Auth(
    private val store: TokenStorage,
    private val http: Http,
    private val config: () -> AppConfig,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val random = SecureRandom()

    /** Creates and stores a pending login; returns the authorize URL to open in the browser. */
    fun startLogin(scopes: String = config().scopes): String {
        val cfg = config()
        val verifier = randomUrlSafe(32)
        val state = randomUrlSafe(32)
        val challenge = b64url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
        store.savePending(PendingLogin(verifier, state, scopes))
        val q = linkedMapOf(
            "code" to "true",
            "client_id" to cfg.clientId,
            "response_type" to "code",
            "redirect_uri" to cfg.redirectUrl,
            "scope" to scopes,
            "code_challenge" to challenge,
            "code_challenge_method" to "S256",
            "state" to state,
        )
        return cfg.authorizeUrl + "?" + q.entries.joinToString("&") { "${it.key}=${enc(it.value)}" }
    }

    /** Exchanges the pasted "code#state" for tokens. The state part is required and must match. */
    fun completeLogin(pasted: String): LoginResult {
        val pending = store.loadPending() ?: return LoginResult.NoPendingLogin
        val (code, state) = splitPasted(pasted) ?: return LoginResult.StateMismatch
        if (!constantTimeEquals(state, pending.state)) return LoginResult.StateMismatch
        val cfg = config()
        val body = JSONObject()
            .put("grant_type", "authorization_code")
            .put("code", code)
            .put("redirect_uri", cfg.redirectUrl)
            .put("client_id", cfg.clientId)
            .put("code_verifier", pending.verifier)
            .put("state", state)
        val resp = try {
            post(cfg.tokenUrl, body)
        } catch (_: IOException) {
            return LoginResult.Offline
        }
        return when {
            resp.code == 200 -> {
                val t = parseTokens(resp.body, previousRefresh = null, requestedScope = pending.scopes)
                    ?: return LoginResult.Failed(200)
                store.save(t)
                store.savePending(null)
                LoginResult.Ok
            }
            isRateLimited(resp) -> LoginResult.RateLimited
            resp.code == 400 || resp.code == 401 -> LoginResult.Rejected
            else -> LoginResult.Failed(resp.code)
        }
    }

    /**
     * Refreshes the access token. [rejectedAccess] is the token that just failed (or expired);
     * if another caller already replaced it, the new one is returned without a network call.
     */
    fun refresh(rejectedAccess: String?): RefreshResult = synchronized(LOCK) {
        val current = store.load() ?: return RefreshResult.SignedOut
        if (current.access != rejectedAccess && current.expiresAtMs - EXPIRY_MARGIN_MS > clock()) {
            return RefreshResult.Ok(current)
        }
        val refresh = current.refresh ?: return RefreshResult.SignedOut
        val cfg = config()
        val body = JSONObject()
            .put("grant_type", "refresh_token")
            .put("refresh_token", refresh)
            .put("client_id", cfg.clientId)
        val resp = try {
            post(cfg.tokenUrl, body)
        } catch (_: IOException) {
            return RefreshResult.Offline
        }
        when {
            resp.code == 200 -> {
                val t = parseTokens(resp.body, previousRefresh = refresh, requestedScope = current.scope)
                    ?: return RefreshResult.Failed(200)
                store.save(t) // rotated refresh token saved atomically before use
                RefreshResult.Ok(t)
            }
            isRateLimited(resp) -> RefreshResult.RateLimited(resp.retryAfterS)
            resp.code == 400 || resp.code == 401 -> RefreshResult.SignedOut
            else -> RefreshResult.Failed(resp.code)
        }
    }

    private fun post(url: String, body: JSONObject): HttpResponse =
        http.request(
            "POST", url,
            mapOf("Content-Type" to "application/json", "Accept" to "application/json"),
            body.toString(),
        )

    private fun parseTokens(raw: String, previousRefresh: String?, requestedScope: String): Tokens? = try {
        val j = JSONObject(raw)
        val access = j.getString("access_token").takeIf { it.isNotBlank() } ?: return null
        val refresh = j.optString("refresh_token").takeIf { it.isNotBlank() && it != "null" } ?: previousRefresh
        val expiresIn = j.optLong("expires_in", 3600).coerceIn(60, 30L * 24 * 3600)
        val scope = j.optString("scope").takeIf { it.isNotBlank() && it != "null" } ?: requestedScope
        Tokens(access, refresh, clock() + expiresIn * 1000, scope)
    } catch (_: Exception) {
        null
    }

    private fun randomUrlSafe(bytes: Int): String = b64url(ByteArray(bytes).also { random.nextBytes(it) })

    companion object {
        private val LOCK = Any()
        const val EXPIRY_MARGIN_MS = 60_000L

        fun b64url(data: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(data)

        private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

        /** "code#state" → pair; a missing state is rejected (Pulse let it through). */
        fun splitPasted(pasted: String): Pair<String, String>? {
            val s = pasted.trim()
            val i = s.indexOf('#')
            if (i <= 0 || i == s.length - 1) return null
            val code = s.substring(0, i)
            val state = s.substring(i + 1)
            if (code.any { it.isWhitespace() } || state.any { it.isWhitespace() }) return null
            return code to state
        }

        fun constantTimeEquals(a: String, b: String): Boolean =
            MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))

        fun isRateLimited(r: HttpResponse) = r.code == 429 || r.body.contains("rate_limit_error")
    }
}
