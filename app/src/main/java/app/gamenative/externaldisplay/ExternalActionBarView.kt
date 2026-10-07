package app.gamenative.externaldisplay

import android.annotation.SuppressLint
import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.winlator.xserver.XKeycode
import com.winlator.xserver.XServer

/**
 * The main second-screen pad.
 *
 * Left half: a column of window shortcuts (Map, Character, Spellbook, Talents, Skills, Quest Log).
 * Right half: F1-F12 in two rows, the 12 action buttons (4 rows of 3), and a slot underneath
 * for the modifier buttons (see [setModifierRow]).
 *
 * Every button sends WoW's default key, so the pad works with the stock bindings and needs no addon.
 * To use different keys, change the lists in the companion object.
 *
 * One touch is exactly one key press and one key release. There are no macros, sequences or timed
 * repeats here: anything multi-step belongs in WoW's own macro system.
 */
class ExternalActionBarView(
    context: Context,
    private val xServer: XServer,
    private val theme: PadTheme,
    /** Called after a button's key has been released, e.g. so one-shot modifiers can let go. */
    private val onKeyTapped: () -> Unit = {},
) : LinearLayout(context) {

    private data class Slot(val label: String, val key: XKeycode)

    private val downKeys = mutableSetOf<XKeycode>()
    private val rightColumn: LinearLayout

    init {
        orientation = HORIZONTAL
        isMotionEventSplittingEnabled = true
        setBackgroundColor(theme.background)
        val pad = dp(8)
        setPadding(pad, pad, pad, pad)

        val leftColumn = column(LEFT_WEIGHT).apply {
            PANELS.forEach { addView(keyRow(listOf(it), textSp = 15f, weight = 1f)) }
        }
        rightColumn = column(RIGHT_WEIGHT).apply {
            FUNCTION_KEYS.chunked(6).forEach { addView(keyRow(it, textSp = 13f, weight = 0.6f)) }
            ACTION_SLOTS.chunked(3).forEach { addView(keyRow(it, textSp = 26f, weight = 1f)) }
        }
        addView(leftColumn)
        addView(rightColumn)
    }

    /** Puts the modifier buttons under the action buttons. */
    fun setModifierRow(row: View) {
        (row.parent as? android.view.ViewGroup)?.removeView(row)
        row.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 0.7f)
        rightColumn.addView(row)
    }

    private fun column(weight: Float) = LinearLayout(context).apply {
        orientation = VERTICAL
        isMotionEventSplittingEnabled = true
        layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, weight)
    }

    private fun keyRow(slots: List<Slot>, textSp: Float, weight: Float): LinearLayout {
        return LinearLayout(context).apply {
            orientation = HORIZONTAL
            isMotionEventSplittingEnabled = true
            // Rows share the column's height by weight, so everything always fits.
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, weight)
            slots.forEach { addView(createButton(it, textSp)) }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createButton(slot: Slot, textSp: Float): View {
        return TextView(context).apply {
            text = slot.label
            contentDescription = slot.label
            gravity = Gravity.CENTER
            setTextColor(theme.text)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, textSp)
            typeface = theme.typeface
            maxLines = 1
            background = createKeyBackground()
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply {
                val margin = dp(3)
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
        onKeyTapped()
    }

    private fun createKeyBackground() = theme.buttonStates(resources.displayMetrics.density, 10f)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    /** Never leave a key stuck down if the display goes away or the game closes mid-press. */
    override fun onDetachedFromWindow() {
        downKeys.toList().forEach { xServer.injectKeyRelease(it) }
        downKeys.clear()
        super.onDetachedFromWindow()
    }

    private companion object {
        // Width split between the window-shortcut column and the F-key/number/modifier block.
        // A smaller RIGHT_WEIGHT squeezes that block toward the right edge, within reach of a right thumb.
        const val LEFT_WEIGHT = 1f
        const val RIGHT_WEIGHT = 1f

        // WoW's default bindings for the character, spellbook and similar windows.
        val PANELS = listOf(
            Slot("Map", XKeycode.KEY_M),
            Slot("Character", XKeycode.KEY_C),
            Slot("Spellbook", XKeycode.KEY_P),
            Slot("Talents", XKeycode.KEY_N),
            Slot("Skills", XKeycode.KEY_K),
            Slot("Quest Log", XKeycode.KEY_L),
        )

        val FUNCTION_KEYS = listOf(
            Slot("F1", XKeycode.KEY_F1),
            Slot("F2", XKeycode.KEY_F2),
            Slot("F3", XKeycode.KEY_F3),
            Slot("F4", XKeycode.KEY_F4),
            Slot("F5", XKeycode.KEY_F5),
            Slot("F6", XKeycode.KEY_F6),
            Slot("F7", XKeycode.KEY_F7),
            Slot("F8", XKeycode.KEY_F8),
            Slot("F9", XKeycode.KEY_F9),
            Slot("F10", XKeycode.KEY_F10),
            Slot("F11", XKeycode.KEY_F11),
            Slot("F12", XKeycode.KEY_F12),
        )

        // WoW's default bindings for Action Bar 1, slots 1 to 12.
        val ACTION_SLOTS = listOf(
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
