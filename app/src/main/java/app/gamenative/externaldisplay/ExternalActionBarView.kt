package app.gamenative.externaldisplay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import app.gamenative.R
import com.winlator.xserver.XKeycode
import com.winlator.xserver.XServer

/**
 * Twelve touch buttons for the second display, in the order of WoW's main action bar (four rows of three).
 *
 * The slots send WoW's default keys for "Action Bar 1": 1 2 3 4 5 6 7 8 9 0 - =. That means the pad
 * works with the stock key bindings and needs no addon. To use different keys, change [SLOTS].
 *
 * One touch is exactly one key press and one key release. There are no macros, sequences or timed
 * repeats here: anything multi-step belongs in WoW's own macro system.
 */
class ExternalActionBarView(context: Context, private val xServer: XServer) : LinearLayout(context) {

    private data class Slot(val label: String, val key: XKeycode)

    private val downKeys = mutableSetOf<XKeycode>()

    private val keyColor = ContextCompat.getColor(context, R.color.external_display_key_color)
    private val keyBackground = ContextCompat.getColor(context, R.color.external_display_key_background)
    private val keyPressed = ContextCompat.getColor(context, R.color.external_display_key_highlight_strong_background)
    private val keyBorder = 0xFF3A3A3A.toInt()

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        isMotionEventSplittingEnabled = true
        setBackgroundColor(ContextCompat.getColor(context, R.color.external_display_surface_background))
        val pad = dp(12)
        setPadding(pad, pad, pad, pad)

        SLOTS.chunked(COLUMNS).forEach { rowSlots ->
            val row = LinearLayout(context).apply {
                orientation = HORIZONTAL
                isMotionEventSplittingEnabled = true
                // Rows share the screen height equally, so four rows always fit.
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
            }
            rowSlots.forEach { slot -> row.addView(createButton(slot)) }
            addView(row)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createButton(slot: Slot): View {
        return TextView(context).apply {
            text = slot.label
            contentDescription = "Action ${slot.label}"
            gravity = Gravity.CENTER
            setTextColor(keyColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f)
            typeface = Typeface.DEFAULT_BOLD
            background = createKeyBackground()
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply {
                val margin = dp(6)
                setMargins(margin, margin, margin, margin)
            }
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        view.isPressed = true
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        pressKey(slot.key)
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        view.isPressed = false
                        releaseKey(slot.key)
                    }
                }
                true
            }
        }
    }

    private fun pressKey(key: XKeycode) {
        if (!downKeys.add(key)) return
        xServer.injectKeyPress(key)
    }

    private fun releaseKey(key: XKeycode) {
        if (!downKeys.remove(key)) return
        xServer.injectKeyRelease(key)
    }

    private fun createKeyBackground(): StateListDrawable {
        fun shape(fill: Int) = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(fill)
            setStroke(dp(2), keyBorder)
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), shape(keyPressed))
            addState(intArrayOf(), shape(keyBackground))
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    /** Never leave a key stuck down if the display goes away or the game closes mid-press. */
    override fun onDetachedFromWindow() {
        downKeys.toList().forEach { xServer.injectKeyRelease(it) }
        downKeys.clear()
        super.onDetachedFromWindow()
    }

    private companion object {
        const val COLUMNS = 3

        // WoW's default bindings for Action Bar 1, slots 1 to 12.
        val SLOTS = listOf(
            Slot("1", XKeycode.KEY_1),
            Slot("2", XKeycode.KEY_2),
            Slot("3", XKeycode.KEY_3),
            Slot("4", XKeycode.KEY_4),
            Slot("5", XKeycode.KEY_5),
            Slot("6", XKeycode.KEY_6),
            Slot("7", XKeycode.KEY_7),
            Slot("8", XKeycode.KEY_8),
            Slot("9", XKeycode.KEY_9),
            Slot("0", XKeycode.KEY_0),
            Slot("-", XKeycode.KEY_MINUS),
            Slot("=", XKeycode.KEY_EQUAL),
        )
    }
}
