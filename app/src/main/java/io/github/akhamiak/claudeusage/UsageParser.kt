package io.github.akhamiak.claudeusage

import org.json.JSONObject
import java.time.Instant
import java.time.OffsetDateTime
import kotlin.math.floor

/** One usage snapshot. Percent is floored so 99.6 never reads as LIMIT. Reset times in epoch ms. */
data class Reading(
    val sessionPct: Int,
    val sessionResetsAt: Long?,
    val weekPct: Int,
    val weekResetsAt: Long?,
)

/**
 * Both response shapes → [Reading], and the §6.3 health check: anything that doesn't
 * validate returns null (counted as a validation failure by the caller).
 */
object UsageParser {

    fun parse(body: String, f: Fields): Reading? {
        val json = try {
            JSONObject(body)
        } catch (_: Exception) {
            return null
        }
        return parseLimits(json, f) ?: parseLegacy(json, f)
    }

    private data class Window(val pct: Int, val resetsAt: Long?)

    private fun parseLimits(json: JSONObject, f: Fields): Reading? {
        val arr = json.optJSONArray(f.limitsArray) ?: return null
        var session: Window? = null
        var week: Window? = null
        for (i in 0 until arr.length()) {
            val e = arr.optJSONObject(i) ?: continue
            val kind = e.optString(f.kind)
            val isSession = kind in f.sessionKinds
            val isWeek = kind in f.weeklyKinds
            if (!isSession && !isWeek) continue
            val w = window(e, f.percent, f.resetsAt, resetRequiredAboveZero = true) ?: return null
            if (isSession && session == null) session = w
            if (isWeek && week == null) week = w
        }
        if (session == null || week == null) return null
        return Reading(session.pct, session.resetsAt, week.pct, week.resetsAt)
    }

    private fun parseLegacy(json: JSONObject, f: Fields): Reading? {
        val s = json.optJSONObject(f.legacySession) ?: return null
        val w = json.optJSONObject(f.legacyWeekly) ?: return null
        val sw = window(s, f.legacyPercent, f.resetsAt, resetRequiredAboveZero = false) ?: return null
        val ww = window(w, f.legacyPercent, f.resetsAt, resetRequiredAboveZero = false) ?: return null
        return Reading(sw.pct, sw.resetsAt, ww.pct, ww.resetsAt)
    }

    private fun window(o: JSONObject, pctKey: String, resetKey: String, resetRequiredAboveZero: Boolean): Window? {
        val raw = o.opt(pctKey) as? Number ?: return null
        val pct = raw.toDouble()
        if (pct.isNaN() || pct < 0.0 || pct > 100.0) return null
        val resetRaw = if (o.isNull(resetKey)) null else o.opt(resetKey)
        val resetsAt: Long? = when (resetRaw) {
            null -> null
            is String -> parseIso(resetRaw) ?: return null
            else -> return null
        }
        if (resetRequiredAboveZero && resetsAt == null && pct > 0.0) return null
        return Window(floor(pct).toInt(), resetsAt)
    }

    fun parseIso(s: String): Long? = try {
        OffsetDateTime.parse(s).toInstant().toEpochMilli()
    } catch (_: Exception) {
        try {
            Instant.parse(s).toEpochMilli()
        } catch (_: Exception) {
            null
        }
    }
}
