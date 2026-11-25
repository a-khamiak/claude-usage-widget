package io.github.akhamiak.claudeusage

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Own dot glyphs (D6, §3.3): 5×7 grid drawn as circles, so no font licensing.
 * Pure data and geometry; drawing lives in WidgetRenderer.
 */
object DotFont {
    const val ROWS = 7
    const val DOT = 0.72f // dot diameter as a fraction of the cell

    val GLYPHS: Map<Char, Array<String>> = mapOf(
        '0' to arrayOf("01110", "10001", "10011", "10101", "11001", "10001", "01110"),
        '1' to arrayOf("00100", "01100", "00100", "00100", "00100", "00100", "01110"),
        '2' to arrayOf("01110", "10001", "00001", "00010", "00100", "01000", "11111"),
        '3' to arrayOf("11111", "00010", "00100", "00010", "00001", "10001", "01110"),
        '4' to arrayOf("00010", "00110", "01010", "10010", "11111", "00010", "00010"),
        '5' to arrayOf("11111", "10000", "11110", "00001", "00001", "10001", "01110"),
        '6' to arrayOf("00110", "01000", "10000", "11110", "10001", "10001", "01110"),
        '7' to arrayOf("11111", "00001", "00010", "00100", "01000", "01000", "01000"),
        '8' to arrayOf("01110", "10001", "10001", "01110", "10001", "10001", "01110"),
        '9' to arrayOf("01110", "10001", "10001", "01111", "00001", "00010", "01100"),
        ':' to arrayOf("0", "0", "1", "0", "1", "0", "0"),
        '-' to arrayOf("000", "000", "000", "111", "000", "000", "000"),
        ' ' to arrayOf("00", "00", "00", "00", "00", "00", "00"),
        'A' to arrayOf("01110", "10001", "10001", "11111", "10001", "10001", "10001"),
        'C' to arrayOf("01110", "10001", "10000", "10000", "10000", "10001", "01110"),
        'D' to arrayOf("11110", "10001", "10001", "10001", "10001", "10001", "11110"),
        'E' to arrayOf("11111", "10000", "10000", "11110", "10000", "10000", "11111"),
        'G' to arrayOf("01110", "10001", "10000", "10111", "10001", "10001", "01111"),
        'H' to arrayOf("10001", "10001", "10001", "11111", "10001", "10001", "10001"),
        'I' to arrayOf("111", "010", "010", "010", "010", "010", "111"),
        'K' to arrayOf("10001", "10010", "10100", "11000", "10100", "10010", "10001"),
        'L' to arrayOf("10000", "10000", "10000", "10000", "10000", "10000", "11111"),
        'M' to arrayOf("10001", "11011", "10101", "10101", "10001", "10001", "10001"),
        'N' to arrayOf("10001", "10001", "11001", "10101", "10011", "10001", "10001"),
        'O' to arrayOf("01110", "10001", "10001", "10001", "10001", "10001", "01110"),
        'P' to arrayOf("11110", "10001", "10001", "11110", "10000", "10000", "10000"),
        'R' to arrayOf("11110", "10001", "10001", "11110", "10100", "10010", "10001"),
        'S' to arrayOf("01111", "10000", "10000", "01110", "00001", "00001", "11110"),
        'T' to arrayOf("11111", "00100", "00100", "00100", "00100", "00100", "00100"),
        'U' to arrayOf("10001", "10001", "10001", "10001", "10001", "10001", "01110"),
        'W' to arrayOf("10001", "10001", "10001", "10101", "10101", "10101", "01010"),
        'Y' to arrayOf("10001", "10001", "01010", "00100", "00100", "00100", "00100"),
    )

    /** D4 weighted percent, 4×4; '2' = big dot (1.4× diameter). Top edge sits at the digit top. */
    val PERCENT = arrayOf("2001", "0010", "0100", "1002")
    const val PERCENT_BIG = 1.4f

    fun glyph(c: Char): Array<String> = GLYPHS[c] ?: GLYPHS.getValue(' ')

    /** Width in cells, with one empty cell between glyphs. */
    fun widthCells(text: String): Int =
        if (text.isEmpty()) 0 else text.sumOf { glyph(it)[0].length } + (text.length - 1)

    // ---- Burst (D12, §3.3) ----
    const val RAYS = 12
    const val DOTS_PER_RAY = 5
    const val TOTAL = RAYS * DOTS_PER_RAY

    /** One burst dot in units of a 100×100 box centred on (50, 50). */
    data class BurstDot(val x: Float, val y: Float, val r: Float)

    /** Dots in fill order: ray 0 (12 o'clock) inner→outer, then clockwise. */
    val BURST: List<BurstDot> = buildList {
        for (k in 0 until RAYS) {
            val a = (-90.0 + k * 30.0) * PI / 180.0
            for (i in 0 until DOTS_PER_RAY) {
                val radius = 12.5 + i * 8.5 // ray radius step ≈ 0.085 of the box
                val dot = 2.2f + i * 0.25f // grows from 2.2 to 3.2 outward
                add(BurstDot((50 + radius * cos(a)).toFloat(), (50 + radius * sin(a)).toFloat(), dot))
            }
        }
    }

    fun litCount(pct: Int): Int = (pct.coerceIn(0, 100) / 100.0 * TOTAL).roundToInt().coerceIn(0, TOTAL)
}
