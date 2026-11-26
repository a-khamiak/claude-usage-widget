# HANDOFF

**Next chat:** `Claude Usage Widget · v0.1 · M1 build (resume)`
Start it with: SPEC.md + this file. Nothing else. Attach `a-khamiak/claude-usage-widget` with push access.

## Where we stopped (25 Nov 2026, chat "Claude Usage Widget · v0.1 · M1 build")
Paused mid-M1 to protect the weekly budget (D20). Everything written is committed on `main`.

Done:
- Signing key generated (RSA 4096, 30 y). Secrets `KEYSTORE_B64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` are set in the repo (D17). No key material exists in the sandbox or the repo.
- Gradle scaffold (wrapper 8.14.3, pinned versions D16), manifest, resources, `config/endpoint.json`.
- All app code in `app/src/main/java/io/github/akhamiak/claudeusage/`: Config, Http, TokenStore, Auth, UsageParser, UsageClient, WidgetState (state machine + Format), DotFont, WidgetRenderer, Scheduler (StatusStore, Engine, RefreshWorker), UsageWidget (+ Widgets renderer glue), RefreshReceiver, SetupActivity.
- Unit tests: `PureLogicTest.kt` (config, parser, state machine, format, dot font) and `NetworkTest.kt` (MockWebServer: auth + usage classification).

**Not yet compiled.** The sandbox has no Maven/Google Maven access, so GitHub Actions is the only compiler. Expect a round or two of compile fixes.

## Update 26 Nov (chat "Claude Usage Widget · v0.1 · M1 build (resume)")
Steps 0, 0b, 1, 2, 3 done: CLAUDE.md + CODEMAP.md, D22 layout, CI green first try, v0.1.0 released via the manual trigger (D23).
Release: https://github.com/a-khamiak/claude-usage-widget/releases/tag/v0.1.0. Open: step 4 (scope test, Aliaksei installing) and step 5 (close).
For M3: Dependabot PRs for AGP 9.5.3 and Gradle wrapper 9.9.2 fail CI (major bumps, D16); Kotlin 2.4.21 and mockwebserver 5.5.1 pass. Tags: release with `gh workflow run release.yml --ref main -f version=X.Y.Z`.

## Exact next steps (in order)
0. Pre-flight (two lines: what's connected, which files you'll read). Add a short `CLAUDE.md` (build rules: the token-economy list in the build-skill memory) and a one-page `CODEMAP.md` (file → purpose, one line each).
0b. **Apply the chosen layout before the first release (D22):** `BACKLOG.md` → "Milestone 2 (M2) polish", option 1 (three masses), no divider (variant A), week label left, plan name off the widget. Changes stay in `WidgetRenderer.kt` (`drawData`, `drawLimit`, `drawMessage`) and `Format` (space between hours and minutes: `7H 25M`; two-line label system `SESSION` / `IN 2H 10M`, `OFFLINE` / `2H AGO`, `PAUSED` / `NEXT 20:45`). Update `FormatTest` expectations to match. Labels: medium weight, tracking ≈ +6% (his typography rule 5), system font kept (host consistency, rule 3). Do not touch other files. **Reference image approved by Aliaksei (26 Nov): `docs/design-final-main.png`.** It's the main (normal) state; the other states (warning, limit, offline, paused, signed out, set up, update, kill switch) follow its grid, masses and label system.
1. Write `.github/workflows/build.yml` (push/PR: wrapper validation → `./gradlew lint testDebugUnitTest assembleDebug`), `release.yml` (tag `v*`: decode `KEYSTORE_B64` to `$RUNNER_TEMP`, `KEYSTORE_PATH` env, `-PappVersionName/-PappVersionCode` from the tag, `assembleRelease`, `apksigner verify`, `sha256sum`, `gh release create` with the SHA in the notes; use env vars, never `${{ }}` inside `run`), `watch.yml` (§6.4, Monday 07:41 UTC, `issues: write`), `.github/dependabot.yml` (gradle + github-actions weekly).
   Pinned SHAs (looked up 25 Nov): `actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1` (v7.0.1), `actions/setup-java@d1374f8ac74474200d16ef8ecee22c7ae7a51880` (v6.3.0), `gradle/actions/setup-gradle@8da46b539cd8fe6ab27ca75128919a18d757604c` and `gradle/actions/wrapper-validation@8da46b539cd8fe6ab27ca75128919a18d757604c` (v6.4.1). Java 17 temurin.
2. Push, then `gh run list` / `gh run view --log-failed` until build.yml is green. Likely spots: org.json test classpath, lint issue IDs, `Activity` edge-to-edge, `RemoteViews` API levels.
3. Render the states once (e.g. a quick script or golden test) to check the layout against the backlog spec, then tag `v0.1.0`, check the Release has the signed APK + SHA-256, give Aliaksei the link and the install steps (§9 Install).
4. Scope test (D4): he signs in; the setup screen shows `SCOPE:` and `LAST RESULT:`. Log the result as a new decision.
5. Close: usage check-in row in §15, CHANGELOG entry, this file → next chat `Claude Usage Widget · v0.1 · M2 polish`, build-index registry.

## Session notes (so they aren't re-researched)
- `gh` works for the repo (contents, runs list). Writing Actions secrets is blocked by the sandbox proxy.
- Claude Pulse source: `github.com/G-biggy/claude-pulse-android` (MIT, Ghayyath Huzayen), files `app/src/main/java/com/ghayyath/claudepulse/{TokenManager,ApiClient}.kt`. Raw URLs on `raw.githubusercontent.com/.../main/...` for watch.yml.
- Claude Code changelog for watch.yml: `raw.githubusercontent.com/anthropics/claude-code/main/CHANGELOG.md`.
- Renderer golden snapshots (§10) are left for M3; M1 covers burst dot counts as pure tests.
