package app.gamenative.externaldisplay

import android.annotation.SuppressLint
import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.graphics.drawable.GradientDrawable
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.LinearLayout
import android.widget.TextView
import app.gamenative.ui.screen.wow.WowFlavor
import com.winlator.xserver.XKeycode
import com.winlator.xserver.XServer

/**
 * The main second-screen pad.
 *
 * Left half: a column of window shortcuts (Map, Character, Spellbook, Talents, Skills, Quest Log,
 * Social, System).
 * Right half: the modifier buttons (see [setModifierRow]) on top; below them the 8 target marker
 * buttons and the 8 command buttons, with a party column beside them (Me, P1-P4) that sends F1-F5 to
 * target yourself or a party member. The action bars themselves live on the controller (WoW's gamepad mode).
 *
 * Window and party buttons send WoW's default keys, so they work with the stock bindings and need no
 * addon. Marker and command buttons are editable [PadAction]s: a chat command such as "/tm 8", or a key
 * for an in-game macro.
 *
 * One touch is exactly one key press, or one typed line. Nothing repeats or runs on a timer: anything
 * multi-step belongs in WoW's own macro system.
 */
class ExternalActionBarView(
    context: Context,
    private val xServer: XServer,
    private val theme: PadTheme,
    /** Called after a button's key has been released, e.g. so one-shot modifiers can let go. */
    private val onKeyTapped: () -> Unit = {},
    /** Called after two buttons were swapped by dragging, so the pad can be rebuilt in the new order. */
    private val onLayoutChanged: () -> Unit = {},
) : LinearLayout(context) {

    /** [hiddenIn]: flavors whose client has no such window, so the button is never shown there. */
    private data class Slot(val label: String, val key: XKeycode, val hiddenIn: Set<WowFlavor> = emptySet())

    private val downKeys = mutableSetOf<XKeycode>()
    /** Modifiers held by key-type command buttons. */
    private val downModifiers = mutableSetOf<XKeycode>()
    private val rightColumn: LinearLayout
    // Buttons that can be dragged onto each other, per order key: (id, view).
    private val dropTargets = mutableMapOf<String, MutableList<Pair<String, View>>>()
    private val modifierGroup: LinearLayout

    /** A backing panel that hides itself while it has nothing in it (e.g. the modifiers moved to the trackpad). */
    private class Group(context: Context) : LinearLayout(context) {
        override fun onViewAdded(child: View?) {
            super.onViewAdded(child)
            visibility = VISIBLE
        }

        override fun onViewRemoved(child: View?) {
            super.onViewRemoved(child)
            if (childCount == 0) visibility = GONE
        }
    }

    init {
        orientation = HORIZONTAL
        isMotionEventSplittingEnabled = true
        setBackgroundColor(theme.background)
        val pad = dp(8)
        setPadding(pad, pad, pad, pad)

        // Each group of buttons sits on its own faint backing; the command block's is the strongest.
        // Window buttons: one column up to SINGLE_COLUMN_MAX buttons, two columns beyond that.
        val windows = visibleWindows()
        val columns = if (windows.size > SINGLE_COLUMN_MAX) windows.chunked((windows.size + 1) / 2) else listOf(windows)
        val split = columns.size > 1
        val leftColumn = LinearLayout(context).apply {
            orientation = HORIZONTAL
            isMotionEventSplittingEnabled = true
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, if (split) SPLIT_LEFT_WEIGHT else LEFT_WEIGHT)
            columns.forEach { slots ->
                addView(
                    group(vertical = true, strong = false).apply {
                        layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) }
                        slots.forEach { addView(actionRow(listOf(windowButton(it, textSp = if (split) 13f else 15f)))) }
                    },
                )
            }
            if (windows.isEmpty() || !PadSettings.bool(PadSettings.SEC_WINDOWS)) visibility = GONE
        }
        modifierGroup = group(vertical = false, strong = false).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 0.7f).apply { setMargins(0, dp(3), 0, dp(3)) }
            visibility = GONE
        }
        val markerGroup = group(vertical = true, strong = false).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1.8f).apply { setMargins(0, dp(3), 0, dp(3)) }
            val markers = PadSettings.actions(PadSettings.MARKERS)
            PadSettings.displayOrder(PadSettings.MARKERS).chunked(4).forEach { row ->
                addView(actionRow(row.map { i -> markerButton(i, markers[i]) }))
            }
            if (!PadSettings.bool(PadSettings.SEC_MARKERS)) visibility = GONE
        }
        val commandGroup = group(vertical = true, strong = true).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 2.2f).apply { setMargins(0, dp(3), 0, dp(3)) }
            val commands = PadSettings.actions(PadSettings.COMMANDS)
            PadSettings.displayOrder(PadSettings.COMMANDS).chunked(4).forEach { row ->
                addView(actionRow(row.map { i -> commandButton(i, commands[i]) }))
            }
            if (!PadSettings.bool(PadSettings.SEC_COMMANDS)) visibility = GONE
        }
        // Party buttons stack top to bottom (Me, then P1-P4, like the party frames) in a narrow column
        // beside the markers and commands, so each button is wide and short rather than tall and thin.
        val partyGroup = group(vertical = true, strong = false).apply {
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, PARTY_WEIGHT).apply { setMargins(dp(6), dp(3), 0, dp(3)) }
            PARTY_SLOTS.forEach { addView(keyRow(listOf(it), textSp = 16f, weight = 1f, muted = true)) }
            if (!PadSettings.bool(PadSettings.SEC_PARTY)) visibility = GONE
        }
        val actionColumn = LinearLayout(context).apply {
            orientation = VERTICAL
            isMotionEventSplittingEnabled = true
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
            addView(markerGroup)
            addView(commandGroup)
            if (markerGroup.visibility == GONE && commandGroup.visibility == GONE) visibility = GONE
        }
        val lowerRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            isMotionEventSplittingEnabled = true
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 4f)
            addView(actionColumn)
            addView(partyGroup)
        }
        rightColumn = column(RIGHT_WEIGHT).apply {
            addView(modifierGroup)
            addView(lowerRow)
        }
        // "Swap sides" puts the window column on the right.
        if (PadSettings.bool(PadSettings.SWAP_SIDES)) {
            addView(rightColumn)
            addView(leftColumn)
        } else {
            addView(leftColumn)
            addView(rightColumn)
        }
    }

    /** The window buttons to show, in the order the player arranged them: switched on in settings and present in the selected client. */
    private fun visibleWindows(): List<Slot> = PadSettings.order(PadSettings.ORDER_WINDOWS)
        .mapNotNull { label -> PANELS.find { it.label == label } }
        .filter { slot ->
            PadSettings.bool(PadSettings.windowKey(slot.label)) && WowFlavor.current !in slot.hiddenIn
        }

    /** Puts the modifier buttons above the action buttons. */
    fun setModifierRow(row: View) {
        (row.parent as? android.view.ViewGroup)?.removeView(row)
        row.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        modifierGroup.addView(row)
    }

    private fun group(vertical: Boolean, strong: Boolean): LinearLayout = Group(context).apply {
        orientation = if (vertical) VERTICAL else HORIZONTAL
        isMotionEventSplittingEnabled = true
        background = theme.groupBackground(resources.displayMetrics.density, strong)
        setPadding(dp(4), dp(4), dp(4), dp(4))
    }

    private fun column(weight: Float) = LinearLayout(context).apply {
        orientation = VERTICAL
        isMotionEventSplittingEnabled = true
        layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, weight)
    }

    /** [muted] dims the button trim. */
    private fun keyRow(slots: List<Slot>, textSp: Float, weight: Float, muted: Boolean = false): LinearLayout =
        actionRow(slots.map { createButton(it, textSp, muted) }, weight)

    /** A row of buttons sharing the group's height by weight, so everything always fits. */
    private fun actionRow(buttons: List<View>, weight: Float = 1f): LinearLayout = LinearLayout(context).apply {
        orientation = HORIZONTAL
        isMotionEventSplittingEnabled = true
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, weight)
        buttons.forEach { addView(it) }
    }

    private fun label(text: String, textSp: Float, muted: Boolean, raised: Boolean = false) = TextView(context).apply {
        this.text = text
        contentDescription = text
        gravity = Gravity.CENTER
        setTextColor(theme.text)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, textSp * PadSettings.labelScale)
        typeface = theme.typeface
        maxLines = 2
        background = createKeyBackground(muted, raised)
        layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply {
            val margin = dp(3)
            setMargins(margin, margin, margin, margin)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createButton(slot: Slot, textSp: Float, muted: Boolean): View =
        label(slot.label, textSp, muted).apply {
            maxLines = 1
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        view.isPressed = true
                        PadSettings.haptic(view)
                        pressKey(keyFor(slot))
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        view.isPressed = false
                        releaseKey(keyFor(slot))
                    }
                }
                true
            }
        }

    /** A window shortcut. It can be dragged onto another window button to swap places. */
    private fun windowButton(slot: Slot, textSp: Float): View = label(slot.label, textSp, muted = true).apply {
        maxLines = 1
        makeMovable(PadSettings.ORDER_WINDOWS, slot.label) {
            val key = keyFor(slot)
            pressKey(key)
            releaseKey(key)
        }
    }

    /** A target marker button: the marker's symbol in its color. */
    private fun markerButton(index: Int, action: PadAction): View {
        val (symbol, name, color) = PadSettings.MARKER_STYLES[index]
        return actionButton(PadSettings.ORDER_MARKERS, index, action, textSp = 22f, muted = true).apply {
            text = symbol
            contentDescription = action.label.ifBlank { name }
            setTextColor(color)
        }
    }

    private fun commandButton(index: Int, action: PadAction): View =
        actionButton(PadSettings.ORDER_COMMANDS, index, action, textSp = 13f, muted = false, raised = PadSettings.emphasis > 0f)

    /** A button that runs a [PadAction] when tapped: one key press (with its modifiers) or one typed chat command. */
    private fun actionButton(orderKey: String, index: Int, action: PadAction, textSp: Float, muted: Boolean, raised: Boolean = false): TextView =
        label(action.label, textSp, muted, raised).apply {
            val keycode = action.keycode
            val modifiers = action.modifiers.mapNotNull { PadAction.MODIFIER_KEYS[it] }
            makeMovable(orderKey, index.toString()) {
                if (action.isKey && keycode != null) {
                    modifiers.forEach { if (downModifiers.add(it)) xServer.injectKeyPress(it) }
                    pressKey(keycode)
                    releaseKey(keycode)
                    modifiers.reversed().forEach { if (downModifiers.remove(it)) xServer.injectKeyRelease(it) }
                } else if (action.text.isNotBlank()) {
                    // Armed one-shot modifiers would otherwise turn the typed text into capitals or shortcuts.
                    onKeyTapped()
                    ChatTyper.type(xServer, action.text)
                }
            }
        }

    /**
     * Tap to use, long-press to move. A tap runs [onTap] when the finger lifts on the button (sliding off
     * cancels it). Holding still for [LONG_PRESS_MS] picks the button up instead: drag it onto another
     * button of the same kind ([orderKey]) and let go, and the two swap places. The order is saved and
     * the pad is rebuilt.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun View.makeMovable(orderKey: String, id: String, onTap: () -> Unit) {
        dropTargets.getOrPut(orderKey) { mutableListOf() } += id to this
        val slop = ViewConfiguration.get(context).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        var dragging = false
        var target: Pair<String, View>? = null
        val pickUp = Runnable {
            dragging = true
            isPressed = false
            alpha = 0.45f
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
        fun endDrag() {
            removeCallbacks(pickUp)
            alpha = 1f
            target?.second?.foreground = null
        }
        setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    dragging = false
                    target = null
                    view.isPressed = true
                    PadSettings.haptic(view)
                    view.postDelayed(pickUp, LONG_PRESS_MS)
                }
                MotionEvent.ACTION_MOVE -> if (dragging) {
                    val over = dropTargetAt(orderKey, event.rawX, event.rawY, exclude = view)
                    if (over != target) {
                        target?.second?.foreground = null
                        over?.second?.foreground = dropHighlight()
                        target = over
                    }
                } else if (Math.hypot((event.x - downX).toDouble(), (event.y - downY).toDouble()) > slop) {
                    // Moved before the long press: not a pick-up. Lifting outside the button cancels the tap.
                    view.removeCallbacks(pickUp)
                    view.isPressed = event.x >= 0 && event.y >= 0 && event.x <= view.width && event.y <= view.height
                }
                MotionEvent.ACTION_UP -> {
                    val inside = event.x >= 0 && event.y >= 0 && event.x <= view.width && event.y <= view.height
                    view.isPressed = false
                    val dropOn = target
                    endDrag()
                    if (dragging) {
                        if (dropOn != null) {
                            PadSettings.swap(orderKey, id, dropOn.first)
                            // Rebuilt after this touch has finished, since this button is about to be replaced.
                            this@ExternalActionBarView.post { onLayoutChanged() }
                        }
                    } else if (inside) {
                        onTap()
                    }
                    dragging = false
                }
                MotionEvent.ACTION_CANCEL -> {
                    view.isPressed = false
                    endDrag()
                    dragging = false
                }
            }
            true
        }
    }

    /** The movable button of the same kind under a screen position, if any. */
    private fun dropTargetAt(orderKey: String, rawX: Float, rawY: Float, exclude: View): Pair<String, View>? {
        val location = IntArray(2)
        return dropTargets[orderKey].orEmpty().firstOrNull { (_, v) ->
            if (v === exclude || !v.isShown) return@firstOrNull false
            v.getLocationOnScreen(location)
            rawX >= location[0] && rawX < location[0] + v.width && rawY >= location[1] && rawY < location[1] + v.height
        }
    }

    /** A bright outline over the button a dragged one would swap with. */
    private fun dropHighlight() = GradientDrawable().apply {
        cornerRadius = dp(10).toFloat()
        setColor(0x33FFFFFF)
        setStroke(dp(3), theme.borderBright)
    }

    /** The key a button sends: the user's remap if there is one, otherwise the default. */
    private fun keyFor(slot: Slot): XKeycode =
        PadSettings.remapped(slot.label)?.let { name -> runCatching { XKeycode.valueOf(name) }.getOrNull() } ?: slot.key

    private fun pressKey(key: XKeycode) {
        if (!downKeys.add(key)) return
        xServer.injectKeyPress(key)
    }

    private fun releaseKey(key: XKeycode) {
        if (!downKeys.remove(key)) return
        xServer.injectKeyRelease(key)
        onKeyTapped()
    }

    private fun createKeyBackground(muted: Boolean, raised: Boolean) =
        theme.buttonStates(resources.displayMetrics.density, 10f, muted, raised)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    /** Never leave a key stuck down if the display goes away or the game closes mid-press. */
    override fun onDetachedFromWindow() {
        downKeys.toList().forEach { xServer.injectKeyRelease(it) }
        downModifiers.forEach { xServer.injectKeyRelease(it) }
        downModifiers.clear()
        downKeys.clear()
        super.onDetachedFromWindow()
    }

    companion object {
        /** Every remappable pad button: its label and the key it sends by default. */
        /** Window buttons that exist in the selected client, for the "Window buttons shown" settings. */
        fun availableWindowLabels(): List<String> =
            PANELS.filter { WowFlavor.current !in it.hiddenIn }.map { it.label }

        fun remappableButtons(): List<Pair<String, XKeycode>> =
            (PANELS + PARTY_SLOTS).map { it.label to it.key }

        // Width split between the window-shortcut column and the F-key/number/modifier block.
        // A smaller RIGHT_WEIGHT squeezes that block toward the right edge, within reach of a right thumb.
        private const val LEFT_WEIGHT = 1f
        private const val RIGHT_WEIGHT = 1f

        // Width of the party column, relative to the marker/command block beside it (1).
        private const val PARTY_WEIGHT = 0.3f

        // How long a button is held before it is picked up to be moved.
        private const val LONG_PRESS_MS = 450L

        // Up to this many window buttons stay in one column; more split it in two, a little wider overall.
        private const val SINGLE_COLUMN_MAX = 8
        private const val SPLIT_LEFT_WEIGHT = 1.45f

        // WoW's default bindings for the character, spellbook and similar windows.
        private val PANELS = listOf(
            Slot("Map", XKeycode.KEY_M),
            Slot("Character", XKeycode.KEY_C),
            Slot("Spellbook", XKeycode.KEY_P),
            Slot("Talents", XKeycode.KEY_N),
            Slot("Skills", XKeycode.KEY_K),
            Slot("Quest Log", XKeycode.KEY_L),
            Slot("Social", XKeycode.KEY_O),
            // Escape opens the game menu ("System") when nothing else is open.
            Slot("System", XKeycode.KEY_ESC),
            // Windows that Classic Era's client doesn't have.
            Slot("Bags", XKeycode.KEY_B),
            Slot("Group Finder", XKeycode.KEY_I, hiddenIn = setOf(WowFlavor.CLASSIC_ERA)),
            Slot("Achievements", XKeycode.KEY_Y, hiddenIn = setOf(WowFlavor.CLASSIC_ERA)),
            Slot("Guild", XKeycode.KEY_J, hiddenIn = setOf(WowFlavor.CLASSIC_ERA)),
        )

        // WoW's default party targeting: F1 targets yourself, F2-F5 party members 1-4.
        private val PARTY_SLOTS = listOf(
            Slot("Me", XKeycode.KEY_F1),
            Slot("P1", XKeycode.KEY_F2),
            Slot("P2", XKeycode.KEY_F3),
            Slot("P3", XKeycode.KEY_F4),
            Slot("P4", XKeycode.KEY_F5),
        )
    }
}
