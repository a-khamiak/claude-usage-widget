# CODEMAP (one line per file)

Code: `app/src/main/java/io/github/akhamiak/claudeusage/`

| File | Purpose |
|---|---|
| `Config.kt` | Built-in defaults, remote `config/endpoint.json` fetch, validation, host allowlist, cache (§6.2) |
| `Http.kt` | Minimal `HttpsURLConnection` wrapper (timeouts, JSON GET/POST) |
| `TokenStore.kt` | Keystore AES-GCM encrypted token storage (§5.3) |
| `Auth.kt` | PKCE sign-in, token refresh (single flight), token-endpoint classification (§5) |
| `UsageClient.kt` | Usage call + error classification incl. the expired-token 429 trap (§5.4, §6.1) |
| `UsageParser.kt` | Both response shapes → `Reading`, validation (§6.3) |
| `WidgetState.kt` | `Status`, pure `StateMachine` (§3.4, backoff, adaptive interval) and `Format` (countdowns, labels, TalkBack text) |
| `DotFont.kt` | Dot glyph table, D4 percent, burst geometry (§3.3) |
| `WidgetRenderer.kt` | Canvas drawing of every state on the D22 grid (three masses, two-line labels) |
| `Scheduler.kt` | `StatusStore`, `Engine` (fetch → status → render), `RefreshWorker`, periodic/one-off/re-render scheduling (§7) |
| `UsageWidget.kt` | `AppWidgetProvider` + `Widgets` render glue (bitmap, content description, tap intent) |
| `RefreshReceiver.kt` | Non-exported tap target, cooldown, routes taps per state |
| `SetupActivity.kt` | Sign-in, status (`SCOPE:`, `LAST RESULT:`), refresh, sign out, about (§3.5) |

Tests: `app/src/test/.../PureLogicTest.kt` (config, parser, state machine, format, dot font), `NetworkTest.kt` (MockWebServer: auth + usage).

Other: `config/endpoint.json` (remote config), `app/src/main/res/` (widget layout, provider info, backup rules), `docs/` (design references), `.github/workflows/` (build, release, watch).
