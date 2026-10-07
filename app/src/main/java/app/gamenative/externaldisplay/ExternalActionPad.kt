package app.gamenative.externaldisplay

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import android.util.TypedValue
import android.widget.TextView
import android.annotation.SuppressLint
import android.view.MotionEvent
import android.os.SystemClock
import com.winlator.widget.TouchpadView
import com.winlator.winhandler.MouseEventFlags
import com.winlator.xserver.Pointer
import com.winlator.xserver.XKeycode
import com.winlator.xserver.XServer
import app.gamenative.R

/**
 * Whole second-screen UI: a slim header with a trackpad button (left) and a keyboard button (right),
 * and below it either the 12 action buttons or a laptop-style trackpad. The keyboard is the app's own
 * on-screen keyboard, drawn over the bottom of whichever is showing.
 */
class ExternalActionPad(
    context: Context,
    private val xServer: XServer,
    private val theme: PadTheme,
    touchpadViewProvider: () -> TouchpadView?,
) : LinearLayout(context) {

    private val density = resources.displayMetrics.density
    private val trackpadButton: ImageButton
    private val keyboardButton: ImageButton
    private val keyboardView: ExternalOnScreenKeyboardView
    private val padView: ExternalActionBarView
    private val heldModifiers = mutableSetOf<XKeycode>()
    private val lockedModifiers = mutableSetOf<XKeycode>()
    private val modifierStyles = mutableMapOf<XKeycode, (Boolean) -> Unit>()
    private val trackpadView: TouchpadView
    private val trackpadPanel: LinearLayout
    private val modifierRow: LinearLayout
    private val releaseMouseButtons = mutableListOf<() -> Unit>()

    init {
        orientation = VERTICAL
        setBackgroundColor(theme.background)

        padView = ExternalActionBarView(context, xServer, theme, onKeyTapped = { releaseModifiers() }).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        trackpadView = TouchpadView(context, xServer, false).apply {
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                val m = (10 * density).toInt()
                setMargins(m, 0, m, m)
            }
            background = theme.buttonBackground(density, 16f, active = false)
            touchpadViewProvider()?.let { setSimTouchScreen(it.isSimTouchScreen) }
            // Slower, steadier cursor for precise aiming: no speed-up on fast swipes, and a lower base speed.
            setCursorAcceleration(TRACKPAD_ACCELERATION)
            setSensitivity(TRACKPAD_SENSITIVITY)
        }
        trackpadPanel = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            addView(trackpadView)
            addView(
                LinearLayout(context).apply {
                    orientation = HORIZONTAL
                    val m = (10 * density).toInt()
                    layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (64 * density).toInt()).apply {
                        setMargins(m, 0, m, m)
                    }
                    addView(mouseButton("Left click", Pointer.Button.BUTTON_LEFT, MouseEventFlags.LEFTDOWN, MouseEventFlags.LEFTUP))
                    addView(mouseButton("Right click", Pointer.Button.BUTTON_RIGHT, MouseEventFlags.RIGHTDOWN, MouseEventFlags.RIGHTUP))
                },
            )
            visibility = View.GONE
        }
        keyboardView = ExternalOnScreenKeyboardView(context, xServer).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { gravity = Gravity.BOTTOM }
            visibility = View.GONE
        }

        trackpadButton = circleButton(R.drawable.icon_trackpad, "Trackpad") { setTrackpad(trackpadPanel.visibility != View.VISIBLE) }
        keyboardButton = circleButton(R.drawable.icon_keyboard, "Keyboard") { setKeyboard(keyboardView.visibility != View.VISIBLE) }

        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            val m = (6 * density).toInt()
            setPadding((8 * density).toInt(), m, (8 * density).toInt(), m)
            addView(trackpadButton)
            addView(keyboardButton)
        }
        // One set of modifier buttons: under the action buttons on the pad, above the click buttons on the trackpad.
        modifierRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            val m = (3 * density).toInt()
            setPadding(0, m, 0, 0)
            addView(modifierButton("Shift", XKeycode.KEY_SHIFT_L))
            addView(modifierButton("Ctrl", XKeycode.KEY_CTRL_L))
            addView(modifierButton("Alt", XKeycode.KEY_ALT_L))
        }
        padView.setModifierRow(modifierRow)
        val body = FrameLayout(context).apply {
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            addView(padView)
            addView(trackpadPanel)
            addView(keyboardView)
        }
        addView(header)
        addView(body)
    }

    /**
     * Modifier button with three states:
     * - tap: armed (lit). It lets go by itself right after the next pad button (1-9, 0, -, =).
     * - double tap: locked (lit with a bright outline). It stays held until you tap it again.
     * - tap while armed or locked: off.
     */
    private fun modifierButton(label: String, key: XKeycode): TextView {
        return TextView(context).apply {
            text = label
            gravity = Gravity.CENTER
            setTextColor(theme.text)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = theme.typeface
            contentDescription = label
            layoutParams = LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                val m = (3 * density).toInt()
                setMargins(m, 0, m, m)
            }
            fun style(active: Boolean) {
                val locked = key in lockedModifiers
                background = theme.buttonBackground(density, 12f, active, emphasized = locked)
                setTextColor(if (active) theme.textPressed else theme.text)
            }
            style(false)
            modifierStyles[key] = { active -> style(active) }
            var lastArmedAt = 0L
            setOnClickListener {
                performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                val now = android.os.SystemClock.uptimeMillis()
                when {
                    key !in heldModifiers -> {
                        heldModifiers.add(key)
                        xServer.injectKeyPress(key)
                        lastArmedAt = now
                        style(true)
                    }
                    key !in lockedModifiers && now - lastArmedAt <= DOUBLE_TAP_MS -> {
                        lockedModifiers.add(key)
                        style(true)
                    }
                    else -> {
                        heldModifiers.remove(key)
                        lockedModifiers.remove(key)
                        xServer.injectKeyRelease(key)
                        style(false)
                    }
                }
            }
        }
    }

    /**
     * Mouse button under the trackpad. It acts like a real button: finger down presses it, finger up
     * releases it, so you can hold and drag. Double-tap and it stays pressed after you lift your finger
     * (lit with a bright outline) until you tap it again.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun mouseButton(label: String, button: Pointer.Button, downFlag: Int, upFlag: Int): TextView {
        return TextView(context).apply {
            text = label
            gravity = Gravity.CENTER
            setTextColor(theme.text)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = theme.typeface
            contentDescription = label
            layoutParams = LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                val m = (4 * density).toInt()
                setMargins(m, 0, m, 0)
            }
            var pressed = false
            var locked = false
            var lockOnRelease = false
            var ignoreUp = false
            var lastDownAt = 0L

            fun style() {
                background = theme.buttonBackground(density, 12f, pressed || locked, emphasized = locked)
                setTextColor(if (pressed || locked) theme.textPressed else theme.text)
            }
            // Same two paths the trackpad itself uses: the Wine mouse in relative mode, X pointer buttons otherwise.
            fun send(down: Boolean) {
                if (xServer.isRelativeMouseMovement()) {
                    xServer.getWinHandler().mouseEvent(if (down) downFlag else upFlag, 0, 0, 0)
                } else if (down) {
                    xServer.injectPointerButtonPress(button)
                } else {
                    xServer.injectPointerButtonRelease(button)
                }
            }
            style()
            releaseMouseButtons.add {
                if (pressed || locked) send(false)
                pressed = false; locked = false; lockOnRelease = false; ignoreUp = false
                style()
            }
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                        val now = SystemClock.uptimeMillis()
                        if (locked) {
                            send(false)
                            locked = false
                            pressed = false
                            ignoreUp = true
                        } else {
                            send(true)
                            pressed = true
                            lockOnRelease = now - lastDownAt <= DOUBLE_TAP_MS
                            lastDownAt = now
                        }
                        style()
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        when {
                            ignoreUp -> ignoreUp = false
                            lockOnRelease && pressed -> {
                                locked = true
                                lockOnRelease = false
                            }
                            pressed -> {
                                send(false)
                                releaseModifiers()
                            }
                        }
                        pressed = false
                        style()
                    }
                }
                true
            }
        }
    }

    /** Lets go of every armed modifier. Locked ones stay down. */
    private fun releaseModifiers() {
        heldModifiers.toList().filter { it !in lockedModifiers }.forEach { key ->
            heldModifiers.remove(key)
            xServer.injectKeyRelease(key)
            modifierStyles[key]?.invoke(false)
        }
    }

    /** Never leave a modifier stuck down if the display goes away. */
    override fun onDetachedFromWindow() {
        heldModifiers.toList().forEach { xServer.injectKeyRelease(it) }
        heldModifiers.clear()
        lockedModifiers.clear()
        releaseMouseButtons.forEach { it() }
        super.onDetachedFromWindow()
    }

    private companion object {
        const val DOUBLE_TAP_MS = 350L

        // Trackpad feel. Lower sensitivity = slower, more precise. Acceleration 1.0 = none.
        const val TRACKPAD_SENSITIVITY = 0.7f
        const val TRACKPAD_ACCELERATION = 1.0f
    }

    /** Trackpad and keyboard are mutually exclusive: opening one closes the other. */
    private fun setTrackpad(on: Boolean) {
        if (on) setKeyboard(false)
        trackpadPanel.visibility = if (on) View.VISIBLE else View.GONE
        if (!on) releaseMouseButtons.forEach { it() }
        padView.visibility = if (on) View.GONE else View.VISIBLE
        // The modifiers follow whichever view is showing.
        (modifierRow.parent as? ViewGroup)?.removeView(modifierRow)
        if (on) {
            val m = (10 * density).toInt()
            modifierRow.layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (44 * density).toInt()).apply {
                setMargins(m, 0, m, 0)
            }
            trackpadPanel.addView(modifierRow, 1)
        } else {
            padView.setModifierRow(modifierRow)
        }
        styleButton(trackpadButton, on)
    }

    private fun setKeyboard(on: Boolean) {
        if (on) setTrackpad(false)
        keyboardView.visibility = if (on) View.VISIBLE else View.GONE
        // With the keyboard up, the space above it is left blank (just the pad background).
        padView.visibility = if (on) View.GONE else View.VISIBLE
        styleButton(keyboardButton, on)
    }

    private fun circleButton(@DrawableRes icon: Int, label: String, onClick: () -> Unit): ImageButton {
        return ImageButton(context).apply {
            // The two header buttons split the full width between them.
            layoutParams = LayoutParams(0, (44 * density).toInt(), 1f).apply {
                val m = (3 * density).toInt()
                setMargins(m, 0, m, 0)
            }
            setImageResource(icon)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            val pad = (8 * density).toInt()
            setPadding(pad, pad, pad, pad)
            contentDescription = label
            setOnClickListener { onClick() }
            styleButton(this, false)
        }
    }

    private fun styleButton(button: ImageButton, active: Boolean) {
        button.background = theme.buttonBackground(density, 12f, active)
        button.setColorFilter(if (active) theme.textPressed else theme.text)
    }
}
