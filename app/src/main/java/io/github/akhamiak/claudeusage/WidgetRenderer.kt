package io.github.akhamiak.claudeusage

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import java.time.ZoneId
import kotlin.math.hypot

/** Everything the renderer needs besides the state; units in px. */
data class RenderSpec(
    val widthPx: Int,
    val heightPx: Int,
    val density: Float, // px per dp
    val fontScale: Float, // sp→px = density * fontScale
    val cornerPx: Float,
    val is24h: Boolean,
    val zone: ZoneId,
    val now: Long,
)

/**
 * Draws the 2×2 tile onto a Bitmap (§3, §4).
 * Layout D22 ("three masses", BACKLOG M2 option 1, variant A): one grid with u = L/32 and padding 3u.
 * Top row: burst circle and session square of equal size D, gap = padding. Bottom band: week label pair
 * on the left, week number on the right, its height equal to the two label lines. No divider.
 * One label system everywhere: grey label line over a white value line. Reference: docs/design-final-main.png.
 */
object WidgetRenderer {
    private const val TILE = 0xFF1B1B1D.toInt()
    private const val WHITE = 0xFFF2F2F2.toInt()
    private const val LABEL = 0xFF9A9A9C.toInt()
    private const val UNLIT = 0xFF353537.toInt()
    private const val CLAY = 0xFFD97757.toInt()
    private const val WARN = 0xFFE0262F.toInt()
    private const val RED_TILE = 0xFFD71921.toInt()
    private const val PINK = 0xFFFFC4C7.toInt()
    private const val GREY = 0xFF6E6E70.toInt()

    /** Visible radius of the burst in its 100-unit box (outer dot edge), so the circle measures by its dots. */
    private val BURST_EXTENT = DotFont.BURST.maxOf { hypot(it.x - 50f, it.y - 50f) + it.r }

    /** Empty margin inside a dot cell, so dot text aligns by its visible edge. */
    private const val CELL_INSET = (1f - DotFont.DOT) / 2f

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        letterSpacing = 0.06f // typography rule 5 (D22)
    }

    fun render(state: WidgetState, spec: RenderSpec): Bitmap {
        val w = spec.widthPx.coerceIn(64, 1200)
        val h = spec.heightPx.coerceIn(64, 1200)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val g = Geo(w.toFloat(), h.toFloat())
        val bg = if (state is WidgetState.Limit) RED_TILE else TILE
        dotPaint.color = bg
        c.drawRoundRect(RectF(0f, 0f, g.w, g.h), spec.cornerPx, spec.cornerPx, dotPaint)

        when (state) {
            is WidgetState.Data -> drawData(c, g, state, spec)
            is WidgetState.Limit -> drawLimit(c, g, state, spec)
            WidgetState.NotSetUp -> drawMessage(c, g, listOf("SET", "UP"), CLAY, null, "TAP TO SIGN IN")
            WidgetState.SignedOut -> drawMessage(c, g, listOf("SIGN", "IN"), WHITE, "SESSION EXPIRED", "TAP TO SIGN IN")
            WidgetState.EndpointChanged ->
                drawMessage(c, g, listOf("UPDATE"), WHITE, "USAGE SOURCE CHANGED", "TAP TO CHECK FIX")
            is WidgetState.KillSwitch ->
                drawMessage(c, g, listOf("PAUSED"), WHITE, state.message.ifEmpty { null }, null)
        }
        val refreshing = (state as? WidgetState.Data)?.refreshing == true || (state as? WidgetState.Limit)?.refreshing == true
        if (refreshing) {
            dotPaint.color = if (state is WidgetState.Limit) WHITE else CLAY
            c.drawCircle(g.w - 1.5f * g.u, 1.5f * g.u, 0.016f * g.l, dotPaint)
        }
        return bmp
    }

    /** Grid geometry in px (BACKLOG M2 polish, D22). */
    private class Geo(val w: Float, val h: Float) {
        val l = minOf(w, h)
        val u = l / 32f
        val pad = 3f * u
        val left = pad
        val right = w - pad
        val top = pad
        val bottom = h - pad
        val innerW = right - left
        val textSize = 0.058f * l
        val capH = textSize * 0.711f // Roboto cap height
        /** Height of a label pair: cap, 1u gap, cap. */
        val pairH = 2f * capH + u
        /** Size of the burst circle and the session square. */
        val d = minOf((innerW - pad) / 2f, bottom - top - pairH - 2f * u)
    }

    // ---------- Normal / Warning / Stale / Paused ----------

    private fun drawData(c: Canvas, g: Geo, st: WidgetState.Data, spec: RenderSpec) {
        val r = st.reading
        val stale = st.mode is Mode.Stale
        val sessionWarn = r != null && r.sessionPct >= StateMachine.WARN_PCT
        val weekWarn = r != null && r.weekPct >= StateMachine.WARN_PCT
        val sessionColor = when { stale -> GREY; sessionWarn -> WARN; else -> WHITE }
        val weekColor = when { stale -> GREY; weekWarn -> WARN; else -> WHITE }
        val burstColor = when { stale -> GREY; sessionWarn -> WARN; else -> CLAY }
        val labelColor = if (stale) GREY else LABEL
        val valueColor = if (stale) GREY else WHITE

        // Mass 1: the burst circle, visible edge on the top and left padding.
        drawBurst(c, g.left + g.d / 2f, g.top + g.d / 2f, g.d, r?.sessionPct ?: 0, burstColor)

        // Mass 2: the session square. Label on its top edge, value on its bottom edge, number between.
        val x = g.left + g.d + g.pad
        val colW = g.right - x
        val (label, value) = when (val m = st.mode) {
            is Mode.Stale -> "OFFLINE" to (if (m.ageMs < 0) "—" else Format.age(m.ageMs))
            is Mode.Paused -> "PAUSED" to "NEXT " + Format.clock(m.nextTryAt, spec.zone, spec.is24h)
            Mode.Normal -> "SESSION" to Format.sessionValue(r?.sessionResetsAt, spec.now)
        }
        drawText(c, label, x, g.top + g.capH, labelColor, g, Paint.Align.LEFT, colW)
        val valueBaseline = g.top + g.d
        drawText(c, value, x, valueBaseline, valueColor, g, Paint.Align.LEFT, colW)

        val sText = r?.sessionPct?.toString() ?: "--"
        val slotTop = g.top + g.capH + 1.5f * g.u
        val slotBottom = valueBaseline - g.capH - 1.5f * g.u
        val cell = minOf(colW / numberCells(sText), (slotBottom - slotTop) / visibleRows())
        val numTop = (slotTop + slotBottom) / 2f - visibleRows() * cell / 2f
        drawNumber(c, sText, x, numTop, cell, sessionColor)

        // Mass 3: the week band on the bottom padding. Number height = the two label lines (variant A).
        val bandTop = g.bottom - g.pairH
        val weekText = if (r == null) "—" else Format.weekReset(r.weekResetsAt, spec.now, spec.zone, spec.is24h)
        drawPair(c, g, "WEEK", weekText, labelColor, valueColor)
        val wText = r?.weekPct?.toString() ?: "--"
        val wCell = g.pairH / visibleRows()
        drawNumber(c, wText, g.right - numberCells(wText) * wCell, bandTop, wCell, weekColor)
    }

    private fun drawBurst(c: Canvas, cx: Float, cy: Float, visibleD: Float, pct: Int, lit: Int) {
        val unit = visibleD / (2f * BURST_EXTENT)
        val n = DotFont.litCount(pct)
        DotFont.BURST.forEachIndexed { i, d ->
            dotPaint.color = if (i < n) lit else UNLIT
            c.drawCircle(cx + (d.x - 50f) * unit, cy + (d.y - 50f) * unit, d.r * unit, dotPaint)
        }
    }

    /** Dot rows that are visible ink (the cell inset is trimmed top and bottom). */
    private fun visibleRows() = DotFont.ROWS - 2f * CELL_INSET

    /** Width in cells of digits + one gap + the 4-cell D4 percent. */
    private fun numberCells(digits: String) = (DotFont.widthCells(digits) + 1 + 4).toFloat()

    /** Digits + D4 percent; [x] and [top] are the visible left and top edges. */
    private fun drawNumber(c: Canvas, digits: String, x: Float, top: Float, cell: Float, color: Int) {
        val x0 = x - CELL_INSET * cell
        val y0 = top - CELL_INSET * cell
        drawDots(c, digits, x0, y0, cell, color)
        drawPercent(c, x0 + (DotFont.widthCells(digits) + 1) * cell, y0, cell, color)
    }

    /** Dot text with its visible left/top edges at [x]/[top]. */
    private fun drawWord(c: Canvas, text: String, x: Float, top: Float, cell: Float, color: Int) =
        drawDots(c, text, x - CELL_INSET * cell, top - CELL_INSET * cell, cell, color)

    private fun drawDots(c: Canvas, text: String, x: Float, top: Float, cell: Float, color: Int) {
        dotPaint.color = color
        val r = cell * DotFont.DOT / 2f
        var cx = x
        for (ch in text) {
            val g = DotFont.glyph(ch)
            for (row in g.indices) for (col in g[row].indices) {
                if (g[row][col] == '1') c.drawCircle(cx + (col + 0.5f) * cell, top + (row + 0.5f) * cell, r, dotPaint)
            }
            cx += (g[0].length + 1) * cell
        }
    }

    private fun drawPercent(c: Canvas, x: Float, top: Float, cell: Float, color: Int) {
        dotPaint.color = color
        val r = cell * DotFont.DOT / 2f
        for (row in DotFont.PERCENT.indices) for (col in 0 until 4) {
            val v = DotFont.PERCENT[row][col]
            if (v == '0') continue
            val rr = if (v == '2') r * DotFont.PERCENT_BIG else r
            c.drawCircle(x + (col + 0.5f) * cell, top + (row + 0.5f) * cell, rr, dotPaint)
        }
    }

    private fun drawText(
        c: Canvas, s: String, x: Float, baseline: Float, color: Int, g: Geo, align: Paint.Align,
        maxW: Float = g.innerW,
    ) {
        textPaint.color = color
        textPaint.textSize = g.textSize
        textPaint.textAlign = align
        // Shrink to fit rather than clip.
        val wMeasured = textPaint.measureText(s)
        if (wMeasured > maxW) textPaint.textSize = g.textSize * maxW / wMeasured
        c.drawText(s, x, baseline, textPaint)
    }

    /** Label pair on the bottom padding, left edge. Either line may be absent; a lone line sits on the bottom. */
    private fun drawPair(c: Canvas, g: Geo, label: String?, value: String?, labelColor: Int, valueColor: Int) {
        if (value != null) {
            drawText(c, value, g.left, g.bottom, valueColor, g, Paint.Align.LEFT)
            if (label != null) drawText(c, label, g.left, g.bottom - g.capH - g.u, labelColor, g, Paint.Align.LEFT)
        } else if (label != null) {
            drawText(c, label, g.left, g.bottom, labelColor, g, Paint.Align.LEFT)
        }
    }

    // ---------- Limit: full red tile ----------

    private fun drawLimit(c: Canvas, g: Geo, st: WidgetState.Limit, spec: RenderSpec) {
        // Small LIMIT word, same height as the week number (two label lines).
        val wordCell = minOf(g.pairH / visibleRows(), g.innerW / DotFont.widthCells("LIMIT"))
        drawWord(c, "LIMIT", g.left, g.top, wordCell, WHITE)

        // Big reset time, centred between the word and the bottom band.
        val time = st.resetsAt?.takeIf { it > spec.now }?.let { Format.clock(it, spec.zone, spec.is24h) } ?: "--:--"
        val timeCell = minOf(0.22f * g.l / visibleRows(), g.innerW / DotFont.widthCells(time))
        val above = g.top + visibleRows() * wordCell
        val below = g.bottom - g.pairH
        val timeTop = (above + below) / 2f - visibleRows() * timeCell / 2f
        drawWord(c, time, g.left, timeTop, timeCell, WHITE)

        val label = if (st.window == Window.SESSION) "SESSION RESETS" else "WEEK RESETS"
        drawPair(c, g, label, Format.limitValue(st.resetsAt, spec.now, spec.zone), PINK, WHITE)
    }

    // ---------- Message states ----------

    private fun drawMessage(c: Canvas, g: Geo, words: List<String>, wordColor: Int, label: String?, value: String?) {
        val widest = words.maxOf { DotFont.widthCells(it) }.coerceAtLeast(1)
        val cell = minOf(0.20f * g.l / visibleRows(), g.innerW / widest)
        var top = g.top
        for (w in words) {
            drawWord(c, w, g.left, top, cell, wordColor)
            top += cell * (DotFont.ROWS + 2)
        }
        drawPair(c, g, label, value, LABEL, WHITE)
    }
}
