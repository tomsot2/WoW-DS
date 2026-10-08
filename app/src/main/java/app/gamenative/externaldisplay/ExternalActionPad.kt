package app.gamenative.externaldisplay

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
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
import app.gamenative.data.TouchGestureConfig

/**
 * Whole second-screen UI: a slim header with a trackpad button (left) and a keyboard button (right),
 * and below it either the 12 action buttons or a laptop-style trackpad. The keyboard is the app's own
 * on-screen keyboard, drawn over the bottom of whichever is showing.
 */
class ExternalActionPad(
    context: Context,
    private val xServer: XServer,
    private val theme: PadTheme,
    private val touchpadViewProvider: () -> TouchpadView?,
) : LinearLayout(context) {

    private val density = resources.displayMetrics.density
    private lateinit var trackpadButton: ImageButton
    private lateinit var keyboardButton: ImageButton
    private lateinit var settingsButton: ImageButton
    private lateinit var settingsView: ExternalPadSettingsView
    private lateinit var keyboardView: ExternalOnScreenKeyboardView
    private lateinit var padView: ExternalActionBarView
    private var settingsOpen = false
    private var settingsVersion = 0
    private val heldModifiers = mutableSetOf<XKeycode>()
    private val lockedModifiers = mutableSetOf<XKeycode>()
    // Each modifier can have a button on the pad and one on the trackpad; both follow the same state.
    private val modifierStyles = mutableMapOf<XKeycode, MutableList<(Boolean) -> Unit>>()
    private lateinit var trackpadView: TouchpadView
    private lateinit var trackpadPanel: LinearLayout
    private lateinit var padModifierRow: LinearLayout
    private lateinit var trackpadModifierRow: LinearLayout
    private val releaseMouseButtons = mutableListOf<() -> Unit>()

    // Idle dimming: after a while without a touch the whole pad is darkened (see PadSettings.DIM_IDLE).
    private var dimmed = false
    // True while the touch that woke the pad is still down, so that touch doesn't press anything.
    private var swallowingWakeTouch = false
    private val dimRunnable = Runnable { setDimmed(true) }

    private lateinit var chatButton: ImageButton
    private lateinit var keyboardPanel: LinearLayout
    // True while the keyboard is up because of the Chat button; sending the message then closes it.
    private var openedForChat = false

    private val shiftRunnable: Runnable = Runnable {
        val range = SHIFT_RANGE_DP * density
        shiftContent((Math.random().toFloat() * 2 - 1) * range, (Math.random().toFloat() * 2 - 1) * range)
        scheduleShift()
    }

    init {
        PadSettings.init(context)
        orientation = VERTICAL
        setBackgroundColor(theme.background)
        buildUi()
    }

    /** Builds (or rebuilds, after settings changed) everything below the pad background. */
    private fun buildUi() {
        removeAllViews()
        // Let go of anything still held (including locked modifiers) before the old buttons are dropped.
        heldModifiers.toList().forEach { xServer.injectKeyRelease(it) }
        releaseMouseButtons.forEach { it() }
        heldModifiers.clear()
        lockedModifiers.clear()
        modifierStyles.clear()
        releaseMouseButtons.clear()
        settingsOpen = false

        padView = ExternalActionBarView(
            context,
            xServer,
            theme,
            onKeyTapped = { releaseModifiers() },
            // Two buttons were swapped by dragging: rebuild the pad in the new order.
            onLayoutChanged = { buildUi() },
        ).apply {
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
            setCursorAcceleration(PadSettings.trackpadAcceleration)
            setSensitivity(PadSettings.trackpadSensitivity)
            setPrecisionCursor(true)
            if (!PadSettings.bool(PadSettings.TP_TAP)) {
                setGestureConfig(TouchGestureConfig().copy(tapEnabled = false))
            }
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
        // The keyboard fills the pad below the chat bar, so its keys are as large as the screen allows.
        keyboardView = ExternalOnScreenKeyboardView(context, xServer, theme, fillHeight = true).apply {
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            // Opened with the Chat button: sending the message closes the keyboard again.
            onEnter = { if (openedForChat) setKeyboard(false) }
        }
        // The chat bar (channels and quick phrases) sits on top of the keyboard.
        keyboardPanel = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(theme.background)
            addView(chatBar())
            addView(keyboardView)
            visibility = View.GONE
        }

        trackpadButton = circleButton(R.drawable.icon_trackpad, "Trackpad") { setTrackpad(trackpadPanel.visibility != View.VISIBLE) }
        chatButton = circleButton(R.drawable.icon_chat, "Chat") { openChat() }
        keyboardButton = circleButton(R.drawable.icon_keyboard, "Keyboard") { setKeyboard(keyboardPanel.visibility != View.VISIBLE) }
        settingsButton = circleButton(R.drawable.icon_settings, "Settings") { setSettings(!settingsOpen) }
        settingsView = ExternalPadSettingsView(context, theme).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            visibility = View.GONE
        }

        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            // The header buttons sit on a faint gold backing, like the button groups below.
            background = theme.groupBackground(density, strong = false)
            val side = (8 * density).toInt()
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(side, side / 2, side, 0)
            }
            val inset = (4 * density).toInt()
            setPadding(inset, inset, inset, inset)
            addView(trackpadButton)
            addView(chatButton)
            addView(settingsButton)
            addView(keyboardButton)
        }
        // Modifier buttons on the pad (above the marker buttons) follow the "modifier keys shown" setting.
        // With none showing, the row and its backing stay out of the way.
        padModifierRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            val m = (3 * density).toInt()
            setPadding(0, m, 0, 0)
            if (PadSettings.bool(PadSettings.MOD_SHIFT)) addView(modifierButton("Shift", XKeycode.KEY_SHIFT_L))
            if (PadSettings.bool(PadSettings.MOD_CTRL)) addView(modifierButton("Ctrl", XKeycode.KEY_CTRL_L))
            if (PadSettings.bool(PadSettings.MOD_ALT)) addView(modifierButton("Alt", XKeycode.KEY_ALT_L))
        }
        if (padModifierRow.childCount > 0) padView.setModifierRow(padModifierRow)
        // The trackpad always has all three, above the click buttons, whatever the setting says.
        trackpadModifierRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            val m = (10 * density).toInt()
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (44 * density).toInt()).apply {
                setMargins(m, 0, m, 0)
            }
            addView(modifierButton("Shift", XKeycode.KEY_SHIFT_L))
            addView(modifierButton("Ctrl", XKeycode.KEY_CTRL_L))
            addView(modifierButton("Alt", XKeycode.KEY_ALT_L))
        }
        trackpadPanel.addView(trackpadModifierRow, 1)
        val body = FrameLayout(context).apply {
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            addView(padView)
            addView(trackpadPanel)
            addView(keyboardPanel)
            addView(settingsView)
        }
        addView(header)
        addView(body)
        // The dim time or amount may have just changed in settings.
        setDimmed(false)
        scheduleDim()
        scheduleShift()
    }

    /**
     * Two rows above the keyboard. Channel buttons type the channel's slash command ("/p ") and leave
     * the chat box open for the rest of the message. Phrase buttons send a whole message to the channel
     * picked with the "To" button.
     */
    private fun chatBar(): LinearLayout {
        fun barButton(text: String, onTap: (TextView) -> Unit) = TextView(context).apply {
            this.text = text
            gravity = Gravity.CENTER
            maxLines = 1
            setTextColor(theme.text)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = theme.typeface
            background = theme.buttonStates(density, 8f, muted = true)
            layoutParams = LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                val m = (3 * density).toInt()
                setMargins(m, m, m, m)
            }
            setOnClickListener {
                PadSettings.haptic(it)
                onTap(this)
            }
        }
        fun row() = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (42 * density).toInt())
        }
        val channels = row().apply {
            CHAT_CHANNELS.forEach { (label, command) ->
                addView(barButton(label) { ChatTyper.type(xServer, "$command ", send = false) })
            }
        }
        val phrases = row().apply {
            addView(
                barButton("To: ${PadSettings.phraseChannel.first}") { b ->
                    val next = (PadSettings.int(PadSettings.PHRASE_CHANNEL) + 1) % PadSettings.PHRASE_CHANNELS.size
                    PadSettings.set(PadSettings.PHRASE_CHANNEL, next)
                    b.text = "To: ${PadSettings.phraseChannel.first}"
                }.apply { (layoutParams as LayoutParams).weight = 1.4f },
            )
            PadSettings.actions(PadSettings.PHRASES).forEach { phrase ->
                addView(
                    barButton(phrase.label) {
                        if (phrase.text.isBlank()) return@barButton
                        ChatTyper.type(xServer, "${PadSettings.phraseChannel.second} ${phrase.text}")
                        if (openedForChat) setKeyboard(false)
                    },
                )
            }
        }
        return LinearLayout(context).apply {
            orientation = VERTICAL
            val side = (5 * density).toInt()
            setPadding(side, side, side, 0)
            addView(channels)
            addView(phrases)
        }
    }

    /** The Chat button: opens WoW's chat box (Enter) with the keyboard, or closes the keyboard again. */
    private fun openChat() {
        if (keyboardPanel.visibility == View.VISIBLE) {
            setKeyboard(false)
            return
        }
        releaseModifiers()
        xServer.injectKeyPress(XKeycode.KEY_ENTER)
        xServer.injectKeyRelease(XKeycode.KEY_ENTER)
        setKeyboard(true)
        openedForChat = true
        styleButton(chatButton, true)
    }

    /**
     * Burn-in protection: every few minutes the pad's contents move to a new spot a few pixels away, so no
     * edge sits on the same OLED pixels for hours. It's too small a move to notice.
     */
    private fun scheduleShift() {
        removeCallbacks(shiftRunnable)
        if (PadSettings.bool(PadSettings.BURN_IN_SHIFT)) {
            postDelayed(shiftRunnable, SHIFT_INTERVAL_MS)
        } else {
            shiftContent(0f, 0f)
        }
    }

    /**
     * Moves what's on the pad (the header and everything below it), not the pad itself: the pad's dark
     * background stays put and fills the strip uncovered at the edge. Moving the whole pad would uncover
     * the window behind it, which shows as a white border.
     */
    private fun shiftContent(x: Float, y: Float) {
        for (i in 0 until childCount) {
            getChildAt(i).translationX = x
            getChildAt(i).translationY = y
        }
    }

    /**
     * Every touch on the pad passes through here first. A touch on a dimmed pad only wakes it: the whole
     * gesture is swallowed so the button under the finger isn't pressed by accident. The idle countdown
     * waits while a finger is down, so holding a button never dims the pad.
     */
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                removeCallbacks(dimRunnable)
                swallowingWakeTouch = dimmed
                if (dimmed) setDimmed(false)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> scheduleDim()
        }
        if (swallowingWakeTouch) return true
        return super.dispatchTouchEvent(event)
    }

    private fun scheduleDim() {
        removeCallbacks(dimRunnable)
        val delay = PadSettings.dimIdleMs
        if (delay > 0) postDelayed(dimRunnable, delay)
    }

    /** Darkens everything on the pad with a black layer drawn over it. */
    private fun setDimmed(on: Boolean) {
        dimmed = on
        foreground = if (on) ColorDrawable(Color.argb((PadSettings.dimAmount * 255).toInt(), 0, 0, 0)) else null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        scheduleDim()
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
                background = theme.buttonBackground(density, 12f, active, emphasized = locked, muted = true)
                setTextColor(if (active) theme.textPressed else theme.text)
            }
            style(key in heldModifiers)
            modifierStyles.getOrPut(key) { mutableListOf() }.add { active -> style(active) }
            var lastArmedAt = 0L
            setOnClickListener {
                let { PadSettings.haptic(it) }
                val now = android.os.SystemClock.uptimeMillis()
                when {
                    key !in heldModifiers -> {
                        heldModifiers.add(key)
                        xServer.injectKeyPress(key)
                        lastArmedAt = now
                        style(true)
                    }
                    key !in lockedModifiers && now - lastArmedAt <= PadSettings.doubleTapMs -> {
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
                        view.let { PadSettings.haptic(it) }
                        val now = SystemClock.uptimeMillis()
                        if (locked) {
                            send(false)
                            locked = false
                            pressed = false
                            ignoreUp = true
                        } else {
                            send(true)
                            pressed = true
                            lockOnRelease = now - lastDownAt <= PadSettings.doubleTapMs
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
            modifierStyles[key]?.forEach { it(false) }
        }
    }

    /** Never leave a modifier stuck down if the display goes away. */
    override fun onDetachedFromWindow() {
        removeCallbacks(dimRunnable)
        removeCallbacks(shiftRunnable)
        heldModifiers.toList().forEach { xServer.injectKeyRelease(it) }
        heldModifiers.clear()
        lockedModifiers.clear()
        releaseMouseButtons.forEach { it() }
        super.onDetachedFromWindow()
    }


    /** Trackpad and keyboard are mutually exclusive: opening one closes the other. */
    private fun setTrackpad(on: Boolean) {
        if (on) {
            setKeyboard(false)
            setSettings(false)
        }
        trackpadPanel.visibility = if (on) View.VISIBLE else View.GONE
        if (!on) releaseMouseButtons.forEach { it() }
        padView.visibility = if (on) View.GONE else View.VISIBLE
        // A modifier armed on one view shows as armed on the other too.
        modifierStyles.forEach { (key, styles) -> styles.forEach { it(key in heldModifiers) } }
        styleButton(trackpadButton, on)
    }

    private fun setKeyboard(on: Boolean) {
        if (on) {
            setTrackpad(false)
            setSettings(false)
        }
        keyboardPanel.visibility = if (on) View.VISIBLE else View.GONE
        // The keyboard panel covers the whole pad while it is up.
        padView.visibility = if (on) View.GONE else View.VISIBLE
        openedForChat = false
        styleButton(keyboardButton, on)
        styleButton(chatButton, false)
    }

    /**
     * The settings screen replaces the pad, like the trackpad and keyboard do. Closing it rebuilds the
     * pad if any setting changed, so the new layout, look and trackpad feel apply.
     */
    private fun setSettings(on: Boolean) {
        if (on == settingsOpen) return
        if (on) {
            setTrackpad(false)
            setKeyboard(false)
            settingsVersion = PadSettings.version
            settingsOpen = true
            settingsView.visibility = View.VISIBLE
            padView.visibility = View.GONE
            styleButton(settingsButton, true)
        } else {
            settingsOpen = false
            if (PadSettings.version != settingsVersion) {
                buildUi()
            } else {
                settingsView.visibility = View.GONE
                padView.visibility = View.VISIBLE
                styleButton(settingsButton, false)
            }
        }
    }

    private fun circleButton(@DrawableRes icon: Int, label: String, onClick: () -> Unit): ImageButton {
        return ImageButton(context).apply {
            // The header buttons split the full width between them.
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
        button.background = theme.buttonBackground(density, 12f, active, muted = true)
        button.setColorFilter(if (active) theme.textPressed else theme.text)
    }

    private companion object {
        /** Chat bar channel buttons: label and the slash command typed. */
        val CHAT_CHANNELS = listOf(
            "Say" to "/s", "Party" to "/p", "Inst" to "/i", "Raid" to "/ra", "Guild" to "/g", "Whisper" to "/w", "Reply" to "/r",
        )

        // Burn-in protection: how often the pad moves, and how far it can move from its home position.
        const val SHIFT_INTERVAL_MS = 3 * 60 * 1000L
        const val SHIFT_RANGE_DP = 3f
    }
}
