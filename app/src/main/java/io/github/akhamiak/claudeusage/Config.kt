package io.github.akhamiak.claudeusage

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL

data class Fields(
    val limitsArray: String = "limits",
    val kind: String = "kind",
    val percent: String = "percent",
    val resetsAt: String = "resets_at",
    val sessionKinds: List<String> = listOf("session"),
    val weeklyKinds: List<String> = listOf("weekly_all"),
    val legacySession: String = "five_hour",
    val legacyWeekly: String = "seven_day",
    val legacyPercent: String = "utilization",
)

data class Polling(
    val tapCooldownS: Int = 120,
    val activeIntervalMin: Int = 15,
    val idleIntervalMin: Int = 60,
    val idleAfterMin: Int = 90,
    val backoffStartMin: Int = 5,
    val backoffMaxMin: Int = 240,
    val staleAfterMin: Int = 20,
    val configTtlH: Int = 24,
)

data class AppConfig(
    val disabled: Boolean = false,
    val message: String = "",
    val updateRequired: Boolean = false,
    val usageUrl: String = "https://api.anthropic.com/api/oauth/usage",
    val usageQuery: String = "",
    val betaHeader: String = "oauth-2025-04-20",
    val authorizeUrl: String = "https://claude.com/cai/oauth/authorize",
    val tokenUrl: String = "https://platform.claude.com/v1/oauth/token",
    val redirectUrl: String = "https://platform.claude.com/oauth/code/callback",
    val clientId: String = "9d1c250a-e61b-44d9-88ed-5944d1962f5e",
    val scopes: String = "user:profile",
    val fields: Fields = Fields(),
    val polling: Polling = Polling(),
) {
    val fullUsageUrl: String get() = usageUrl + usageQuery
}

/** Parsing + validation of config/endpoint.json. Pure; no Android dependencies. */
object ConfigParser {
    /** Hard-coded, not configurable (§6.2): a compromised repo can't point the token anywhere else. */
    val ALLOWED_HOSTS = setOf("api.anthropic.com", "platform.claude.com", "claude.com", "claude.ai")
    private val ALLOWED_SCOPES = setOf("user:profile", "user:inference")
    private val CLIENT_ID_RE = Regex("^[0-9a-fA-F-]{8,64}$")
    private val BETA_RE = Regex("^[A-Za-z0-9._,-]{1,128}$")
    private val QUERY_RE = Regex("^(\\?[A-Za-z0-9_=&.-]{0,200})?$")
    private val KIND_RE = Regex("^[A-Za-z0-9_.-]{1,64}$")

    val DEFAULT = AppConfig()

    fun isAllowedUrl(url: String): Boolean = try {
        val u = URL(url)
        u.protocol == "https" && u.host in ALLOWED_HOSTS && u.userInfo == null &&
            (u.port == -1 || u.port == 443) && u.ref == null
    } catch (_: Exception) {
        false
    }

    /** Returns null if the config is invalid in any way; the caller then keeps the cached or built-in one. */
    fun parse(raw: String, appVersionCode: Int): AppConfig? = try {
        val j = JSONObject(raw)
        if (j.optInt("schema", -1) != 1) null else build(j, appVersionCode)
    } catch (_: Exception) {
        null
    }

    private fun build(j: JSONObject, appVersionCode: Int): AppConfig? {
        val d = DEFAULT
        val usageUrl = j.optString("usage_url", d.usageUrl)
        val usageQuery = j.optString("usage_query", d.usageQuery)
        val authorizeUrl = j.optString("authorize_url", d.authorizeUrl)
        val tokenUrl = j.optString("token_url", d.tokenUrl)
        val redirectUrl = j.optString("redirect_url", d.redirectUrl)
        if (!QUERY_RE.matches(usageQuery)) return null
        for (u in listOf(usageUrl, usageUrl + usageQuery, authorizeUrl, tokenUrl, redirectUrl)) {
            if (!isAllowedUrl(u)) return null
        }
        val clientId = j.optString("client_id", d.clientId)
        if (!CLIENT_ID_RE.matches(clientId)) return null
        val beta = j.optString("beta_header", d.betaHeader)
        if (!BETA_RE.matches(beta)) return null
        val scopes = j.optString("scopes", d.scopes).trim()
        val scopeList = scopes.split(' ').filter { it.isNotEmpty() }
        if (scopeList.isEmpty() || !ALLOWED_SCOPES.containsAll(scopeList)) return null

        val fields = j.optJSONObject("fields")?.let { parseFields(it) ?: return null } ?: d.fields
        val polling = j.optJSONObject("polling")?.let { parsePolling(it) } ?: d.polling

        val minVersion = j.optInt("min_app_version", 1)
        return AppConfig(
            disabled = j.optBoolean("disabled", false),
            message = cleanMessage(j.optString("message", "")),
            updateRequired = minVersion > appVersionCode,
            usageUrl = usageUrl,
            usageQuery = usageQuery,
            betaHeader = beta,
            authorizeUrl = authorizeUrl,
            tokenUrl = tokenUrl,
            redirectUrl = redirectUrl,
            clientId = clientId,
            scopes = scopeList.joinToString(" "),
            fields = fields,
            polling = polling,
        )
    }

    private fun cleanMessage(s: String): String =
        s.uppercase().filter { it.isLetterOrDigit() || it in " ·:.-/" }.take(24).trim()

    private fun parseFields(f: JSONObject): Fields? {
        val d = Fields()
        fun key(name: String, def: String): String? =
            f.optString(name, def).takeIf { KIND_RE.matches(it) }
        fun list(name: String, def: List<String>): List<String>? {
            val arr: JSONArray = f.optJSONArray(name) ?: return def
            val out = (0 until arr.length()).map { arr.optString(it) }
            return out.takeIf { it.isNotEmpty() && it.all { k -> KIND_RE.matches(k) } }
        }
        return Fields(
            limitsArray = key("limits_array", d.limitsArray) ?: return null,
            kind = key("kind", d.kind) ?: return null,
            percent = key("percent", d.percent) ?: return null,
            resetsAt = key("resets_at", d.resetsAt) ?: return null,
            sessionKinds = list("session_kinds", d.sessionKinds) ?: return null,
            weeklyKinds = list("weekly_kinds", d.weeklyKinds) ?: return null,
            legacySession = key("legacy_session", d.legacySession) ?: return null,
            legacyWeekly = key("legacy_weekly", d.legacyWeekly) ?: return null,
            legacyPercent = key("legacy_percent", d.legacyPercent) ?: return null,
        )
    }

    /** Numeric bounds are enforced by clamping (§6.2), so a bad value can't hammer the endpoint. */
    private fun parsePolling(p: JSONObject): Polling {
        val d = Polling()
        fun v(name: String, def: Int, min: Int, max: Int) = p.optInt(name, def).coerceIn(min, max)
        return Polling(
            tapCooldownS = v("tap_cooldown_s", d.tapCooldownS, 60, 3600),
            activeIntervalMin = v("active_interval_min", d.activeIntervalMin, 15, 240),
            idleIntervalMin = v("idle_interval_min", d.idleIntervalMin, 15, 720),
            idleAfterMin = v("idle_after_min", d.idleAfterMin, 15, 1440),
            backoffStartMin = v("backoff_start_min", d.backoffStartMin, 1, 60),
            backoffMaxMin = v("backoff_max_min", d.backoffMaxMin, 15, 1440),
            staleAfterMin = v("stale_after_min", d.staleAfterMin, 5, 1440),
            configTtlH = v("config_ttl_h", d.configTtlH, 1, 168),
        )
    }
}

/** Cached remote config. Fetched from the public repo at most once per TTL, or on demand from the UPDATE state. */
class ConfigStore(context: Context, private val http: Http = Http()) {
    private val prefs = context.applicationContext.getSharedPreferences("config", Context.MODE_PRIVATE)

    fun current(): AppConfig {
        val raw = prefs.getString(KEY_RAW, null) ?: return ConfigParser.DEFAULT
        return ConfigParser.parse(raw, BuildConfig.VERSION_CODE) ?: ConfigParser.DEFAULT
    }

    /** Returns true if a fetch was attempted and a valid config is now cached. */
    fun refreshIfDue(now: Long, force: Boolean = false): Boolean {
        val last = prefs.getLong(KEY_FETCHED_AT, 0)
        val ttlMs = current().polling.configTtlH * 3_600_000L
        if (!force && now - last < ttlMs) return false
        prefs.edit().putLong(KEY_FETCHED_AT, now).apply()
        return try {
            val resp = http.request("GET", REMOTE_URL, mapOf("Accept" to "application/json"))
            if (resp.code != 200) return false
            if (ConfigParser.parse(resp.body, BuildConfig.VERSION_CODE) == null) return false
            prefs.edit().putString(KEY_RAW, resp.body).apply()
            true
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        const val REPO_URL = "https://github.com/a-khamiak/claude-usage-widget"
        const val RELEASES_URL = "$REPO_URL/releases/latest"
        private const val REMOTE_URL =
            "https://raw.githubusercontent.com/a-khamiak/claude-usage-widget/main/config/endpoint.json"
        private const val KEY_RAW = "raw"
        private const val KEY_FETCHED_AT = "fetched_at"
    }
}
