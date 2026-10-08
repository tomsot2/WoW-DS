package app.gamenative.externaldisplay

import android.content.Context
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
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
    /** Stretch the rows to fill the height the keyboard is given, instead of a fixed key height. */
    private val fillHeight: Boolean = false,
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

    // GAP is an empty space in a row, used for the stagger in the wide layout.
    private enum class Action { INPUT, SHIFT, CAPS, BACKSPACE, ENTER, SPACE, TAB, ESC, ARROW_LEFT, ARROW_DOWN, ARROW_RIGHT, ARROW_UP, GAP }

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

    private fun buildLayout() {
        keyButtons.clear()
        removeAllViews()
        if (PadSettings.int(PadSettings.KEYBOARD_LAYOUT) == 1) buildStandardLayout() else buildWideLayout()
    }

    // Keys shared by both layouts.
    private fun letter(c: Char) = KeySpec(c.toString(), c.uppercase(), XKeycode.valueOf("KEY_${c.uppercaseChar()}"), isLetter = true)
    private fun letters(chars: String) = chars.map { letter(it) }
    private fun gap(weight: Float) = KeySpec("", weight = weight, action = Action.GAP)
    private val numberKeys get() = listOf(
        KeySpec("1", "!", XKeycode.KEY_1), KeySpec("2", "@", XKeycode.KEY_2), KeySpec("3", "#", XKeycode.KEY_3),
        KeySpec("4", "$", XKeycode.KEY_4), KeySpec("5", "%", XKeycode.KEY_5), KeySpec("6", "^", XKeycode.KEY_6),
        KeySpec("7", "&", XKeycode.KEY_7), KeySpec("8", "*", XKeycode.KEY_8), KeySpec("9", "(", XKeycode.KEY_9),
        KeySpec("0", ")", XKeycode.KEY_0),
    )
    private val grave get() = KeySpec("`", "~", XKeycode.KEY_GRAVE)
    private val minus get() = KeySpec("-", "_", XKeycode.KEY_MINUS)
    private val equal get() = KeySpec("=", "+", XKeycode.KEY_EQUAL)
    private val bracketLeft get() = KeySpec("[", "{", XKeycode.KEY_BRACKET_LEFT)
    private val bracketRight get() = KeySpec("]", "}", XKeycode.KEY_BRACKET_RIGHT)
    private val semicolon get() = KeySpec(";", ":", XKeycode.KEY_SEMICOLON)
    private val apostrophe get() = KeySpec("'", "\"", XKeycode.KEY_APOSTROPHE)
    private val zRowPunctuation get() = listOf(
        KeySpec(",", "<", XKeycode.KEY_COMMA), KeySpec(".", ">", XKeycode.KEY_PERIOD), KeySpec("/", "?", XKeycode.KEY_SLASH),
    )
    private fun backslash(weight: Float = 1f) = KeySpec("\\", "|", XKeycode.KEY_BACKSLASH, weight = weight)
    private fun backspace(weight: Float) = KeySpec("⌫", keycode = XKeycode.KEY_BKSP, weight = weight, action = Action.BACKSPACE)
    private fun tab(weight: Float) = KeySpec("Tab", keycode = XKeycode.KEY_TAB, weight = weight, action = Action.TAB)
    private fun caps(weight: Float) = KeySpec("Caps", weight = weight, action = Action.CAPS)
    private fun shift(weight: Float) = KeySpec("Shift", weight = weight, action = Action.SHIFT)
    private fun enter(weight: Float) = KeySpec("Enter", keycode = XKeycode.KEY_ENTER, weight = weight, action = Action.ENTER)
    private fun esc(weight: Float) = KeySpec("Esc", keycode = XKeycode.KEY_ESC, weight = weight, action = Action.ESC)
    private fun space(weight: Float) = KeySpec("Space", keycode = XKeycode.KEY_SPACE, weight = weight, action = Action.SPACE)
    private fun arrows(weight: Float = 1f) = listOf(
        KeySpec("←", keycode = XKeycode.KEY_LEFT, weight = weight, action = Action.ARROW_LEFT),
        KeySpec("↓", keycode = XKeycode.KEY_DOWN, weight = weight, action = Action.ARROW_DOWN),
        KeySpec("↑", keycode = XKeycode.KEY_UP, weight = weight, action = Action.ARROW_UP),
        KeySpec("→", keycode = XKeycode.KEY_RIGHT, weight = weight, action = Action.ARROW_RIGHT),
    )

    /**
     * "Wide letters", for narrow screens like the Thor's bottom one. The letter rows keep a real
     * keyboard's stagger (A starts a quarter key in, Z three quarters) but drop the side keys, so each
     * letter row is 10.75 keys wide instead of 15 and the letters come out about 40% wider. The keys that
     * used to sit at the sides get rows of their own above and below.
     */
    private fun buildWideLayout() {
        addRow(listOf(grave, minus, equal, bracketLeft, bracketRight, backslash(), apostrophe, backspace(2.75f)))
        addRow(numberKeys)
        addRow(letters("qwertyuiop") + gap(0.75f))
        addRow(listOf(gap(0.25f)) + letters("asdfghjkl") + semicolon + gap(0.5f))
        addRow(listOf(gap(0.75f)) + letters("zxcvbnm") + zRowPunctuation)
        addRow(listOf(shift(2f), caps(1.75f), space(4.75f), enter(2.25f)))
        addRow(listOf(esc(1.5f), tab(1.5f)) + arrows(1.9375f))
    }

    /**
     * A standard US QWERTY keyboard. Every row adds up to 15 key widths, so the rows line up with the
     * same stagger as a real keyboard. Shifted symbols come from Shift, as on a physical keyboard.
     */
    private fun buildStandardLayout() {
        addRow(listOf(grave) + numberKeys + minus + equal + backspace(2f))
        addRow(listOf(tab(1.5f)) + letters("qwertyuiop") + bracketLeft + bracketRight + backslash(1.5f))
        addRow(listOf(caps(1.75f)) + letters("asdfghjkl") + semicolon + apostrophe + enter(2.25f))
        addRow(listOf(shift(2.25f)) + letters("zxcvbnm") + zRowPunctuation + shift(2.75f))
        addRow(listOf(esc(1.5f), space(9.5f)) + arrows())
    }

    private fun addRow(keys: List<KeySpec>) {
        // With fillHeight the rows share whatever height the keyboard is given; otherwise each is a fixed height.
        val height = if (fillHeight) ViewGroup.LayoutParams.MATCH_PARENT else dp(54)
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = if (fillHeight) {
                LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            } else {
                LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
        }

        val margin = dp(3)

        keys.forEach { spec ->
            if (spec.action == Action.GAP) {
                row.addView(View(context), LayoutParams(0, height, spec.weight))
                return@forEach
            }
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
            Action.SHIFT, Action.CAPS, Action.GAP -> Unit
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
            Action.GAP -> Unit
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
