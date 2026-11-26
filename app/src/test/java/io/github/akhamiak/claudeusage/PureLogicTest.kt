package io.github.akhamiak.claudeusage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime

class ConfigParserTest {
    private val repoConfig = File("../config/endpoint.json").takeIf { it.exists() }?.readText()
        ?: File("config/endpoint.json").readText()

    @Test fun repoConfigIsValidAndMatchesDefaults() {
        val c = ConfigParser.parse(repoConfig, 1)
        assertNotNull(c)
        assertEquals(ConfigParser.DEFAULT, c)
    }

    private fun with(key: String, value: String) =
        repoConfig.replace(Regex("\"$key\"\\s*:\\s*\"[^\"]*\""), "\"$key\": \"$value\"")

    @Test fun foreignHostRejected() {
        assertNull(ConfigParser.parse(with("usage_url", "https://evil.example.com/api/oauth/usage"), 1))
        assertNull(ConfigParser.parse(with("token_url", "https://platform.claude.com.evil.io/v1/oauth/token"), 1))
    }

    @Test fun httpAndUserInfoRejected() {
        assertNull(ConfigParser.parse(with("usage_url", "http://api.anthropic.com/api/oauth/usage"), 1))
        assertNull(ConfigParser.parse(with("authorize_url", "https://x@claude.com/cai/oauth/authorize"), 1))
        assertNull(ConfigParser.parse(with("usage_url", "https://api.anthropic.com:8443/api/oauth/usage"), 1))
    }

    @Test fun queryMustBeSimple() {
        assertNotNull(ConfigParser.parse(with("usage_query", "?cedar_ember=1&skip_spend=1"), 1))
        assertNull(ConfigParser.parse(with("usage_query", "@evil.com/x"), 1))
        assertNull(ConfigParser.parse(with("usage_query", "?a=1#frag"), 1))
    }

    @Test fun scopesLimitedToKnownSet() {
        assertNotNull(ConfigParser.parse(with("scopes", "user:profile user:inference"), 1))
        assertNull(ConfigParser.parse(with("scopes", "user:profile org:admin"), 1))
        assertNull(ConfigParser.parse(with("scopes", ""), 1))
    }

    @Test fun pollingClampedToBounds() {
        val raw = repoConfig.replace("\"tap_cooldown_s\": 120", "\"tap_cooldown_s\": 5")
            .replace("\"active_interval_min\": 15", "\"active_interval_min\": 1")
        val c = ConfigParser.parse(raw, 1)!!
        assertEquals(60, c.polling.tapCooldownS)
        assertEquals(15, c.polling.activeIntervalMin)
    }

    @Test fun wrongSchemaOrGarbageRejected() {
        assertNull(ConfigParser.parse(repoConfig.replace("\"schema\": 1", "\"schema\": 2"), 1))
        assertNull(ConfigParser.parse("not json", 1))
    }

    @Test fun killSwitchAndMinVersion() {
        val raw = repoConfig.replace("\"disabled\": false", "\"disabled\": true")
            .replace("\"message\": \"\"", "\"message\": \"endpoint blocked by anthropic, see repo\"")
        val c = ConfigParser.parse(raw, 1)!!
        assertTrue(c.disabled)
        assertTrue(c.message.length <= 24)
        assertEquals(c.message, c.message.uppercase())
        val old = ConfigParser.parse(repoConfig.replace("\"min_app_version\": 1", "\"min_app_version\": 500"), 100)!!
        assertTrue(old.updateRequired)
    }
}

class UsageParserTest {
    private val f = Fields()

    @Test fun limitsShape() {
        val r = UsageParser.parse(
            """{"limits":[{"kind":"session","percent":38.7,"resets_at":"2026-11-25T22:40:00Z"},
               {"kind":"weekly_all","percent":61,"resets_at":"2026-12-02T13:00:00+00:00"},
               {"kind":"weekly_model","percent":12,"resets_at":null}]}""", f,
        )!!
        assertEquals(38, r.sessionPct)
        assertEquals(61, r.weekPct)
        assertEquals(ZonedDateTime.parse("2026-11-25T22:40:00Z").toInstant().toEpochMilli(), r.sessionResetsAt)
    }

    @Test fun legacyShape() {
        val r = UsageParser.parse(
            """{"five_hour":{"utilization":7.0,"resets_at":"2026-11-25T22:40:00.123456+00:00"},
               "seven_day":{"utilization":18,"resets_at":null}}""", f,
        )!!
        assertEquals(7, r.sessionPct)
        assertEquals(18, r.weekPct)
        assertNull(r.weekResetsAt)
    }

    @Test fun nullResetAllowedOnlyAtZero() {
        assertNotNull(UsageParser.parse("""{"limits":[{"kind":"session","percent":0,"resets_at":null},{"kind":"weekly_all","percent":0}]}""", f))
        assertNull(UsageParser.parse("""{"limits":[{"kind":"session","percent":5,"resets_at":null},{"kind":"weekly_all","percent":0}]}""", f))
    }

    @Test fun invalidResponses() {
        assertNull(UsageParser.parse("<html>", f))
        assertNull(UsageParser.parse("{}", f))
        assertNull(UsageParser.parse("""{"limits":[{"kind":"session","percent":101,"resets_at":"2026-11-25T22:40:00Z"},{"kind":"weekly_all","percent":1,"resets_at":"2026-11-25T22:40:00Z"}]}""", f))
        assertNull(UsageParser.parse("""{"limits":[{"kind":"session","percent":-1,"resets_at":"2026-11-25T22:40:00Z"},{"kind":"weekly_all","percent":1,"resets_at":"2026-11-25T22:40:00Z"}]}""", f))
        assertNull(UsageParser.parse("""{"limits":[{"kind":"session","percent":"38","resets_at":"2026-11-25T22:40:00Z"},{"kind":"weekly_all","percent":1,"resets_at":"2026-11-25T22:40:00Z"}]}""", f))
        assertNull(UsageParser.parse("""{"limits":[{"kind":"session","percent":3,"resets_at":"tomorrow"},{"kind":"weekly_all","percent":1,"resets_at":"2026-11-25T22:40:00Z"}]}""", f))
        assertNull(UsageParser.parse("""{"limits":[{"kind":"weekly_all","percent":1,"resets_at":"2026-11-25T22:40:00Z"}]}""", f))
        assertNull(UsageParser.parse("""{"five_hour":{"utilization":150},"seven_day":{"utilization":1}}""", f))
    }

    @Test fun configurableFieldNames() {
        val g = Fields(limitsArray = "windows", kind = "type", sessionKinds = listOf("5h"), weeklyKinds = listOf("7d"))
        assertNotNull(UsageParser.parse("""{"windows":[{"type":"5h","percent":1,"resets_at":"2026-11-25T22:40:00Z"},{"type":"7d","percent":2,"resets_at":"2026-11-25T22:40:00Z"}]}""", g))
    }
}

class StateMachineTest {
    private val cfg = AppConfig()
    private val now = 1_800_000_000_000L
    private val reading = Reading(38, now + 2 * 3_600_000, 61, now + 3 * 86_400_000L)
    private val fresh = Status(everSignedIn = true, reading = reading, lastSuccessAt = now - 60_000, lastFetchAt = now - 60_000)

    @Test fun notSetUp() = assertEquals(WidgetState.NotSetUp, StateMachine.derive(Status(), cfg, now, false))
    @Test fun signedOutWhenTokensGone() = assertEquals(WidgetState.SignedOut, StateMachine.derive(fresh, cfg, now, false))
    @Test fun signedOutOnError() =
        assertEquals(WidgetState.SignedOut, StateMachine.derive(fresh.copy(lastError = LastError.SIGNED_OUT), cfg, now, true))

    @Test fun normal() {
        val s = StateMachine.derive(fresh, cfg, now, true) as WidgetState.Data
        assertEquals(Mode.Normal, s.mode)
        assertFalse(s.refreshing)
    }

    @Test fun refreshingDotExpires() {
        assertTrue((StateMachine.derive(fresh.copy(refreshingSince = now - 1000), cfg, now, true) as WidgetState.Data).refreshing)
        assertFalse((StateMachine.derive(fresh.copy(refreshingSince = now - 120_000), cfg, now, true) as WidgetState.Data).refreshing)
    }

    @Test fun staleByAgeAndOffline() {
        val old = StateMachine.derive(fresh.copy(lastSuccessAt = now - 21 * 60_000), cfg, now, true) as WidgetState.Data
        assertTrue(old.mode is Mode.Stale)
        val off = StateMachine.derive(fresh.copy(lastError = LastError.OFFLINE), cfg, now, true) as WidgetState.Data
        assertTrue(off.mode is Mode.Stale)
    }

    @Test fun pausedWhileRateLimited() {
        val s = StateMachine.derive(fresh.copy(lastError = LastError.RATE_LIMITED, nextAllowedAt = now + 600_000), cfg, now, true)
        assertEquals(Mode.Paused(now + 600_000), (s as WidgetState.Data).mode)
    }

    @Test fun limitPicksBindingWindow() {
        val s = StateMachine.derive(fresh.copy(reading = reading.copy(sessionPct = 100)), cfg, now, true)
        assertEquals(Window.SESSION, (s as WidgetState.Limit).window)
        val both = StateMachine.derive(fresh.copy(reading = reading.copy(sessionPct = 100, weekPct = 100)), cfg, now, true)
        assertEquals(Window.WEEK, (both as WidgetState.Limit).window)
    }

    @Test fun limitEndsWhenResetPasses() {
        val s = fresh.copy(reading = reading.copy(sessionPct = 100, sessionResetsAt = now - 1000))
        val d = StateMachine.derive(s, cfg, now, true) as WidgetState.Data
        assertEquals(0, d.reading!!.sessionPct)
    }

    @Test fun endpointChangedAfterTwoInvalid() {
        val one = StateMachine.apply(fresh, FetchResult.Invalid(200), now, cfg.polling)
        assertTrue(StateMachine.derive(one, cfg, now, true) is WidgetState.Data)
        val two = StateMachine.apply(one, FetchResult.Invalid(200), now, cfg.polling)
        assertEquals(WidgetState.EndpointChanged, StateMachine.derive(two, cfg, now, true))
        val ok = StateMachine.apply(two, FetchResult.Ok(reading), now, cfg.polling)
        assertTrue(StateMachine.derive(ok, cfg, now, true) is WidgetState.Data)
    }

    @Test fun killSwitchWins() {
        assertTrue(StateMachine.derive(fresh, cfg.copy(disabled = true, message = "X"), now, true) is WidgetState.KillSwitch)
        assertTrue(StateMachine.derive(Status(), cfg.copy(updateRequired = true), now, false) is WidgetState.KillSwitch)
    }

    @Test fun scopeMissingSignsOutAndFlags() {
        val s = StateMachine.apply(fresh, FetchResult.ScopeMissing, now, cfg.polling)
        assertTrue(s.scopeUpgradeNeeded)
        assertEquals(WidgetState.SignedOut, StateMachine.derive(s, cfg, now, true))
    }

    @Test fun backoffDoublesCapsAndHonoursRetryAfter() {
        val p = Polling()
        assertEquals(5 * 60_000L, StateMachine.backoffMs(0, p, null))
        assertEquals(10 * 60_000L, StateMachine.backoffMs(1, p, null))
        assertEquals(240 * 60_000L, StateMachine.backoffMs(10, p, null))
        assertEquals(240 * 60_000L, StateMachine.backoffMs(40, p, null))
        assertEquals(900_000L, StateMachine.backoffMs(0, p, 900))
        val rl = StateMachine.apply(fresh, FetchResult.RateLimited(null), now, p)
        assertEquals(now + 5 * 60_000L, rl.nextAllowedAt)
        val rl2 = StateMachine.apply(rl, FetchResult.RateLimited(null), now, p)
        assertEquals(now + 10 * 60_000L, rl2.nextAllowedAt)
        assertEquals(0, StateMachine.apply(rl2, FetchResult.Ok(reading), now, p).backoffLevel)
    }

    @Test fun adaptiveInterval() {
        val p = Polling()
        assertEquals(15, StateMachine.intervalMin(fresh.copy(sessionChangedAt = now - 30 * 60_000), now, p))
        assertEquals(60, StateMachine.intervalMin(fresh.copy(sessionChangedAt = now - 91 * 60_000), now, p))
        val changed = StateMachine.apply(fresh.copy(sessionChangedAt = now - 200 * 60_000), FetchResult.Ok(reading.copy(sessionPct = 40)), now, p)
        assertEquals(15, StateMachine.intervalMin(changed, now, p))
        val same = StateMachine.apply(fresh.copy(sessionChangedAt = now - 200 * 60_000), FetchResult.Ok(reading), now, p)
        assertEquals(60, StateMachine.intervalMin(same, now, p))
    }

    @Test fun resetRefreshIsEarliestFutureResetPlusOneMinute() {
        assertEquals(reading.sessionResetsAt!! + 60_000, StateMachine.nextResetRefreshAt(reading, now))
        assertNull(StateMachine.nextResetRefreshAt(Reading(0, null, 0, null), now))
    }
}

class FormatTest {
    private val zone = ZoneId.of("Europe/Warsaw")
    private val now = ZonedDateTime.of(2026, 11, 25, 21, 0, 0, 0, zone).toInstant().toEpochMilli()

    @Test fun countdownRoundsDownToFive() {
        assertEquals("2H 10M", Format.countdown((2 * 60 + 14) * 60_000L))
        assertEquals("40M", Format.countdown(41 * 60_000L))
        assertEquals("3M", Format.countdown(3 * 60_000L + 59_000))
        assertEquals("2H", Format.countdown(122 * 60_000L))
        assertEquals("0M", Format.countdown(-5))
    }

    @Test fun weekResetTwoForms() {
        val wed = ZonedDateTime.of(2026, 12, 2, 14, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals("WED 14:00", Format.weekReset(wed, now, zone, true))
        assertEquals("WED 2:00PM", Format.weekReset(wed, now, zone, false))
        assertEquals("IN 7H 25M", Format.weekReset(now + (7 * 60 + 27) * 60_000L, now, zone, true))
        assertEquals("—", Format.weekReset(null, now, zone, true))
    }

    @Test fun captionsAndAge() {
        assertEquals("IN 2H 10M", Format.sessionValue(now + (2 * 60 + 14) * 60_000L, now))
        assertEquals("—", Format.sessionValue(null, now))
        assertEquals("IN 1H 40M", Format.limitValue(now + 102 * 60_000L, now, zone))
        val wed = ZonedDateTime.of(2026, 12, 2, 14, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals("WED", Format.limitValue(wed, now, zone))
        assertEquals("—", Format.limitValue(null, now, zone))
        assertEquals("2H AGO", Format.age(2 * 3_600_000L + 5))
        assertEquals("41M AGO", Format.age(41 * 60_000L))
        assertEquals("3D AGO", Format.age(3 * 86_400_000L))
    }

    @Test fun accessibilityText() {
        val r = Reading(38, now + (2 * 60 + 14) * 60_000L, 61, null)
        assertEquals(
            "Session 38 percent, resets in 2 hours 14 minutes; week 61 percent",
            Format.description(WidgetState.Data(r, Mode.Normal, false), now),
        )
    }
}

class DotFontTest {
    @Test fun burstCounts() {
        mapOf(0 to 0, 1 to 1, 38 to 23, 80 to 48, 99 to 59, 100 to 60).forEach { (pct, n) ->
            assertEquals("pct $pct", n, DotFont.litCount(pct))
        }
        assertEquals(60, DotFont.BURST.size)
    }

    @Test fun burstFillsFromTwelveOClockInnerToOuterThenClockwise() {
        val first = DotFont.BURST[0]
        assertEquals(50f, first.x, 0.01f)
        assertTrue(first.y < 50f)
        assertTrue(DotFont.BURST[4].y < first.y) // outer dot of ray 0 is higher
        assertTrue(DotFont.BURST[5].x > 50f) // ray 1 is to the right (clockwise)
        DotFont.BURST.forEach { assertTrue(it.x - it.r >= 0f && it.x + it.r <= 100f && it.y - it.r >= 0f && it.y + it.r <= 100f) }
    }

    @Test fun glyphTableWellFormed() {
        val needed = "0123456789:ACDEGHIKLMNOPRSTUWY"
        needed.forEach { assertTrue("missing $it", DotFont.GLYPHS.containsKey(it)) }
        DotFont.GLYPHS.forEach { (ch, rows) ->
            assertEquals("rows of $ch", DotFont.ROWS, rows.size)
            assertTrue("width of $ch", rows.all { it.length == rows[0].length && it.all { c -> c == '0' || c == '1' } })
        }
        assertEquals(5 + 1 + 5, DotFont.widthCells("38"))
        // Every state word renders with real glyphs (no silent blanks).
        listOf("LIMIT", "SIGN", "IN", "UPDATE", "SET", "UP", "PAUSED", "10:40PM").forEach { w ->
            w.forEach { assertTrue("$w/$it", DotFont.GLYPHS.containsKey(it)) }
        }
    }
}
