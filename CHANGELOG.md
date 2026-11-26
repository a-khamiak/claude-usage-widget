# Changelog

## v0.2.0 (26 Nov 2026)
- D25 layout: text sized and weighted like the system widgets on the home screen, mixed case (`Session`, `in 40 min`, `Wed 14:00`), equal margins, smaller burst, heavier dots, bigger numbers.
- Scope test passed with `user:profile` (D24).

## v0.1.0 (26 Nov 2026)
Chats: "Claude Usage Widget · v0.1 · M1 build" (25 Nov, paused, D20) and "Claude Usage Widget · v0.1 · M1 build (resume)" (26 Nov).
- D22 layout: three masses on the u = L/32 grid, no divider, two-line labels on every state, spaced countdowns (`7H 25M`).
- CI: build.yml (lint, unit tests, debug build), release.yml (signed release on a tag or a manual run, D23), watch.yml (weekly endpoint watch), Dependabot.
- CLAUDE.md (build rules) and CODEMAP.md.
- Release: https://github.com/a-khamiak/claude-usage-widget/releases/tag/v0.1.0, APK SHA-256 `bf8747c2b255f15e8bbeaded097f717d33993e09177ec7727f2e2fd59ddf181d`, signing cert matches D17.
- Project scaffold, remote config, signing key + Actions secrets.
- App code: PKCE sign-in, Keystore token storage, usage client with §5.4 classification, parser with health check, state machine, dot font, renderer, scheduling, widget, setup screen.
- Unit tests for config, parser, state machine, formatting, dot font, auth and usage client.
