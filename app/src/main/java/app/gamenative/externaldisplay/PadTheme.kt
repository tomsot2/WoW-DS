package app.gamenative.externaldisplay

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable

/**
 * Look of the second-screen pad: dark stone panels with gold trim and cream serif lettering.
 * All colors and shapes are drawn in code, so no game artwork is used.
 */
data class PadTheme(
    val background: Int,
    val keyTop: Int,
    val keyBottom: Int,
    val pressedTop: Int,
    val pressedBottom: Int,
    val border: Int,
    val borderInner: Int,
    val borderBright: Int,
    val text: Int,
    val textPressed: Int,
) {
    val typeface: Typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)

    /** Dim, thinner trim for secondary buttons, so the action buttons stand out. */
    private val borderMuted: Int get() = blend(border, keyBottom, MUTED_AMOUNT)
    private val borderInnerMuted: Int get() = blend(borderInner, keyBottom, MUTED_AMOUNT)

    private fun blend(from: Int, to: Int, amount: Float): Int {
        fun mix(a: Int, b: Int) = (a + (b - a) * amount).toInt()
        return Color.argb(
            mix(Color.alpha(from), Color.alpha(to)),
            mix(Color.red(from), Color.red(to)),
            mix(Color.green(from), Color.green(to)),
            mix(Color.blue(from), Color.blue(to)),
        )
    }

    /**
     * One button face. [emphasized] gives it the bright outline used for "locked".
     * [muted] dims the trim of a resting button; pressed or emphasized buttons always keep the full trim.
     */
    fun buttonBackground(
        density: Float,
        radiusDp: Float,
        active: Boolean,
        emphasized: Boolean = false,
        muted: Boolean = false,
    ): Drawable {
        val dim = muted && !active && !emphasized
        val top = if (active) pressedTop else keyTop
        val bottom = if (active) pressedBottom else keyBottom
        val outer = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(top, bottom)).apply {
            cornerRadius = radiusDp * density
            val strokeDp = if (emphasized) 4 else if (dim) 2 else 3
            setStroke((strokeDp * density).toInt(), if (emphasized) borderBright else if (dim) borderMuted else border)
        }
        val inner = GradientDrawable().apply {
            cornerRadius = (radiusDp - 2).coerceAtLeast(2f) * density
            setColor(Color.TRANSPARENT)
            setStroke(density.toInt().coerceAtLeast(1), if (dim) borderInnerMuted else borderInner)
        }
        val inset = (3 * density).toInt()
        return LayerDrawable(arrayOf(outer, inner)).apply { setLayerInset(1, inset, inset, inset, inset) }
    }

    /** Normal face, switching to the glowing face while pressed. */
    fun buttonStates(density: Float, radiusDp: Float, muted: Boolean = false): StateListDrawable = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed), buttonBackground(density, radiusDp, active = true))
        addState(intArrayOf(), buttonBackground(density, radiusDp, active = false, muted = muted))
    }

    companion object {
        // How far a muted border is pulled toward the button face (0 = unchanged, 1 = invisible).
        private const val MUTED_AMOUNT = 0.55f

        val DEFAULT = PadTheme(
            background = 0xFF0A0807.toInt(),
            keyTop = 0xFF28201A.toInt(),
            keyBottom = 0xFF16110E.toInt(),
            pressedTop = 0xFF784E16.toInt(),
            pressedBottom = 0xFF462C0C.toInt(),
            border = 0xFFB08C40.toInt(),
            borderInner = 0xFF5E461C.toInt(),
            borderBright = 0xFFDEBA64.toInt(),
            text = 0xFFECDEBA.toInt(),
            textPressed = 0xFFFFECB4.toInt(),
        )
    }
}
