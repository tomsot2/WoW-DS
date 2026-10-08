package app.gamenative.externaldisplay

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.ColorStateList
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import app.gamenative.ui.screen.wow.WowFlavor
import com.winlator.xserver.XKeycode
import org.json.JSONObject

/**
 * The settings screen of the second-screen pad. Everything is written to [PadSettings] as soon as it
 * changes. Layout and look changes show up on the pad itself when this screen is closed.
 *
 * Built in code like the rest of the pad, with the same gold-on-dark look.
 */
class ExternalPadSettingsView(
    context: Context,
    private val theme: PadTheme,
) : ScrollView(context) {

    private val density = resources.displayMetrics.density
    private val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    init {
        setBackgroundColor(theme.background)
        isFillViewport = false
        val side = dp(8)
        content.setPadding(side, dp(4), side, side)
        addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        showMain()
    }

    // Screens

    private fun showMain() {
        content.removeAllViews()
        scrollTo(0, 0)

        section("Modifier keys shown") {
            addView(toggleRow(listOf("Shift" to PadSettings.MOD_SHIFT, "Ctrl" to PadSettings.MOD_CTRL, "Alt" to PadSettings.MOD_ALT)))
        }
        section("Window buttons shown") {
            addView(note("Up to 8 stay in one column. More split it in two."))
            ExternalActionBarView.availableWindowLabels().chunked(3).forEach { chunk ->
                addView(
                    rowOf(
                        *chunk.map { label ->
                            button(label, active = PadSettings.bool(PadSettings.windowKey(label))) { b ->
                                val on = !PadSettings.bool(PadSettings.windowKey(label))
                                PadSettings.set(PadSettings.windowKey(label), on)
                                style(b, on)
                            }
                        }.toTypedArray(),
                        filler = 3 - chunk.size,
                    ),
                )
            }
        }
        section("Pad layout") {
            addView(note("Turn groups on or off, and move them around the pad."))
            addView(rowOf(button("Edit layout") { showLayoutEditor(PadSettings.G_PARTY) }))
            addView(
                toggleRow(
                    listOf(
                        "Windows" to PadSettings.SEC_WINDOWS,
                        "Markers" to PadSettings.SEC_MARKERS,
                        "Commands" to PadSettings.SEC_COMMANDS,
                        "Party" to PadSettings.SEC_PARTY,
                    ),
                ),
            )
            addView(note("To move a window, marker or command button, hold it until it buzzes, then drag it onto another button of the same kind to swap them."))
            addView(rowOf(button("Reset button positions") { b ->
                PadSettings.resetOrders()
                b.text = "Positions reset"
            }))
        }
        section("Buttons and phrases") {
            addView(
                note(
                    "Each button types a chat command (like /readycheck) or presses a key you've bound to an " +
                        "in-game macro. One tap is one command or one key press.",
                ),
            )
            addView(
                rowOf(
                    button("Markers") { showActionList(PadSettings.MARKERS) },
                    button("Commands") { showActionList(PadSettings.COMMANDS) },
                    button("Quick phrases") { showActionList(PadSettings.PHRASES) },
                ),
            )
        }
        section("Look and feel") {
            addView(sliderRow("Muted borders", PadSettings.MUTED, 0, 100) { "$it" })
            addView(sliderRow("Command emphasis", PadSettings.EMPHASIS, 0, 200) { "$it%" })
            addView(sliderRow("Label size", PadSettings.LABEL_SCALE, 80, 140) { "$it%" })
            addView(cycleRow("Haptics", PadSettings.HAPTICS, listOf("Off", "Light", "Normal", "Strong")))
            addView(cycleRow("Keyboard layout", PadSettings.KEYBOARD_LAYOUT, listOf("Wide letters", "Standard")))
            addView(note("Wide letters moves Tab, Caps, Shift, Enter, Backspace and the symbol keys into rows of their own, so the letters get the full width."))
            addView(sliderRow("Double-tap lock", PadSettings.DOUBLE_TAP_MS, 200, 600) { "$it ms" })
            addView(cycleRow("Dim when idle", PadSettings.DIM_IDLE, listOf("Off", "After 15 s", "After 30 s", "After 1 min", "After 2 min")))
            addView(sliderRow("Dim amount", PadSettings.DIM_AMOUNT, 50, 95) { "$it%" })
            addView(note("When dimmed, the first tap only wakes the pad and doesn't press anything."))
            addView(toggleRow(listOf("Burn-in protection (move the pad slightly every few minutes)" to PadSettings.BURN_IN_SHIFT)))
        }
        section("Trackpad") {
            addView(sliderRow("Speed", PadSettings.TP_SPEED, 1, 20) { tenths(it) })
            addView(sliderRow("Acceleration", PadSettings.TP_ACCEL, 10, 30) { tenths(it) })
            addView(toggleRow(listOf("Tap to click" to PadSettings.TP_TAP)))
        }
        section("Controller") {
            addView(sliderRow("Stick deadzone", PadSettings.STICK_DEADZONE, 5, 40) { "$it%" })
        }
        section("Game and app") {
            addView(flavorRow())
        }
        section("Remap buttons") {
            addView(rowOf(button("Choose a button to remap") { showRemap() }))
        }
        section("Profiles") {
            val status = note("")
            for (slot in 1..PadSettings.PROFILE_SLOTS) {
                addView(profileRow(slot, status))
            }
            addView(status)
        }
        section("Backup") {
            val status = note("")
            addView(
                rowOf(
                    button("Copy settings") {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("pad settings", PadSettings.snapshot().toString()))
                        status.text = "Settings copied to the clipboard"
                    },
                    button("Paste settings") {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val text = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
                        val json = text?.let { runCatching { JSONObject(it) }.getOrNull() }
                        if (json == null) {
                            status.text = "The clipboard has no copied settings"
                        } else {
                            PadSettings.applySnapshot(json)
                            showMain()
                        }
                    },
                ),
            )
            addView(status)
        }
        section("Reset") {
            addView(
                rowOf(
                    button("Reset to defaults") {
                        PadSettings.resetAll()
                        showMain()
                    },
                ),
            )
        }
    }

    private fun showRemap() {
        content.removeAllViews()
        scrollTo(0, 0)
        section("Remap buttons") {
            addView(note("Tap a pad button, then choose the key it should send."))
            addView(
                rowOf(
                    button("Back") { showMain() },
                    button("Reset all remaps") {
                        PadSettings.clearRemaps()
                        showRemap()
                    },
                ),
            )
        }
        val buttons = ExternalActionBarView.remappableButtons()
        section("Pad buttons") {
            buttons.chunked(4).forEach { chunk ->
                addView(
                    rowOf(
                        *chunk.map { (label, default) ->
                            val mapped = PadSettings.remapped(label)
                            button(if (mapped == null) label else "$label → ${keyName(mapped)}", active = mapped != null) {
                                showKeyPicker(label, default)
                            }
                        }.toTypedArray(),
                        filler = 4 - chunk.size,
                    ),
                )
            }
        }
    }

    private fun showKeyPicker(label: String, default: XKeycode) {
        content.removeAllViews()
        scrollTo(0, 0)
        section("Key for \"$label\"") {
            addView(
                rowOf(
                    button("Back") { showRemap() },
                    button("Default (${keyName(default.name)})") {
                        PadSettings.setRemap(label, null)
                        showRemap()
                    },
                ),
            )
        }
        section("Keys") {
            PICKER_KEYS.chunked(6).forEach { chunk ->
                addView(
                    rowOf(
                        *chunk.map { key ->
                            button(keyName(key.name), active = PadSettings.remapped(label) == key.name) {
                                PadSettings.setRemap(label, key.name)
                                showRemap()
                            }
                        }.toTypedArray(),
                        filler = 6 - chunk.size,
                    ),
                )
            }
        }
    }

    // Pad layout

    /**
     * A small map of the pad: its columns side by side, each with its groups top to bottom. Tap a group
     * to select it, then move it with the arrows or switch it on or off. The pad itself changes when
     * settings close.
     */
    private fun showLayoutEditor(selected: String) {
        content.removeAllViews()
        val layout = PadSettings.layout()
        fun shown(group: String) = PadSettings.groupShownKey(group)?.let { PadSettings.bool(it) }
            ?: listOf(PadSettings.MOD_SHIFT, PadSettings.MOD_CTRL, PadSettings.MOD_ALT).any { PadSettings.bool(it) }

        section("Pad layout") {
            addView(
                note(
                    "Tap a group, then move it. ◀ ▶ take it out into a column of its own, then into the next " +
                        "column over, so it can reach any spot, including either edge. ▲ ▼ move it within its column.",
                ),
            )
            addView(rowOf(button("Back") { showMain() }, button("Reset layout") {
                PadSettings.resetLayout()
                showLayoutEditor(selected)
            }))
        }
        section("Pad") {
            // The map: one box per column, as wide as the column will be on the pad.
            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(dp(4), dp(4), dp(4), dp(4))
                    layout.forEach { column ->
                        addView(
                            LinearLayout(context).apply {
                                orientation = LinearLayout.VERTICAL
                                background = theme.groupBackground(density, strong = false)
                                setPadding(dp(2), dp(2), dp(2), dp(2))
                                val wide = if (column.all { it == PadSettings.G_PARTY }) 0.6f else 1f
                                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, wide)
                                    .apply { setMargins(dp(3), 0, dp(3), 0) }
                                column.forEach { group ->
                                    val name = PadSettings.GROUP_NAMES.getValue(group)
                                    addView(
                                        button(if (shown(group)) name else "$name (off)", active = group == selected) {
                                            showLayoutEditor(group)
                                        }.apply {
                                            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52))
                                                .apply { setMargins(dp(2), dp(2), dp(2), dp(2)) }
                                            if (!shown(group)) alpha = 0.5f
                                        },
                                    )
                                }
                            },
                        )
                    }
                },
            )
        }
        section("Move ${PadSettings.GROUP_NAMES.getValue(selected)}") {
            addView(
                rowOf(
                    button("◀") {
                        PadSettings.moveGroupSideways(selected, -1)
                        showLayoutEditor(selected)
                    },
                    button("▲") {
                        PadSettings.moveGroupVertically(selected, -1)
                        showLayoutEditor(selected)
                    },
                    button("▼") {
                        PadSettings.moveGroupVertically(selected, 1)
                        showLayoutEditor(selected)
                    },
                    button("▶") {
                        PadSettings.moveGroupSideways(selected, 1)
                        showLayoutEditor(selected)
                    },
                ),
            )
            val key = PadSettings.groupShownKey(selected)
            if (key != null) {
                addView(rowOf(button(if (PadSettings.bool(key)) "Shown (tap to hide)" else "Hidden (tap to show)", active = PadSettings.bool(key)) {
                    PadSettings.set(key, !PadSettings.bool(key))
                    showLayoutEditor(selected)
                }))
            } else {
                addView(note("The modifiers show when at least one is switched on under \"Modifier keys shown\"."))
            }
        }
    }

    // Marker, command and phrase editing

    private fun listTitle(listKey: String) = when (listKey) {
        PadSettings.MARKERS -> "Marker buttons"
        PadSettings.COMMANDS -> "Command buttons"
        else -> "Quick phrases"
    }

    private fun showActionList(listKey: String) {
        content.removeAllViews()
        scrollTo(0, 0)
        section(listTitle(listKey)) {
            addView(
                note(
                    when (listKey) {
                        PadSettings.MARKERS ->
                            "Each marker types /tm with the marker's number. If your client doesn't have /tm, bind " +
                                "the markers under Key Bindings in game and switch these buttons to those keys."
                        PadSettings.COMMANDS -> "Tap a button to change what it does."
                        else -> "Sent with the \"To\" channel picked on the chat bar above the keyboard."
                    },
                ),
            )
            addView(rowOf(button("Back") { showMain() }))
        }
        section("Buttons") {
            val actions = PadSettings.actions(listKey)
            // In the order they sit on the pad (phrases keep their own order).
            val shown = if (listKey == PadSettings.PHRASES) actions.indices.toList() else PadSettings.displayOrder(listKey)
            shown.map { IndexedValue(it, actions[it]) }.chunked(2).forEach { chunk ->
                addView(
                    rowOf(
                        *chunk.map { (i, action) ->
                            val name = if (listKey == PadSettings.MARKERS) "${PadSettings.MARKER_STYLES[i].first} ${action.label}" else action.label
                            button("$name\n${describe(action)}") { showActionEditor(listKey, i, action) }
                        }.toTypedArray(),
                        filler = 2 - chunk.size,
                    ),
                )
            }
        }
    }

    /** One line saying what an action does, e.g. "/tm 8" or "Ctrl + F1". */
    private fun describe(action: PadAction): String = when {
        action.isKey -> (PadAction.MODIFIER_KEYS.keys.filter { it in action.modifiers }.map { it.lowercase().replaceFirstChar(Char::uppercase) } +
            keyName(action.key)).joinToString(" + ")
        action.text.isBlank() -> "(empty)"
        else -> action.text
    }

    /**
     * Edits one button. Changes are kept in [draft] until Save. Text is typed with the pad's own keyboard
     * at the bottom, into whichever field is selected.
     */
    private fun showActionEditor(listKey: String, index: Int, draft: PadAction, field: Int = 1) {
        content.removeAllViews()
        scrollTo(0, 0)
        val phrase = listKey == PadSettings.PHRASES
        var current = draft
        // 0 = name, 1 = command. A key button only has a name to type.
        var activeField = if (phrase) 1 else if (draft.isKey) 0 else field
        lateinit var labelView: TextView
        lateinit var textView: TextView

        fun fieldText(value: String, active: Boolean) = if (active) "$value▏" else value.ifEmpty { " " }
        fun refreshFields() {
            if (!phrase) {
                labelView.text = fieldText(current.label, activeField == 0)
                labelView.background = theme.buttonBackground(density, 8f, active = false, emphasized = activeField == 0, muted = true)
            }
            textView.text = fieldText(current.text, activeField == 1)
            textView.background = theme.buttonBackground(density, 8f, active = false, emphasized = activeField == 1, muted = true)
        }
        fun field(onSelect: () -> Unit) = TextView(context).apply {
            setTextColor(theme.text)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = theme.typeface
            setPadding(dp(10), dp(8), dp(10), dp(8))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) }
            setOnClickListener {
                onSelect()
                refreshFields()
            }
        }

        section("${listTitle(listKey)}: button ${index + 1}") {
            if (!phrase) {
                addView(
                    rowOf(
                        button("Chat command", active = !current.isKey) { showActionEditor(listKey, index, current.copy(key = "", modifiers = emptySet()), activeField) },
                        button("Key (for an in-game macro)", active = current.isKey) {
                            showKeyChoice(current) { picked -> showActionEditor(listKey, index, picked, 0) }
                        },
                    ),
                )
                labelView = field { activeField = 0 }
                addView(LinearLayout(context).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(8), 0, 0, 0)
                    addView(label("Name", 80))
                    addView(labelView)
                })
            }
            textView = field { activeField = 1 }
            if (current.isKey) {
                addView(note("Sends ${describe(current)}. Bind it to a macro in WoW's Key Bindings."))
                addView(rowOf(button("Change key") { showKeyChoice(current) { picked -> showActionEditor(listKey, index, picked, 0) } }))
            } else {
                addView(LinearLayout(context).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(8), 0, 0, 0)
                    addView(label(if (phrase) "Phrase" else "Command", 80))
                    addView(textView)
                })
                if (!phrase) addView(note("Start with /, like /roll or /use Hearthstone. Text without / is said with /s."))
            }
            addView(
                rowOf(
                    button("Save") {
                        val saved = if (phrase) current.copy(label = current.text) else current
                        PadSettings.setAction(listKey, index, saved)
                        showActionList(listKey)
                    },
                    button("Cancel") { showActionList(listKey) },
                    button("Default") { showActionEditor(listKey, index, PadSettings.defaultAction(listKey, index)) },
                ),
            )
        }
        refreshFields()
        // The pad's own keyboard types into the selected field (nothing goes to the game). Enter switches field.
        content.addView(
            ExternalOnScreenKeyboardView(context, null, theme, localInput = { key ->
                fun edit(change: (String) -> String) {
                    current = if (activeField == 0) current.copy(label = change(current.label)) else current.copy(text = change(current.text))
                }
                when (key) {
                    is ExternalOnScreenKeyboardView.LocalKey.Text -> edit { (it + key.text).take(MAX_TEXT) }
                    ExternalOnScreenKeyboardView.LocalKey.Backspace -> edit { it.dropLast(1) }
                    ExternalOnScreenKeyboardView.LocalKey.Enter -> if (!phrase && !current.isKey) activeField = 1 - activeField
                }
                refreshFields()
            }),
        )
    }

    /** Key mode: Shift/Ctrl/Alt toggles and a key grid. Picking a key returns the updated action. */
    private fun showKeyChoice(action: PadAction, onPicked: (PadAction) -> Unit) {
        content.removeAllViews()
        scrollTo(0, 0)
        var mods = action.modifiers
        section("Key for \"${action.label}\"") {
            addView(note("Pick modifiers, then a key. Ctrl or Alt + an F-key is usually free for macros."))
            addView(
                rowOf(
                    *PadAction.MODIFIER_KEYS.keys.map { mod ->
                        button(mod.lowercase().replaceFirstChar(Char::uppercase), active = mod in mods) { b ->
                            mods = if (mod in mods) mods - mod else mods + mod
                            style(b, mod in mods)
                        }
                    }.toTypedArray(),
                ),
            )
            addView(rowOf(button("Back") { onPicked(action) }))
        }
        section("Keys") {
            PICKER_KEYS.chunked(6).forEach { chunk ->
                addView(
                    rowOf(
                        *chunk.map { key ->
                            button(keyName(key.name), active = action.key == key.name) {
                                onPicked(action.copy(key = key.name, modifiers = mods))
                            }
                        }.toTypedArray(),
                        filler = 6 - chunk.size,
                    ),
                )
            }
        }
    }

    // Building blocks

    private fun dp(value: Int): Int = (value * density).toInt()

    private fun section(title: String, build: LinearLayout.() -> Unit) {
        val group = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = theme.groupBackground(density, strong = false)
            setPadding(dp(4), dp(4), dp(4), dp(4))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { setMargins(0, 0, 0, dp(6)) }
        }
        group.addView(
            TextView(context).apply {
                text = title.uppercase()
                setTextColor(theme.border)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                typeface = theme.typeface
                setPadding(dp(8), dp(2), dp(8), dp(2))
            },
        )
        group.build()
        content.addView(group)
    }

    private fun note(text: String) = TextView(context).apply {
        this.text = text
        setTextColor(theme.text)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        typeface = theme.typeface
        setPadding(dp(8), dp(2), dp(8), dp(2))
    }

    private fun label(text: String, widthDp: Int = 120) = TextView(context).apply {
        this.text = text
        setTextColor(theme.text)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        typeface = theme.typeface
        layoutParams = LinearLayout.LayoutParams(dp(widthDp), ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun style(button: Button, active: Boolean) {
        button.background = theme.buttonBackground(density, 8f, active, muted = true)
        button.setTextColor(if (active) theme.textPressed else theme.text)
    }

    private fun button(text: String, active: Boolean = false, onClick: (Button) -> Unit): Button = Button(context).apply {
        this.text = text
        setAllCaps(false)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        typeface = theme.typeface
        minHeight = dp(36)
        minimumHeight = dp(36)
        setPadding(dp(6), dp(2), dp(6), dp(2))
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) }
        style(this, active)
        setOnClickListener {
            PadSettings.haptic(this)
            onClick(this)
        }
    }

    /** A row of equal-width views. [filler] adds empty cells so a short last row keeps the grid widths. */
    private fun rowOf(vararg views: View, filler: Int = 0): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        views.forEach { addView(it) }
        repeat(filler) {
            addView(View(context), LinearLayout.LayoutParams(0, 1, 1f).apply { setMargins(dp(3), 0, dp(3), 0) })
        }
    }

    private fun toggleRow(items: List<Pair<String, String>>): LinearLayout = rowOf(
        *items.map { (text, key) ->
            button(text, active = PadSettings.bool(key)) { b ->
                val on = !PadSettings.bool(key)
                PadSettings.set(key, on)
                style(b, on)
            }
        }.toTypedArray(),
    )

    private fun cycleRow(title: String, key: String, options: List<String>): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(8), 0, 0, 0)
        addView(label(title))
        addView(
            button(options[PadSettings.int(key).coerceIn(0, options.lastIndex)]) { b ->
                val next = (PadSettings.int(key) + 1) % options.size
                PadSettings.set(key, next)
                b.text = options[next]
            },
        )
    }

    private fun sliderRow(title: String, key: String, min: Int, max: Int, format: (Int) -> String): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), 0, dp(8), 0)
            addView(label(title))
            val valueText = TextView(context).apply {
                setTextColor(theme.text)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                typeface = theme.typeface
                gravity = Gravity.END
                layoutParams = LinearLayout.LayoutParams(dp(56), ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            val current = PadSettings.int(key).coerceIn(min, max)
            valueText.text = format(current)
            val bar = SeekBar(context).apply {
                this.max = max - min
                progress = current - min
                progressTintList = ColorStateList.valueOf(theme.border)
                thumbTintList = ColorStateList.valueOf(theme.borderBright)
                layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                        valueText.text = format(min + progress)
                        if (fromUser) PadSettings.set(key, min + progress)
                    }

                    override fun onStartTrackingTouch(bar: SeekBar?) {}
                    override fun onStopTrackingTouch(bar: SeekBar?) {}
                })
            }
            addView(bar)
            addView(valueText)
        }

    private fun profileRow(slot: Int, status: TextView): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(8), 0, 0, 0)
        addView(label("Profile $slot"))
        addView(
            button("Save") {
                PadSettings.saveProfile(slot)
                status.text = "Saved to profile $slot"
            },
        )
        addView(
            button("Load") {
                if (PadSettings.loadProfile(slot)) showMain() else status.text = "Profile $slot is empty"
            },
        )
    }

    private fun flavorRow(): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(8), 0, 0, 0)
        addView(label("Next launch"))
        addView(
            button(WowFlavor.current.label) { b ->
                val all = WowFlavor.entries
                val next = all[(all.indexOf(WowFlavor.current) + 1) % all.size]
                WowFlavor.select(context, next)
                b.text = next.label
            },
        )
    }

    // Helpers

    private fun tenths(value: Int) = "${value / 10}.${value % 10}"

    private fun keyName(enumName: String): String = when (val n = enumName.removePrefix("KEY_")) {
        "PRIOR" -> "PgUp"
        "NEXT" -> "PgDn"
        "BKSP" -> "Bksp"
        "BRACKET_LEFT" -> "["
        "BRACKET_RIGHT" -> "]"
        "SEMICOLON" -> ";"
        "APOSTROPHE" -> "'"
        "COMMA" -> ","
        "PERIOD" -> "."
        "SLASH" -> "/"
        "BACKSLASH" -> "\\"
        "GRAVE" -> "`"
        "MINUS" -> "-"
        "EQUAL" -> "="
        else -> n.lowercase().replaceFirstChar { it.uppercase() }.takeIf { n.length > 1 } ?: n
    }

    private companion object {
        /** Longest name or command the editor accepts (WoW's chat box takes 255 characters). */
        const val MAX_TEXT = 200

        val PICKER_KEYS: List<XKeycode> = buildList {
            val names = ('A'..'Z').map { "KEY_$it" } + (0..9).map { "KEY_$it" } + (1..12).map { "KEY_F$it" } +
                listOf(
                    "KEY_ESC", "KEY_TAB", "KEY_SPACE", "KEY_ENTER", "KEY_BKSP", "KEY_DEL", "KEY_INSERT", "KEY_HOME",
                    "KEY_END", "KEY_PRIOR", "KEY_NEXT", "KEY_UP", "KEY_DOWN", "KEY_LEFT", "KEY_RIGHT", "KEY_MINUS",
                    "KEY_EQUAL", "KEY_BRACKET_LEFT", "KEY_BRACKET_RIGHT", "KEY_SEMICOLON", "KEY_APOSTROPHE", "KEY_COMMA",
                    "KEY_PERIOD", "KEY_SLASH", "KEY_BACKSLASH", "KEY_GRAVE",
                )
            names.forEach { name -> runCatching { XKeycode.valueOf(name) }.getOrNull()?.let { add(it) } }
        }
    }
}
