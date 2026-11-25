package io.github.akhamiak.claudeusage

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import java.time.ZoneId

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
 * Draws the 2×2 tile onto a Bitmap (§3, §4). Layout Y, proportional to the tile side L.
 * Locked design: D9–D15. Final spacing polish happens on the device in M2.
 */
object WidgetRenderer {
    private const val TILE = 0xFF1B1B1D.toInt()
    private const val WHITE = 0xFFF2F2F2.toInt()
    private const val LABEL = 0xFFBDBDBD.toInt()
    private const val UNLIT = 0xFF3A3A3C.toInt()
    private const val DIVIDER = 0xFF333333.toInt()
    private const val CLAY = 0xFFD97757.toInt()
    private const val WARN = 0xFFE0262F.toInt()
    private const val RED_TILE = 0xFFD71921.toInt()
    private const val GREY = 0xFF7A7A7A.toInt()

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        letterSpacing = 0.12f
    }

    fun render(state: WidgetState, spec: RenderSpec): Bitmap {
        val w = spec.widthPx.coerceIn(64, 1200)
        val h = spec.heightPx.coerceIn(64, 1200)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val g = Geo(w.toFloat(), h.toFloat(), spec)
        val bg = if (state is WidgetState.Limit) RED_TILE else TILE
        dotPaint.color = bg
        c.drawRoundRect(RectF(0f, 0f, g.w, g.h), spec.cornerPx, spec.cornerPx, dotPaint)

        when (state) {
            is WidgetState.Data -> drawData(c, g, state, spec)
            is WidgetState.Limit -> drawLimit(c, g, state, spec)
            WidgetState.NotSetUp -> drawMessage(c, g, listOf("SET", "UP"), CLAY, listOf("TAP TO SIGN IN"))
            WidgetState.SignedOut -> drawMessage(c, g, listOf("SIGN", "IN"), WHITE, listOf("SESSION EXPIRED · TAP"))
            WidgetState.EndpointChanged ->
                drawMessage(c, g, listOf("UPDATE"), WHITE, listOf("USAGE SOURCE CHANGED", "TAP TO CHECK FIX"))
            is WidgetState.KillSwitch ->
                drawMessage(c, g, listOf("PAUSED"), WHITE, listOfNotNull(state.message.ifEmpty { null }))
        }
        val refreshing = (state as? WidgetState.Data)?.refreshing == true || (state as? WidgetState.Limit)?.refreshing == true
        if (refreshing) {
            dotPaint.color = if (state is WidgetState.Limit) WHITE else CLAY
            c.drawCircle(g.w - g.pad / 2f, g.pad / 2f, g.l * 0.018f, dotPaint)
        }
        return bmp
    }

    /** Geometry helpers in px. */
    private class Geo(val w: Float, val h: Float, val spec: RenderSpec) {
        val l = minOf(w, h)
        val pad = 0.094f * l
        val left = pad
        val right = w - pad
        val top = pad
        val bottom = h - pad
        fun dp(v: Float) = v * spec.density
        val textSize = minOf(11f * spec.density * spec.fontScale, 0.068f * l)
        val capH = textSize * 0.72f
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

        // Top block: burst left, number + caption right, centres on one line.
        val topH = 0.48f * g.l
        val cy = g.top + topH / 2f
        val boxW = 0.45f * g.l
        val boxH = 0.41f * g.l
        drawBurst(c, g.left + boxW / 2f, cy, boxH, r?.sessionPct ?: 0, burstColor)

        val digitH = 0.20f * g.l
        val gap7 = g.dp(7f)
        val colH = digitH + gap7 + g.capH
        val numTop = cy - colH / 2f
        drawNumber(c, r?.sessionPct?.toString() ?: "--", g.right, numTop, digitH, sessionColor)
        val caption = when (val m = st.mode) {
            is Mode.Stale -> if (m.ageMs < 0) "OFFLINE" else "OFFLINE · " + Format.age(m.ageMs)
            is Mode.Paused -> "PAUSED · NEXT " + Format.clock(m.nextTryAt, spec.zone, spec.is24h)
            Mode.Normal -> Format.sessionCaption(r?.sessionResetsAt, spec.now)
        }
        drawText(c, caption, g.right, numTop + digitH + gap7 + g.capH, if (stale) GREY else LABEL, g, Paint.Align.RIGHT)

        // Bottom row (D15): WEEK over the reset, week number centred on both lines.
        val gap3 = g.dp(3f)
        val blockH = g.capH * 2 + gap3
        val blockTop = g.bottom - blockH
        drawText(c, "WEEK", g.left, blockTop + g.capH, if (stale) GREY else LABEL, g, Paint.Align.LEFT)
        val weekText = if (r == null) "—" else Format.weekReset(r.weekResetsAt, spec.now, spec.zone, spec.is24h)
        drawText(c, weekText, g.left, g.bottom, if (stale) GREY else WHITE, g, Paint.Align.LEFT)
        val weekDigitH = 0.13f * g.l
        drawNumber(c, r?.weekPct?.toString() ?: "--", g.right, blockTop + blockH / 2f - weekDigitH / 2f, weekDigitH, weekColor)

        // Divider: full inner width, 10 dp above the bottom row.
        dotPaint.color = DIVIDER
        val dy = blockTop - g.dp(10f)
        c.drawRect(g.left, dy, g.right, dy + maxOf(1f, g.dp(1f)), dotPaint)
    }

    private fun drawBurst(c: Canvas, cx: Float, cy: Float, side: Float, pct: Int, lit: Int) {
        val unit = side / 100f
        val n = DotFont.litCount(pct)
        DotFont.BURST.forEachIndexed { i, d ->
            dotPaint.color = if (i < n) lit else UNLIT
            c.drawCircle(cx + (d.x - 50f) * unit, cy + (d.y - 50f) * unit, d.r * unit, dotPaint)
        }
    }

    /** Right-aligned digits + D4 percent. Returns nothing; [top] is the digit top edge. */
    private fun drawNumber(c: Canvas, digits: String, right: Float, top: Float, digitH: Float, color: Int) {
        val cell = digitH / DotFont.ROWS
        val pctW = 4 * cell
        val digitsW = DotFont.widthCells(digits) * cell
        val x0 = right - pctW - cell - digitsW
        drawDots(c, digits, x0, top, cell, color)
        drawPercent(c, right - pctW, top, cell, color)
    }

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

    private fun drawText(c: Canvas, s: String, x: Float, baseline: Float, color: Int, g: Geo, align: Paint.Align) {
        textPaint.color = color
        textPaint.textSize = g.textSize
        textPaint.textAlign = align
        // Shrink to fit the inner width rather than clip.
        val maxW = g.right - g.left
        val wMeasured = textPaint.measureText(s)
        if (wMeasured > maxW) textPaint.textSize = g.textSize * maxW / wMeasured
        c.drawText(s, x, baseline, textPaint)
    }

    // ---------- Limit: full red tile ----------

    private fun drawLimit(c: Canvas, g: Geo, st: WidgetState.Limit, spec: RenderSpec) {
        val innerW = g.right - g.left
        val word = "LIMIT"
        val wordCell = minOf(0.13f * g.l / DotFont.ROWS, innerW / DotFont.widthCells(word))
        drawDots(c, word, g.left, g.top, wordCell, WHITE)

        val time = st.resetsAt?.takeIf { it > spec.now }?.let { Format.clock(it, spec.zone, spec.is24h) } ?: "--:--"
        val timeCell = minOf(0.20f * g.l / DotFont.ROWS, innerW / DotFont.widthCells(time))
        val timeTop = g.top + wordCell * DotFont.ROWS + 0.08f * g.l
        drawDots(c, time, g.left, timeTop, timeCell, WHITE)

        val label = when (st.window) {
            Window.SESSION -> "SESSION RESETS"
            Window.WEEK -> {
                val at = st.resetsAt
                if (at != null && at - spec.now > 24 * 3_600_000L) "WEEK RESETS " + Format.day(at, spec.zone) else "WEEK RESETS"
            }
        }
        drawText(c, label, g.left, g.bottom, WHITE, g, Paint.Align.LEFT)
    }

    // ---------- Message states ----------

    private fun drawMessage(c: Canvas, g: Geo, words: List<String>, wordColor: Int, captions: List<String>) {
        val innerW = g.right - g.left
        val widest = words.maxOf { DotFont.widthCells(it) }.coerceAtLeast(1)
        val cell = minOf(0.20f * g.l / DotFont.ROWS, innerW / widest)
        var top = g.top
        for (w in words) {
            drawDots(c, w, g.left, top, cell, wordColor)
            top += cell * (DotFont.ROWS + 2)
        }
        val lineStep = g.capH + g.dp(4f)
        var baseline = g.bottom - (captions.size - 1) * lineStep
        for (cap in captions) {
            drawText(c, cap, g.left, baseline, LABEL, g, Paint.Align.LEFT)
            baseline += lineStep
        }
    }
}
