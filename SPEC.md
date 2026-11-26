# Claude Usage Widget — SPEC v1.0

Owner: Aliaksei · Spec chat: "Claude Usage Widget · v0.1 · spec", 25 Nov 2026 · Status: **approved for build**
Target: CMF Phone 2 Pro (Android, Nothing OS), home-screen widget, sideloaded own signed APK.
Source of the brief: backlog entry "Claude usage widget (own build)" (25 Nov 2026).

---

## 0. How to use this spec

- This file is the single source of truth. Every build chat starts from this file plus the hand-off note (§17).
- Decisions are final unless §2 is edited. Changes go into the decision log first, then the code.
- Claude runs the technical side end to end (security, refactor, review, static analysis) and keeps Aliaksei's reading to a minimum.

## 1. Goal and scope

**Goal:** a 2×2 home-screen widget that shows Claude plan usage at a glance: the 5-hour session % and the weekly %, with reset times. It should look like a native classic Nothing OS widget with Claude's clay as the identity colour.

**In scope (v1)**
- One 2×2 widget (layout Y: burst shape + session number, week row below)
- Sign in with Claude on the phone (own OAuth session, PKCE)
- Tap to refresh, adaptive background polling, refresh after reset time
- Remote config (endpoint, field names, polling numbers) from the public repo
- Designed error states, each with a fix on tap
- GitHub Actions build, signed release APK on GitHub Releases
- Weekly CI watch for endpoint changes

**Out of scope (v1):** other widget sizes, notifications (no notification permission), model-specific weekly rows, lock-screen widget, Play Store, any server of our own.

## 2. Decision log (newest first)

Source chat for D1–D15: "Claude Usage Widget · v0.1 · spec" (25 Nov 2026). From M1 on, every new decision names its source chat.

| # | Date | Decision |
|---|---|---|
| D25 | 26 Nov | Layout round 2, option 3, picked by Aliaksei from mockups pasted onto his home screen. Text matches the system widgets there ("Modes", "Clear", "Chats": ~20 px caps, 2–3 px stems on a 342 px tile): mixed case, cap height 0.064·L, medium weight, normal spacing. Equal margins 0.10·L. Burst 0.28·L, centred against the session column. Heavier dots (0.80 of the cell). Times: `in 2h 10m`, `in 40 min`, `Wed 14:00`. Supersedes the type sizes and caps of D22. Source: M1 build (resume), 26 Nov. |
| D24 | 26 Nov | Scope test passed: sign-in with `user:profile` only reads usage (`LAST RESULT: OK`, 67% / 56%). D4's fallback (`user:inference`) is not needed. Source: M1 build (resume), 26 Nov. |
| D23 | 26 Nov | The build sandbox can't push tags (git proxy 403, policy). release.yml also runs from a manual trigger (`workflow_dispatch`, main only, input `version`) and creates the tag itself. Approved by Aliaksei. v0.1.0 released this way; signing cert SHA-256 matches D17. Source: M1 build (resume), 26 Nov. |
| D22 | 26 Nov | The chosen layout (BACKLOG "M2 polish": option 1 three masses, no divider, two-line labels, spaced countdowns) goes into M1 before the first release, not M2. It's one renderer file, and it saves a full release round. Labels: medium weight, tracking ≈ +6% (typography rule 5, replacing the 0.12 em in §3.1); system font kept for host consistency. Source: M1 build chat, 26 Nov. |
| D21 | 25 Nov | Fit rule: at the §3.2 sizes a two-digit session number collides with the burst (0.45·L burst + 0.20·L digits > inner width). The session digits now shrink to fit the column right of the burst (≈0.14·L for two digits); captions shrink to that column too. Revisit proportions on the device in M2. Source: M1 build, preview render. |
| D20 | 25 Nov | M1 paused mid-build to protect the weekly budget (Aliaksei at 20% weekly). Resume in a fresh chat from HANDOFF.md after the weekly reset. Source: "Claude Usage Widget · v0.1 · M1 build". |
| D19 | 25 Nov | Countdowns: rounded down to 5 min, but under 5 min show exact minutes. A non-wakeup inexact 5-min alarm re-renders (no network), so countdowns stay within ~5 min while the phone is awake. Source: M1 build. |
| D18 | 25 Nov | Setup screen gets a launcher icon ("Usage"), so status and sign-out are reachable. It's an exported MAIN/LAUNCHER activity; its one extra (`check_fix`) is gated by the tap cooldown, so other apps can't use it to burn the rate limit. All other components stay non-exported. Source: M1 build. |
| D17 | 25 Nov | Signing: this build sandbox can't write Actions secrets (proxy blocks the path), so Aliaksei added the four secrets by hand. Backup .7z kept separately; passphrase in LastPass. Key fingerprint (SHA-256) `5F:9A:95:F4:…:0D:23`. Source: M1 build. |
| D16 | 25 Nov | Toolchain pinned to a known-good set: Gradle 8.14.3, AGP 8.11.1, Kotlin 2.2.0, compile/target SDK 36. The sandbox can't reach Google Maven to confirm the newest AGP (Gradle 9.9.2 and Kotlin 2.4.21 exist); Dependabot raises bumps and the M3 dependency audit moves to latest. Source: M1 build. |
| D15 | 25 Nov | Week reset on the main tile as a two-line label: `WEEK` (grey) above the reset day and time in the phone's own clock format, for example `WED 14:00` (white). Within 24 h it switches to `IN 7H25M`. The week number on the right is centred on both lines. No flip-through pages (supersedes D14). |
| D14 | 25 Nov | (superseded by D15) Week reset as a one-line label `WEEK · THU 09`. |
| D13 | 25 Nov | Layout locked: balanced Y layout. Burst shape left, session number right, hairline divider, week row. Final polish happens on the real phone. |
| D12 | 25 Nov | Fullness shape: **S4 burst**, 12 rays × 5 dots = 60 dots in clay, filling clockwise from 12 o'clock. Generic radial, **not** Claude's logo. Dot size grows slightly outward. |
| D11 | 25 Nov | Percent sign: **D4 weighted**, a 4×4 dot "X" whose two big corner dots read as the % circles. Raised, aligned to the cap height of the digits. |
| D10 | 25 Nov | Colours: clay #D97757 = identity, Nothing red = warning only. |
| D9 | 25 Nov | Warning: digits and burst dots turn red at ≥80%; full red tile at 100%. Widget only, no notification. |
| D8 | 25 Nov | Size: 2×2 only. |
| D7 | 25 Nov | Repo: **public** on Aliaksei's GitHub (config and APK downloads without login; no secrets in code). |
| D6 | 25 Nov | Ndot isn't used (proprietary). Own dot glyphs drawn as circles; labels in the system sans. |
| D5 | 25 Nov | Refresh: tap (2-min cooldown) + adaptive 15/60-min background + one-off after reset. "After every reply" can't be done (no signal reaches the phone, and the endpoint rate limit is shared with Claude Code). |
| D4 | 25 Nov | Ask for scope `user:profile` only. Fall back to `user:profile user:inference` only if the usage call is refused, and record the result here. |
| D3 | 25 Nov | **Rewrite**, not fork. Port PKCE sign-in, token refresh and response parsing (~150 lines) from Claude Pulse (MIT, credited in NOTICE). Everything else is new. |
| D2 | 25 Nov | Build with GitHub Actions; no Android Studio on the laptop. |
| D1 | 25 Nov | Own build instead of installing a stranger's APK. |

## 3. Design spec

### 3.1 Style reference (taken from Aliaksei's home screen; Nothing has no public developer guide)
- Tile surface #1B1B1D. Rounded square with the **system widget radius** (`@android:dimen/system_app_widget_background_radius`), so it matches the other tiles.
- Big numbers: dot matrix, white #F2F2F2.
- Labels: system sans, uppercase, letter-spacing 0.12em, about 11sp, #BDBDBD.
- Unlit dots #3A3A3C. Divider 1 dp #333333.
- Clay #D97757 (identity). Warning red: digits #E0262F, full tile #D71921, white content on the red tile.
- Colour is a signal, not decoration: one accent at a time.

### 3.2 Layout Y (2×2), proportional to tile side L
- Even padding on all four sides: 0.094·L.
- **Top block** (height ≈ 0.48·L): burst shape on the left (box ≈ 0.45·L wide × 0.41·L high). On the right, a right-aligned column: the session number with the D4 % (digit height ≈ 0.20·L), then 7 dp below it the caption `5H · 2H14M`. The burst's vertical centre and the right column's vertical centre sit on one line.
- **Divider:** full inner width, 10 dp above the bottom row.
- **Bottom row (D15):** on the left, a two-line label: `WEEK` in grey #BDBDBD, then 3 dp below it the reset in white #F2F2F2. On the right, the week number with a smaller D4 % (digit height ≈ 0.13·L), vertically centred on the two-line label.
- Caption text: session = countdown to reset, rounded down to 5 min (`2H14M`, `41M`). Week = day + time in the phone's 12/24 h setting when the reset is more than 24 h away (`WED 14:00`), otherwise `IN 7H25M`.
- Visual QA rule: the left and right margins and the top and bottom margins must measure equal on device screenshots (checked in M2).

### 3.3 Dot glyphs
- Own glyph table, 5×7 grid, drawn as filled circles (dot diameter = cell × 0.72): digits 0–9, `:` and the letters used in the state words (A C D E G H I K L M N O P R S T U W Y).
- D4 percent, 4×4: `2001 / 0010 / 0100 / 1002` (2 = big dot at 1.4× diameter). Raised: top edge = digit top.
- The burst: 12 rays, 5 dots per ray, ray radius step ≈ 0.085·shape box. Dot radius grows from 2.2 to 3.2 units outward. Fill order: ray 0 (12 o'clock) inner→outer, then clockwise. Lit dots = round(pct/100 × 60).

### 3.4 States (all styled, every one with a tap action)

| State | Trigger | Shows | Tap does |
|---|---|---|---|
| Normal | fresh data (<20 min old) | burst + numbers in white and clay | refresh (cooldown 2 min) |
| Warning | session or week ≥80% | that number and its burst dots in red | refresh |
| Limit | session or week = 100% | full red tile: `LIMIT` + reset time `22:40` + label `SESSION RESETS` (or `WEEK RESETS THU`) | refresh |
| Refreshing | request in flight | last values + small clay dot top-right | — |
| Stale/offline | no network, or data >20 min old | last values greyed #7A7A7A + `OFFLINE · 2H AGO` | retry |
| Rate-limited | 429 with a valid token | last values + `PAUSED · NEXT 20:45` | shows the next-try time; no extra call |
| Signed out | refresh token refused (400/401), or the expired-token 429 trap (§5.4) | `SIGN` / `IN` + `SESSION EXPIRED · TAP` | opens sign-in |
| Endpoint changed | response fails validation (§6.3) twice in a row | `UPDATE` + `USAGE SOURCE CHANGED` / `TAP TO CHECK FIX` | fetches remote config now; if still broken, opens the repo's Releases page |
| Not set up | first run | `SET` / `UP` + `TAP TO SIGN IN` | opens setup |
| Kill switch | config `disabled=true` | `PAUSED` + config message (≤24 chars) | opens the Releases page |

Copy rules: uppercase on the widget, no exclamation marks, no "please", no "error:" prefixes.

### 3.5 Setup screen (single activity, dark, same palette)
- "Sign in with Claude" button → browser → paste code → done.
- A status line (signed in as plan type, last refresh age), "Refresh now", "Open usage page" (claude.ai/settings/usage), "Sign out" (wipes tokens).
- About: version, licence, Claude Pulse credit, link to the repo.

## 4. Platform and architecture

- Kotlin, single `app` module. Application ID `io.github.akhamiak.claudeusage`, name "Usage" (label under the widget: "Claude usage").
- minSdk 26, targetSdk/compileSdk = latest stable at build time (36 expected). AGP, Kotlin and Gradle at the latest stable versions; versions pinned in `libs.versions.toml`.
- Dependencies: `androidx.core`, `androidx.work` only. Test-only: JUnit, `okhttp3:mockwebserver`, Robolectric (if needed for the renderer). No analytics, no crash SDKs, no networking library (`HttpsURLConnection`).
- **Rendering:** the widget is one `ImageView` filled by a `Bitmap` that we draw on a Canvas at the widget's real size (from `AppWidgetManager` options, re-rendered on resize). This gives exact dot glyphs without font licensing. Set `contentDescription` to the full reading ("Session 38 percent, resets in 2 hours 14 minutes; week 61 percent") for accessibility. Tap target = whole tile.
- Files:
  - `Config.kt`: built-in defaults + remote config fetch, validation, cache
  - `TokenStore.kt`: Keystore-backed encrypted storage
  - `Auth.kt`: PKCE sign-in, token refresh (single flight)
  - `UsageClient.kt`: HTTP call, error classification
  - `UsageParser.kt`: both response shapes → `Reading`
  - `WidgetState.kt`: pure state machine (`Reading` + error + time → state)
  - `Scheduler.kt`: WorkManager periodic, one-off reset work, backoff
  - `DotFont.kt`, `WidgetRenderer.kt`: glyph tables, Canvas drawing
  - `UsageWidget.kt`: `AppWidgetProvider`
  - `RefreshReceiver.kt`: tap target (not exported)
  - `SetupActivity.kt`
- **Manifest:** the app declares only `INTERNET`. WorkManager merges normal-level permissions (`ACCESS_NETWORK_STATE`, `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`), with no user prompts. `FOREGROUND_SERVICE` is removed with `tools:node="remove"` (unused). The final merged list is checked in QA (§11).
- `android:allowBackup="false"`, `dataExtractionRules` and `fullBackupContent` exclude everything. Cleartext traffic stays off (the default).
- The widget provider is exported (the system needs that) with only the `APPWIDGET_UPDATE` action. No custom actions in any intent filter. Taps use explicit, immutable `PendingIntent`s to the non-exported `RefreshReceiver`.

## 5. Auth

### 5.1 Flow (ported from Claude Pulse)
- PKCE S256, `state` required (reject if missing or mismatched; Pulse let a missing state through).
- Authorize: `https://claude.com/cai/oauth/authorize` with `code=true`, Claude Code's public client ID, redirect `https://platform.claude.com/oauth/code/callback` (a manual paste-the-code flow).
- Token: `POST https://platform.claude.com/v1/oauth/token` (JSON body, `authorization_code` / `refresh_token` grants). The refresh token rotates and the new one is saved atomically.
- All URLs and the client ID come from config (§6.2), with built-in defaults.

### 5.2 Scope
- First try `user:profile`. If the usage call returns 403 with that token, sign in again with `user:profile user:inference` and log the result in D4.

### 5.3 Storage
- An AES-256-GCM key in Android Keystore (non-exportable). Tokens are encrypted with it and stored in private SharedPreferences. No `security-crypto` (it's deprecated).
- Never logged and never shown in full. Only the last 4 characters appear in the setup screen.

### 5.4 Classification (with the expired-token trap)
- 401/403 → refresh once → if that fails → Signed out.
- 429 → if the access token's expiry has passed **or** Retry-After is over 600 s → refresh the token once and retry once. If the retry is still 429 → Rate-limited. If the refresh fails → Signed out.
- 400/401 from the token endpoint → Signed out. 429 from the token endpoint → Rate-limited.
- Network or timeout → Stale/offline.

## 6. Data, endpoint health, remote config

### 6.1 Usage call
`GET {usage_url}{query}` with `Authorization: Bearer`, `anthropic-beta: {beta}`, `Accept: application/json`. Timeouts 10 s. Defaults: `https://api.anthropic.com/api/oauth/usage`, beta `oauth-2025-04-20`, query empty. (Claude Code 2.1.326 adds `?cedar_ember=1&skip_spend=1`; that's available through config if needed.)

### 6.2 Remote config: `config/endpoint.json` in the public repo, fetched from raw.githubusercontent.com
```json
{
  "schema": 1,
  "min_app_version": 1,
  "disabled": false,
  "message": "",
  "usage_url": "https://api.anthropic.com/api/oauth/usage",
  "usage_query": "",
  "beta_header": "oauth-2025-04-20",
  "authorize_url": "https://claude.com/cai/oauth/authorize",
  "token_url": "https://platform.claude.com/v1/oauth/token",
  "redirect_url": "https://platform.claude.com/oauth/code/callback",
  "client_id": "9d1c250a-e61b-44d9-88ed-5944d1962f5e",
  "scopes": "user:profile",
  "fields": {
    "limits_array": "limits",
    "kind": "kind", "percent": "percent", "resets_at": "resets_at",
    "session_kinds": ["session"], "weekly_kinds": ["weekly_all"],
    "legacy_session": "five_hour", "legacy_weekly": "seven_day",
    "legacy_percent": "utilization"
  },
  "polling": {
    "tap_cooldown_s": 120,
    "active_interval_min": 15,
    "idle_interval_min": 60,
    "idle_after_min": 90,
    "backoff_start_min": 5,
    "backoff_max_min": 240,
    "stale_after_min": 20,
    "config_ttl_h": 24
  }
}
```
- **Host allowlist (hard-coded, not configurable):** every URL in the config must be `https` and on `api.anthropic.com`, `platform.claude.com`, `claude.com` or `claude.ai`. Otherwise the whole config is rejected and the cached or built-in config is used. This stops a compromised repo from sending the token anywhere else.
- Numeric bounds are enforced (for example, the tap cooldown can't go below 60 s and the active interval below 15 min).
- Fetched at most once per `config_ttl_h`, plus immediately from the "UPDATE" state.

### 6.3 Response validation (health check on every response)
Valid = HTTP 200, JSON, and either (a) a `limits` array containing a session kind and a weekly kind, each with a numeric percent in [0, 100] and an ISO-8601 `resets_at` (null is allowed at 0%), or (b) legacy objects with `utilization` in [0, 100]. Anything else counts as a validation failure. Two in a row → Endpoint changed.

### 6.4 CI watch (`.github/workflows/watch.yml`, weekly, Monday 07:41 UTC)
- Fetches public sources only: Claude Pulse's `ApiClient.kt` and `TokenManager.kt`, and the Claude Code CHANGELOG. Diffs the endpoint, beta header, token URL and client ID against `config/endpoint.json`. Opens a GitHub issue on any difference.
- No token or secret is used. It never calls Anthropic's endpoint itself.

## 7. Refresh strategy

- **Tap:** immediate fetch unless the last fetch was <`tap_cooldown_s` ago. Within the cooldown it only re-renders (the countdowns update) and shows the refreshing dot briefly.
- **Periodic:** WorkManager unique periodic work, `active_interval_min` (15), network required.
- **Adaptive:** if session % hasn't changed for `idle_after_min` (90) → switch to `idle_interval_min` (60). Any change, or a tap, → back to 15.
- **Reset:** a one-off work at `resets_at + 1 min`, so the widget drops to 0% right after a reset.
- **Backoff:** on 429 or 5xx: exponential from 5 to 240 min, honouring Retry-After. Last good values stay visible with their age.
- Re-render without network on every worker run and every tap (countdowns, staleness).
- **Not possible (documented):** refresh on each Claude reply, or on phone unlock (a manifest receiver can't get `USER_PRESENT` on Android 8+).

## 8. Threat model

| Asset / risk | Adversary or cause | Mitigation |
|---|---|---|
| OAuth tokens (account access) | other apps on the phone | private storage, Keystore encryption, no exported components except the widget provider, no custom intent actions |
| Tokens leaving the phone | Google/adb backup | backup disabled, extraction rules exclude all |
| Token gives inference access | stolen token | profile-only scope (D4) |
| Token sent to a foreign host | compromised repo or config | hard-coded host allowlist, https only, schema validation |
| Rate-limit exhaustion (Claude Code's `/usage` breaks) | spam taps, other apps, bugs | tap cooldown, non-exported receiver, backoff, bounded config values |
| Malicious APK update | CI supply chain | actions pinned to SHAs, Gradle wrapper validation, minimal workflow permissions, release only on tag, Dependabot, APK SHA-256 in the release notes |
| Signing key leak | repo or CI | key only in encrypted Actions secrets + Aliaksei's encrypted backup; never committed or printed |
| Endpoint or client ID blocked by Anthropic | policy change (unofficial endpoint) | Endpoint changed state, kill switch, weekly watch; fallback: Chrome shortcut to claude.ai/settings/usage |
| MITM | network | TLS by the platform; no cleartext; no user CAs trusted (default for targetSdk ≥24) |
| Lost phone | theft | Sign out from claude.ai revokes the session; tokens are Keystore-bound |

Residual risk, accepted: this reuses Claude Code's public client ID and an undocumented endpoint. It may break, or be disallowed, at any time.

## 9. CI/CD

- Repo: `github.com/a-khamiak/claude-usage-widget`, public, MIT. Pulse credit in NOTICE.
- `build.yml` (push/PR): wrapper validation → lint → unit tests → assembleDebug.
- `release.yml` (tag `v*`): signed assembleRelease (R8 on) → `apksigner verify` → SHA-256 → GitHub Release with the APK.
- `watch.yml`: see §6.4.
- All actions pinned to commit SHAs; `permissions: contents: read` by default (`contents: write` only for release; `issues: write` only for watch).
- **Signing key:** generated by Claude in the build chat (RSA 4096, 30-year validity) → secrets `KEYSTORE_B64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. An AES-encrypted backup goes to Aliaksei and the passphrase to his password manager. Losing it only means uninstalling and reinstalling (a new sign-in).
- **Install:** open the Release in Brave on the phone → download the APK → allow "Install unknown apps" for Brave once → install. Updates are installed the same way over the top.

## 10. Testing

- **Unit:** parser (both shapes, nulls, garbage, out-of-range numbers), state machine (every row of §3.4), the expired-token 429 rule, backoff maths, adaptive interval, config validation + host allowlist, countdown formatting.
- **Integration:** MockWebServer scripts for 200 / 401 / 403 / 429+Retry-After / 500 / timeout / malformed JSON / token rotation.
- **Renderer:** golden-image snapshots of all states at 2 sizes; dot-count checks for the burst (0, 1, 38, 80, 99, 100%).
- **Robustness:** reboot (work re-scheduled), no network, clock change, widget resize, two widgets placed at once.

## 11. QA and security checklist (finishing stages, Claude runs them)

1. Static analysis: Android lint (warnings as errors for security checks) + detekt.
2. Manifest audit of the release APK (`apkanalyzer`/`aapt2`): permission list, exported components, `allowBackup=false`, no debuggable flag.
3. Secrets: grep the code and the APK strings for tokens and keystore material; check that nothing in `Log.*` touches token or response bodies.
4. Dependency audit: versions current, no known CVEs (Dependabot alerts clear).
5. CI hardening review: pinned SHAs, permissions, triggers (no `pull_request_target`).
6. Threat-model pass: each row of §8 verified with evidence.
7. Refactor pass: dead code, naming, file size, duplication.
8. Independent code review by a sub-agent with no build context, given only this spec + the repo. Findings fixed or justified.
9. Performance: widget render <50 ms, process idle afterwards, no wakelocks held, battery check after 24 h.
10. On-device test script for Aliaksei (≤10 steps, ≤5 min): install, sign in, place the widget, tap, airplane mode, sign out, sign back in, resize, reboot.

## 12. Definition of done (Aliaksei's words, 25 Nov)

The project is done when the design is finalized, everything is tested, he gives good feedback that it needs no further refinement, and all improvements from the checks are implemented and gaps closed.

Measured as: §10 tests green, §11 checklist complete with evidence in `docs/qa.md`, on-device script passed, his "no further refinement" sign-off, tag `v1.0.0`.

## 13. Milestones (one per chat)

- **M1 · Build** (next chat): repo + project docs (§14) + CI + key, auth, client, parser, state machine, renderer, widget, setup screen, unit tests → first signed APK on the phone.
- **M2 · Polish:** his on-device feedback in one batch (spacing, sizes, copy) → fixes → APK.
- **M3 · Harden and release:** §11 items 1–9 → fixes → `v1.0.0` → update the backlog entry → done.

## 14. Process rules (token economy, his Pro plan)

- One milestone per chat. Start each chat from this spec + the hand-off note; nothing else.
- A short skeleton plan (≤10 lines) before each step; minimal iterations.
- Read only the code being changed. Targeted tests while working, the full suite once per milestone.
- Research once, record the findings in this spec (decision log), never re-research.
- Refactor, independent review and security checks at milestones (M3), not per change.
- Short replies. Ideas that come up mid-build go into `BACKLOG.md` in the repo; nothing gets built until he says so.
- At the end of each milestone: a usage check-in (claude.ai/settings/usage in the built-in browser, tab closed after reading) → a row in §15.
- Personal-rule reminder when drifting into tooling for its own sake: one line.
- **Project docs (the repo is the truth):** `SPEC.md` (current truth + decision log with source-chat links), `CHANGELOG.md` (one entry per version, with chat link), `BACKLOG.md` (ideas not built), `HANDOFF.md` (where we stopped, exact next step, next chat name). At the end of every chat: update `HANDOFF.md` + `CHANGELOG.md`, then the build-index registry in memory, and check that they match.
- Chat names: `Claude Usage Widget · <version> · <purpose>`; every hand-off starts with that name.

## 15. Build-cost log

| Date | Chat / milestone | Session % after | Weekly % after | Notes |
|---|---|---|---|---|
| 25 Nov 2026 | Spec session | 9% (resets Thu 01:10) | 18% (resets Wed 14:00) | Pulse audit, endpoint and CI research, 4 mockup rounds, spec |
| 25 Nov 2026 | M1 build (part 1, paused) | n/a | 20% (reported by Aliaksei; no browser check, to save tokens) | Key + secrets, all app code and unit tests written; not yet compiled; CI workflows not yet written |
| 26 Nov 2026 | M1 build (resume), M1 closed | 78% (resets 21:20) | 58% (resets Wed 14:00); usage page warns he'll run out Thu evening, before the reset | 95 requests. Split: instructions 55.5%, files and commands 26.6%, learning doc 8.8%, conversation 6.1%, web 1.3%, repo access 0.9%, memory 0.7%. CI files, v0.1.0, scope test, learning doc, 2 mockup rounds, v0.2.0. Lesson: one long chat re-reads its context every turn; M2 design should have started in a fresh chat |

## 16. Known limitations

- No true "after each reply" refresh (no signal reaches the phone). The best case is 15-min auto plus taps.
- The endpoint's rate limit is shared with Claude Code's own `/usage` and statusline; heavy use of those may cause "PAUSED".
- Unofficial endpoint and client ID: may break without notice. The update path is §6, the fallback the usage-page shortcut.
- Countdown precision is 5 min between renders.

## 17. Hand-off note for the build chat (M1)

> **Claude Usage Widget · v0.1 · M1 build.** Attached: SPEC.md (approved 25 Nov 2026). Build milestone M1 only (§13), following §14.
> GitHub is linked (verified 25 Nov 2026): account `a-khamiak`, empty public repo `a-khamiak/claude-usage-widget` with push access. Attach it with push access and start.
> Order: scaffold + project docs (SPEC.md, CHANGELOG.md, BACKLOG.md, HANDOFF.md, NOTICE) + CI (§9) → key + secrets → `Config`/`TokenStore`/`Auth` (port from Claude Pulse `TokenManager.kt` with the §5 fixes) → `UsageClient`/`UsageParser`/`WidgetState` + unit tests → `DotFont`/`WidgetRenderer` (design §3, locked) → widget + setup → tag `v0.1.0` → give me the Release link and the install steps.
> Then the scope test (D4): I sign in, and you log the result in the decision log.
> Close with: a usage check-in row in §15, HANDOFF.md naming "Claude Usage Widget · v0.1 · M2 polish" as the next chat, a CHANGELOG entry, and the build-index registry updated. Don't start M2.
