# Backlog (ideas, not built until Aliaksei says so)

- Widget picker preview image that looks like the real tile (currently a burst icon on a dark tile).
- Show the plan type in the setup status line, if the API exposes it reliably.
- Renderer golden-image snapshots of all states at 2 sizes (§10; scheduled for M3).

## M2 polish candidates (from the 25 Nov preview render, docs/preview-m1.png)
- After the D21 fit, the session number is smaller than the week number: hierarchy is inverted. Options: smaller burst, larger tile padding trade-off, or shrink the week number to match.
- `WEEK` and the reset line sit too tight at 3 dp; try 5–6 dp.
- Large empty band between the top block and the divider; rebalance vertical spacing.
