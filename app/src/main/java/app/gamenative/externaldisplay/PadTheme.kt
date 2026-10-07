package app.gamenative.externaldisplay

import app.gamenative.ui.screen.wow.WowFlavor

/** Muted colors for the second-screen pad, one set per game version. */
data class PadTheme(
    val background: Int,
    val key: Int,
    val border: Int,
    val text: Int,
    val pressed: Int,
) {
    companion object {
        fun forFlavor(flavor: WowFlavor): PadTheme = when (flavor) {
            WowFlavor.FOREVER -> PadTheme(0xFF0E1117.toInt(), 0xFF1A222E.toInt(), 0xFF465C7A.toInt(), 0xFFC8D2E0.toInt(), 0xFF3A4E6A.toInt())
            WowFlavor.RETAIL -> PadTheme(0xFF14110D.toInt(), 0xFF262018.toInt(), 0xFF7A6642.toInt(), 0xFFDED0B2.toInt(), 0xFF584A2E.toInt())
            WowFlavor.CLASSIC_ERA -> PadTheme(0xFF140F0E.toInt(), 0xFF261C1A.toInt(), 0xFF764C40.toInt(), 0xFFDECCC4.toInt(), 0xFF5A3830.toInt())
        }
    }
}
