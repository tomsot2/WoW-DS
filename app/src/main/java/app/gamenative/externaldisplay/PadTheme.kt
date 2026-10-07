package app.gamenative.externaldisplay

/** Colors for the second-screen pad: pure black for OLED, light grey labels and outlines. */
data class PadTheme(
    val background: Int,
    val key: Int,
    val border: Int,
    val text: Int,
    val pressed: Int,
) {
    companion object {
        val DEFAULT = PadTheme(
            background = 0xFF000000.toInt(),
            key = 0xFF000000.toInt(),
            border = 0xFFB8B8B8.toInt(),
            text = 0xFFE6E6E6.toInt(),
            pressed = 0xFF3A3A3A.toInt(),
        )
    }
}
