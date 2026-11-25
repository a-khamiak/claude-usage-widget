# Backlog (ideas, not built until Aliaksei says so)

- Show the plan type in the setup status line, if the API exposes it reliably.
- Renderer golden-image snapshots of all states at 2 sizes (§10; scheduled for M3).

## Milestone 2 (M2) polish: refined layout (approved direction 25 Nov, chat "Claude Usage Widget · v0.1 · M1 build")
Aliaksei's notes: divider too close to WEEK; needs a space in `7H25M`; spacing between text and blocks unbalanced, padding not uniform, looks like a draft.
Refined spec (shown as vector tiles in chat; replaces §3.2 sizes once applied, then log as a decision):
- One spacing grid: unit u = L/32. Padding 3u on all four sides (= 0.094·L, unchanged).
- Bottom block: label over value, line gap 1u (was 3 dp).
- Divider optically centred (Aliaksei, 21:33): equal gap between the lowest visible burst dot above and the cap tops of `WEEK` below. Measure from visible edges, not layout boxes.
- Burst 0.42·L, top edge on the top padding; its visible bottom sets the upper gap. Session column shares the burst's vertical centre; session digits fit the column (≈0.15·L, fit rule D21). Week number 0.10·L. Hierarchy: session > week.
- Gap burst → right column 0.05·L. All numbers and right-aligned labels end on the same right edge.
- One label system everywhere: grey label line over a white value line.
  - Session: `SESSION` / `IN 2H 10M` (replaces the one-line `5H · 2H14M`).
  - Week: `WEEK` / `WED 14:00` or `IN 7H 25M`.
  - Offline: `OFFLINE` / `2H AGO` (all grey). Paused: `PAUSED` / `NEXT 20:45`.
  - Limit (red tile): small `LIMIT` dots, big reset time, then `SESSION RESETS` (pink #FFC4C7) / `IN 1H 40M`.
  - Signed out: `SIGN` / `IN` dots, then `SESSION EXPIRED` / `TAP TO SIGN IN`.
- Countdowns get a space between hours and minutes: `7H 25M`, `2H 10M`.
- Labels one size: 0.058·L (≈10 sp at the 2×2 size); letter-spacing 0.12 em. Label grey #9A9A9C, unlit dots #353537, divider #2E2E30, stale grey #6E6E70.
- Refreshing dot centred in the corner padding (1.5u, 1.5u), radius 0.016·L.
- Then check on the device (§3.2 visual QA rule) and fine-tune.

## Other ideas
- Widget picker preview image that looks like the real tile.
