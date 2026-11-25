package io.github.akhamiak.claudeusage

import java.io.IOException

sealed class FetchResult {
    data class Ok(val reading: Reading) : FetchResult()
    /** Response failed the §6.3 health check (or a non-auth 4xx such as 404). */
    data class Invalid(val code: Int) : FetchResult()
    object SignedOut : FetchResult()
    /** 403 that survived a token refresh while the token only has user:profile (D4 fallback). */
    object ScopeMissing : FetchResult()
    data class RateLimited(val retryAfterS: Long?) : FetchResult()
    object Offline : FetchResult()
    data class ServerError(val code: Int, val retryAfterS: Long?) : FetchResult()
}

/** The usage call and the §5.4 classification, including the expired-token 429 trap. */
class UsageClient(
    private val store: TokenStorage,
    private val auth: Auth,
    private val http: Http,
    private val config: () -> AppConfig,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    fun fetch(): FetchResult {
        var tokens = store.load() ?: return FetchResult.SignedOut
        if (tokens.expiresAtMs - Auth.EXPIRY_MARGIN_MS <= clock()) {
            tokens = when (val r = auth.refresh(tokens.access)) {
                is RefreshResult.Ok -> r.tokens
                else -> return fromRefresh(r)
            }
        }
        val first = call(tokens) ?: return FetchResult.Offline
        return when (first.code) {
            200 -> parse(first)
            401, 403 -> retryAfterRefresh(tokens)
            429 -> {
                val expired = tokens.expiresAtMs <= clock()
                val longWait = (first.retryAfterS ?: 0) > TRAP_RETRY_AFTER_S
                if (expired || longWait) retryAfterRefresh(tokens) else FetchResult.RateLimited(first.retryAfterS)
            }
            else -> other(first)
        }
    }

    /** Refresh once, retry once (§5.4). */
    private fun retryAfterRefresh(rejected: Tokens): FetchResult {
        val fresh = when (val r = auth.refresh(rejected.access)) {
            is RefreshResult.Ok -> r.tokens
            is RefreshResult.Offline -> return FetchResult.Offline
            is RefreshResult.RateLimited -> return FetchResult.RateLimited(r.retryAfterS)
            else -> return FetchResult.SignedOut // refresh failed → Signed out
        }
        val second = call(fresh) ?: return FetchResult.Offline
        return when (second.code) {
            200 -> parse(second)
            401 -> FetchResult.SignedOut
            403 -> if ("user:inference" !in fresh.scope.split(' ')) FetchResult.ScopeMissing else FetchResult.SignedOut
            429 -> FetchResult.RateLimited(second.retryAfterS)
            else -> other(second)
        }
    }

    private fun parse(r: HttpResponse): FetchResult =
        UsageParser.parse(r.body, config().fields)?.let { FetchResult.Ok(it) } ?: FetchResult.Invalid(r.code)

    private fun other(r: HttpResponse): FetchResult = when {
        r.code >= 500 -> FetchResult.ServerError(r.code, r.retryAfterS)
        Auth.isRateLimited(r) -> FetchResult.RateLimited(r.retryAfterS)
        else -> FetchResult.Invalid(r.code)
    }

    private fun fromRefresh(r: RefreshResult): FetchResult = when (r) {
        is RefreshResult.Ok -> error("unreachable")
        is RefreshResult.SignedOut -> FetchResult.SignedOut
        is RefreshResult.RateLimited -> FetchResult.RateLimited(r.retryAfterS)
        is RefreshResult.Offline -> FetchResult.Offline
        is RefreshResult.Failed -> FetchResult.ServerError(r.code, null)
    }

    private fun call(t: Tokens): HttpResponse? {
        val cfg = config()
        return try {
            http.request(
                "GET", cfg.fullUsageUrl,
                mapOf(
                    "Authorization" to "Bearer ${t.access}",
                    "anthropic-beta" to cfg.betaHeader,
                    "Accept" to "application/json",
                ),
            )
        } catch (_: IOException) {
            null
        }
    }

    private companion object {
        const val TRAP_RETRY_AFTER_S = 600L
    }
}
