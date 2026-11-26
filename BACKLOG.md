# Backlog (ideas, not built until Aliaksei says so)

- Show the plan type in the setup status line, if the API exposes it reliably. Not on the widget (decided 25 Nov, 21:43): it never changes, the percentages already reflect it, a guessed label risks being wrong, and it would crowd the layout.
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
- Open choice (Aliaksei, 21:35): divider or no divider. No-divider variants shown in chat:
  - A, rows: no line; a large whitespace gap separates the two rows. Week label pair is bottom-aligned with the week number's bottom edge, so it reads as one row.
  - B, stacked: the right column holds two groups, each with a grey label above the number and a white value below. Bottom-left stays empty; the burst sits beside the session group (keep it inside the padding).
  Aliaksei chose A over B (21:37). Week label stays left-aligned: two alignment edges only, and the row baseline does the pairing. Still to confirm on the device: A versus the version with the line.
- Macro balance (Aliaksei, 21:39): from a distance the tile should read as clean masses on one grid. Options drawn in chat:
  1. Three masses: the burst (circle) and the session block (square) are the same size D = (inner width - gap) / 2. The gap equals the padding. Both share top and bottom edges. Inside the square: label at the top edge, number fitted to the width, value at the bottom edge, all left-aligned. The week row sits on the bottom padding as one band (number height = two text lines). Cost: a tall empty band above the line.
  2. Even 2×2 grid: four equal cells, with gutters equal to the padding. Session row = burst + block; week row = its own burst (light grey, red at ≥80%) + block. Fully symmetric, but adds a second burst, so the spec changes (design decision needed).
  Aliaksei chose option 1 (three masses) and rejected option 2 (21:41). Build option 1 in M2, then fine-tune the band above the week line on the device.
- Then check on the device (§3.2 visual QA rule) and fine-tune.

## M2 design notes from the phone (26 Nov, Aliaksei)
- Padding looks the narrowest of all widgets on his home screen (vs Nothing calendar, Claude widget). Margin is ~32 px on a 340 px tile, same as the calendar, but the burst (top-left) and week % (bottom-right) sit in the rounded corners and look cramped. Benchmark against neighbouring widgets, give corners more clearance, then lock the look.

- v0.2.0 confirmed on the phone (21:08): new layout live. The ≥80% red warning state (session number and burst in red) feels like too much; try the clay/burst orange for the warning instead of red (revisits D9/D10; keep red for the full-red limit tile?). Next build.
- Release notes: tell him to open the APK link in the Brave app itself; the Claude app's in-app browser gets stuck at "Downloading…" and never saves the file. Next build.

## Other ideas
- Revisit a second card you swipe to (26 Nov, Aliaksei). It was considered in the spec chat and dropped by D15 ("no flip-through pages"): the week reset went onto the main tile instead. Review in M2 against what a second card would show that the main tile can't. Note: a swipeable widget needs a different build (a stack of cards fed by the app), not just a new drawing.
- Widget picker preview image that looks like the real tile.
