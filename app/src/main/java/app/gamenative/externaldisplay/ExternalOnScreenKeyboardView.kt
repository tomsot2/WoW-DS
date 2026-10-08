package app.gamenative.externaldisplay

import android.content.Context
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import app.gamenative.R
import com.winlator.xserver.XKeycode
import com.winlator.xserver.XServer

/**
 * The pad's on-screen keyboard. Normally every key goes to the game. With [localInput] set, keys go to
 * that callback instead (the pad's own text fields, such as the button editor in settings), and nothing
 * reaches the game.
 */
class ExternalOnScreenKeyboardView(
    context: Context,
    private val xServer: XServer?,
    private val theme: PadTheme = PadTheme.DEFAULT,
    private val localInput: ((LocalKey) -> Unit)? = null,
) : LinearLayout(context) {

    /** What a key produces in local mode. */
    sealed interface LocalKey {
        data class Text(val text: String) : LocalKey
        data object Backspace : LocalKey
        data object Enter : LocalKey
    }

    /** Called after Enter is sent to the game (not in local mode). */
    var onEnter: (() -> Unit)? = null

    private data class KeySpec(
        val normalLabel: String,
        val shiftedLabel: String? = null,
        val keycode: XKeycode? = null,
        val weight: Float = 1f,
        val isLetter: Boolean = false,
        val action: Action = Action.INPUT,
    )

    private enum class Action { INPUT, SHIFT, CAPS, BACKSPACE, ENTER, SPACE, TAB, ESC, ARROW_LEFT, ARROW_DOWN, ARROW_RIGHT, ARROW_UP }

    private data class KeyButton(
        val spec: KeySpec,
        val button: Button,
    )

    private val keyButtons = mutableListOf<KeyButton>()
    private val downKeys = mutableSetOf<XKeycode>()
    /** Shift applies to the next key only, then lets go by itself. */
    private var shiftOn = false
    /** Caps Lock capitalises letters until tapped again; numbers and symbols are unaffected, as on a real keyboard. */
    private var capsOn = false

    init {
        orientation = VERTICAL
        setMotionEventSplittingEnabled(true)
        val padding = dp(8)
        setPadding(padding, padding, padding, padding)
        setBackgroundColor(theme.background)
        buildLayout()
        refreshLabels()
    }

    /**
     * A standard US QWERTY keyboard. Every row adds up to 15 key widths, so the rows line up with the
     * same stagger as a real keyboard. Shifted symbols come from Shift, as on a physical keyboard.
     */
    private fun buildLayout() {
        keyButtons.clear()
        removeAllViews()

        addRow(
            listOf(
                KeySpec("`", "~", XKeycode.KEY_GRAVE),
                KeySpec("1", "!", XKeycode.KEY_1),
                KeySpec("2", "@", XKeycode.KEY_2),
                KeySpec("3", "#", XKeycode.KEY_3),
                KeySpec("4", "$", XKeycode.KEY_4),
                KeySpec("5", "%", XKeycode.KEY_5),
                KeySpec("6", "^", XKeycode.KEY_6),
                KeySpec("7", "&", XKeycode.KEY_7),
                KeySpec("8", "*", XKeycode.KEY_8),
                KeySpec("9", "(", XKeycode.KEY_9),
                KeySpec("0", ")", XKeycode.KEY_0),
                KeySpec("-", "_", XKeycode.KEY_MINUS),
                KeySpec("=", "+", XKeycode.KEY_EQUAL),
                KeySpec("⌫", keycode = XKeycode.KEY_BKSP, weight = 2f, action = Action.BACKSPACE),
            ),
        )

        addRow(
            listOf(
                KeySpec("Tab", keycode = XKeycode.KEY_TAB, weight = 1.5f, action = Action.TAB),
                KeySpec("q", "Q", XKeycode.KEY_Q, isLetter = true),
                KeySpec("w", "W", XKeycode.KEY_W, isLetter = true),
                KeySpec("e", "E", XKeycode.KEY_E, isLetter = true),
                KeySpec("r", "R", XKeycode.KEY_R, isLetter = true),
                KeySpec("t", "T", XKeycode.KEY_T, isLetter = true),
                KeySpec("y", "Y", XKeycode.KEY_Y, isLetter = true),
                KeySpec("u", "U", XKeycode.KEY_U, isLetter = true),
                KeySpec("i", "I", XKeycode.KEY_I, isLetter = true),
                KeySpec("o", "O", XKeycode.KEY_O, isLetter = true),
                KeySpec("p", "P", XKeycode.KEY_P, isLetter = true),
                KeySpec("[", "{", XKeycode.KEY_BRACKET_LEFT),
                KeySpec("]", "}", XKeycode.KEY_BRACKET_RIGHT),
                KeySpec("\\", "|", XKeycode.KEY_BACKSLASH, weight = 1.5f),
            ),
        )

        addRow(
            listOf(
                KeySpec("Caps", weight = 1.75f, action = Action.CAPS),
                KeySpec("a", "A", XKeycode.KEY_A, isLetter = true),
                KeySpec("s", "S", XKeycode.KEY_S, isLetter = true),
                KeySpec("d", "D", XKeycode.KEY_D, isLetter = true),
                KeySpec("f", "F", XKeycode.KEY_F, isLetter = true),
                KeySpec("g", "G", XKeycode.KEY_G, isLetter = true),
                KeySpec("h", "H", XKeycode.KEY_H, isLetter = true),
                KeySpec("j", "J", XKeycode.KEY_J, isLetter = true),
                KeySpec("k", "K", XKeycode.KEY_K, isLetter = true),
                KeySpec("l", "L", XKeycode.KEY_L, isLetter = true),
                KeySpec(";", ":", XKeycode.KEY_SEMICOLON),
                KeySpec("'", "\"", XKeycode.KEY_APOSTROPHE),
                KeySpec("Enter", keycode = XKeycode.KEY_ENTER, weight = 2.25f, action = Action.ENTER),
            ),
        )

        addRow(
            listOf(
                KeySpec("Shift", weight = 2.25f, action = Action.SHIFT),
                KeySpec("z", "Z", XKeycode.KEY_Z, isLetter = true),
                KeySpec("x", "X", XKeycode.KEY_X, isLetter = true),
                KeySpec("c", "C", XKeycode.KEY_C, isLetter = true),
                KeySpec("v", "V", XKeycode.KEY_V, isLetter = true),
                KeySpec("b", "B", XKeycode.KEY_B, isLetter = true),
                KeySpec("n", "N", XKeycode.KEY_N, isLetter = true),
                KeySpec("m", "M", XKeycode.KEY_M, isLetter = true),
                KeySpec(",", "<", XKeycode.KEY_COMMA),
                KeySpec(".", ">", XKeycode.KEY_PERIOD),
                KeySpec("/", "?", XKeycode.KEY_SLASH),
                KeySpec("Shift", weight = 2.75f, action = Action.SHIFT),
            ),
        )

        addRow(
            listOf(
                KeySpec("Esc", keycode = XKeycode.KEY_ESC, weight = 1.5f, action = Action.ESC),
                KeySpec("Space", keycode = XKeycode.KEY_SPACE, weight = 9.5f, action = Action.SPACE),
                KeySpec("←", keycode = XKeycode.KEY_LEFT, action = Action.ARROW_LEFT),
                KeySpec("↓", keycode = XKeycode.KEY_DOWN, action = Action.ARROW_DOWN),
                KeySpec("↑", keycode = XKeycode.KEY_UP, action = Action.ARROW_UP),
                KeySpec("→", keycode = XKeycode.KEY_RIGHT, action = Action.ARROW_RIGHT),
            ),
        )
    }

    private fun addRow(keys: List<KeySpec>) {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }

        val margin = dp(3)
        val height = dp(54)

        keys.forEach { spec ->
            val button = Button(context).apply {
                isAllCaps = false
                setTextColor(theme.text)
                setTextSize(16f)
                typeface = theme.typeface
                text = spec.normalLabel
                background = createKeyBackground(normal = true)
                setPadding(0, 0, 0, 0)
                // Narrow keys (1 unit) are smaller than a Button's default minimum size.
                minWidth = 0
                minimumWidth = 0
                minHeight = 0
                minimumHeight = 0
                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                setOnTouchListener { view, event ->
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                        // Same tap feedback as the pad buttons, following the Haptics setting.
                        PadSettings.haptic(view)
                    }
                    handleKeyTouch(spec, event)
                    false
                }
            }
            keyButtons += KeyButton(spec, button)
            // The gap between keys is padding inside each key's cell rather than a margin, so the width
            // of one key unit is the same on every row and the columns line up like a real keyboard.
            row.addView(
                FrameLayout(context).apply {
                    setPadding(margin, margin, margin, margin)
                    layoutParams = LayoutParams(0, height, spec.weight)
                    addView(button)
                },
            )
        }

        addView(row)
    }

    private fun handleKeyTouch(spec: KeySpec, event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> onKeyDown(spec)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> onKeyUp(spec, cancel = event.actionMasked == MotionEvent.ACTION_CANCEL)
        }
    }

    private fun onKeyDown(spec: KeySpec) {
        localInput?.let { output ->
            onLocalKeyDown(spec, output)
            return
        }
        when (spec.action) {
            Action.SHIFT, Action.CAPS -> Unit
            Action.BACKSPACE -> pressKey(XKeycode.KEY_BKSP)
            Action.ENTER -> pressKey(XKeycode.KEY_ENTER)
            Action.SPACE -> pressKey(XKeycode.KEY_SPACE)
            Action.TAB -> pressKey(XKeycode.KEY_TAB)
            Action.ESC -> pressKey(XKeycode.KEY_ESC)
            Action.ARROW_LEFT -> pressKey(XKeycode.KEY_LEFT)
            Action.ARROW_DOWN -> pressKey(XKeycode.KEY_DOWN)
            Action.ARROW_RIGHT -> pressKey(XKeycode.KEY_RIGHT)
            Action.ARROW_UP -> pressKey(XKeycode.KEY_UP)
            Action.INPUT -> {
                val keycode = spec.keycode ?: return
                pressKey(keycode, withShift = isShifted(spec))
                if (shiftOn) {
                    shiftOn = false
                    refreshLabels()
                }
            }
        }
    }

    private fun onKeyUp(spec: KeySpec, cancel: Boolean) {
        when (spec.action) {
            Action.SHIFT -> if (!cancel) {
                shiftOn = !shiftOn
                refreshLabels()
            }
            Action.CAPS -> if (!cancel) {
                capsOn = !capsOn
                refreshLabels()
            }
            Action.BACKSPACE -> releaseKey(XKeycode.KEY_BKSP)
            Action.ENTER -> if (releaseKey(XKeycode.KEY_ENTER)) onEnter?.invoke()
            Action.SPACE -> releaseKey(XKeycode.KEY_SPACE)
            Action.TAB -> releaseKey(XKeycode.KEY_TAB)
            Action.ESC -> releaseKey(XKeycode.KEY_ESC)
            Action.ARROW_LEFT -> releaseKey(XKeycode.KEY_LEFT)
            Action.ARROW_DOWN -> releaseKey(XKeycode.KEY_DOWN)
            Action.ARROW_RIGHT -> releaseKey(XKeycode.KEY_RIGHT)
            Action.ARROW_UP -> releaseKey(XKeycode.KEY_UP)
            Action.INPUT -> spec.keycode?.let { releaseKey(it) }
        }
    }

    /** Local mode: a key produces its character (or Backspace/Enter) for the pad's own text field. */
    private fun onLocalKeyDown(spec: KeySpec, output: (LocalKey) -> Unit) {
        when (spec.action) {
            Action.INPUT -> {
                output(LocalKey.Text(if (isShifted(spec) && spec.shiftedLabel != null) spec.shiftedLabel else spec.normalLabel))
                if (shiftOn) {
                    shiftOn = false
                    refreshLabels()
                }
            }
            Action.SPACE -> output(LocalKey.Text(" "))
            Action.BACKSPACE -> output(LocalKey.Backspace)
            Action.ENTER -> output(LocalKey.Enter)
            else -> Unit
        }
    }

    /** Letters follow Shift and Caps Lock (Shift with Caps on gives lowercase); everything else only Shift. */
    private fun isShifted(spec: KeySpec): Boolean = if (spec.isLetter) shiftOn != capsOn else shiftOn

    private fun refreshLabels() {
        keyButtons.forEach { (spec, button) ->
            when (spec.action) {
                Action.SHIFT -> button.background = createKeyBackground(normal = !shiftOn, highlight = shiftOn)
                Action.CAPS -> button.background = createKeyBackground(normal = !capsOn, highlight = capsOn, strong = capsOn)
                else -> {
                    button.text = if (isShifted(spec) && spec.shiftedLabel != null) spec.shiftedLabel else spec.normalLabel
                    button.background = createKeyBackground(normal = true)
                }
            }
        }
    }

    private fun pressKey(key: XKeycode, withShift: Boolean = false) {
        val xServer = xServer ?: return
        if (!downKeys.add(key)) return
        val shiftWasDown = xServer.keyboard.modifiersMask.isSet(1)
        if (withShift && !shiftWasDown) xServer.injectKeyPress(XKeycode.KEY_SHIFT_L)
        xServer.injectKeyPress(key)
        if (withShift && !shiftWasDown) xServer.injectKeyRelease(XKeycode.KEY_SHIFT_L)
    }

    /** Returns true if the key was down and has now been released. */
    private fun releaseKey(key: XKeycode): Boolean {
        if (!downKeys.remove(key)) return false
        xServer?.injectKeyRelease(key)
        return true
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDetachedFromWindow() {
        downKeys.toList().forEach { key ->
            xServer?.injectKeyRelease(key)
        }
        downKeys.clear()
        super.onDetachedFromWindow()
    }

    /** Normal keys use the pad's stone face; Shift/Caps use the glowing face, Caps with the bright outline. */
    private fun createKeyBackground(
        normal: Boolean = false,
        highlight: Boolean = false,
        strong: Boolean = false,
    ): StateListDrawable {
        val density = resources.displayMetrics.density
        val face = if (highlight) theme.buttonBackground(density, 8f, active = true, emphasized = strong)
        else theme.buttonBackground(density, 8f, active = false)
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), theme.buttonBackground(density, 8f, active = true))
            addState(intArrayOf(), face)
        }
    }
}
