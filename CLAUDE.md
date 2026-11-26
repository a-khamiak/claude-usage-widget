# CLAUDE.md: build rules for this repo

Truth lives in `SPEC.md` (decision log §2), `HANDOFF.md` (where we stopped), `CHANGELOG.md`, `BACKLOG.md`. File map: `CODEMAP.md`.

## Token economy (approved 26 Nov 2026)
1. Lean setup: only the tools this build needs; project rules live here, not in global preferences.
2. One milestone per chat; hand off to a fresh chat when it's done (`HANDOFF.md`, chat name `Claude Usage Widget · <version> · <purpose>`).
3. Quiet tool output: quiet flags, failures only, log tails. No full diffs or install logs.
4. No screenshots where text works. Read the usage page as text.
5. Search first, read line ranges, keep files small and single-purpose, use `CODEMAP.md` instead of opening files to orient.
6. Write a whole module, then test once. Bugs: reproduce with a test, one fix, rethink after two failed attempts.
7. Lighter model for routine coding and tests; strongest for planning and hard bugs.
8. Measure: run the explain-usage skill at the end of each milestone and log it in SPEC §15.

## Build rules
- Pre-flight at the start of every chat: two lines (what's connected, which files will be read).
- Skeleton plan (≤10 lines) before each step.
- The sandbox has no Google Maven: GitHub Actions (`build.yml`) is the only compiler. Push, then `gh run view --log-failed`.
- Ideas raised mid-build go to `BACKLOG.md`; nothing is built until Aliaksei says so.
- New decisions go into SPEC §2 first (with the source chat), then the code.
- Refactor, independent review and security checks happen at milestones (M3), not per change.
- Never commit or print key material. Signing comes only from Actions secrets.
- Workflows: actions pinned to SHAs, least-privilege `permissions`, env vars instead of `${{ }}` inside `run`.
- End of chat: update `HANDOFF.md` + `CHANGELOG.md`, then the build-index registry in memory, and check they match.
