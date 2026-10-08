package app.gamenative.externaldisplay

import android.content.Context
import android.content.SharedPreferences
import android.view.HapticFeedbackConstants
import android.view.View
import org.json.JSONObject

/**
 * User settings for the second-screen pad and the controller extras, kept in one small
 * SharedPreferences file. Every value is a Boolean or an Int (decimals are stored as tenths or
 * percent), so a whole set can be copied to a profile, exported or reset as plain JSON.
 *
 * Reads fall back to the defaults until [init] has run, so callers never need a null check.
 */
object PadSettings {
    private const val PREFS = "pad_settings"
    private const val REMAP = "remap"
    private const val PROFILE_PREFIX = "profile_"
    const val PROFILE_SLOTS = 3

    // Key names.
    const val MOD_SHIFT = "mod_shift"
    const val MOD_CTRL = "mod_ctrl"
    const val MOD_ALT = "mod_alt"
    const val SEC_WINDOWS = "sec_windows"
    const val SEC_MARKERS = "sec_markers"
    const val SEC_COMMANDS = "sec_commands"
    const val SEC_PARTY = "sec_fkeys"
    const val SWAP_SIDES = "swap_sides"
    const val MUTED = "muted_borders"
    const val EMPHASIS = "number_emphasis"
    const val LABEL_SCALE = "label_scale"
    const val HAPTICS = "haptics"
    const val DOUBLE_TAP_MS = "double_tap_ms"
    const val BURN_IN_SHIFT = "burn_in_shift"
    const val PHRASE_CHANNEL = "phrase_channel"
    const val KEYBOARD_LAYOUT = "keyboard_layout"
    const val TP_SPEED = "trackpad_speed"
    const val TP_ACCEL = "trackpad_accel"
    const val TP_TAP = "trackpad_tap"
    const val STICK_DEADZONE = "stick_deadzone"
    const val DIM_IDLE = "dim_idle"
    const val DIM_AMOUNT = "dim_amount"

    /** Choices for [DIM_IDLE], in seconds. 0 never dims. */
    val DIM_IDLE_SECONDS = listOf(0, 15, 30, 60, 120)

    /** Every window button the pad can show. Each has an on/off setting, on by default. */
    val WINDOW_LABELS = listOf(
        "Map", "Character", "Spellbook", "Talents", "Skills", "Quest Log", "Social", "System",
        "Bags", "Group Finder", "Achievements", "Guild",
    )

    fun windowKey(label: String) = "win_$label"

    /** Chat channels the quick phrases can go to: label and slash command. */
    val PHRASE_CHANNELS = listOf("Say" to "/s", "Party" to "/p", "Instance" to "/i", "Raid" to "/ra", "Guild" to "/g")

    // Editable button lists, each stored as a JSON array of PadAction.
    const val MARKERS = "markers"
    const val COMMANDS = "commands"
    const val PHRASES = "phrases"

    /**
     * Target markers in the order shown (4 per row), with WoW's marker numbers for /tm.
     * The symbol and color stand in for the marker icon.
     */
    val MARKER_STYLES = listOf(
        Triple("☠", "Skull", 0xFFF2F2F2.toInt()),
        Triple("✖", "Cross", 0xFFE5403A.toInt()),
        Triple("■", "Square", 0xFF4C9BFF.toInt()),
        Triple("☾", "Moon", 0xFFB8D2E6.toInt()),
        Triple("▲", "Triangle", 0xFF55D24A.toInt()),
        Triple("◆", "Diamond", 0xFFC06BE6.toInt()),
        Triple("●", "Circle", 0xFFFF9A2E.toInt()),
        Triple("★", "Star", 0xFFFFE14A.toInt()),
    )
    private val MARKER_NUMBERS = listOf(8, 7, 6, 5, 4, 3, 2, 1)

    private val ACTION_DEFAULTS: Map<String, List<PadAction>> = mapOf(
        MARKERS to MARKER_STYLES.mapIndexed { i, (_, name, _) -> PadAction(name, text = "/tm ${MARKER_NUMBERS[i]}") },
        COMMANDS to listOf(
            PadAction("Ready check", text = "/readycheck"),
            PadAction("Roll", text = "/roll"),
            PadAction("Follow", text = "/follow"),
            PadAction("Focus", text = "/focus"),
            PadAction("Hearthstone", text = "/use Hearthstone"),
            PadAction("Stop cast", text = "/stopcasting"),
            PadAction("Thank", text = "/thank"),
            PadAction("Wave", text = "/wave"),
        ),
        PHRASES to listOf("ty", "np", "brb", "omw", "ready?", "gg").map { PadAction(it, text = it) },
    )

    /** Defaults. Decimals are stored as tenths (speeds) or percent (ramp, deadzone, emphasis). */
    private val DEFAULTS: Map<String, Any> = linkedMapOf<String, Any>(
        MOD_SHIFT to true,
        MOD_CTRL to true,
        MOD_ALT to true,
        SEC_WINDOWS to true,
        SEC_MARKERS to true,
        SEC_COMMANDS to true,
        SEC_PARTY to true,
        SWAP_SIDES to false,
        MUTED to 55,
        EMPHASIS to 100,
        LABEL_SCALE to 100,
        HAPTICS to 2,
        DOUBLE_TAP_MS to 350,
        BURN_IN_SHIFT to true,
        PHRASE_CHANNEL to 1,
        // 0: wide letters (side keys in rows of their own), 1: standard 15-key-wide rows.
        KEYBOARD_LAYOUT to 0,
        TP_SPEED to 7,
        TP_ACCEL to 10,
        TP_TAP to true,
        STICK_DEADZONE to 15,
        DIM_IDLE to 2,
        DIM_AMOUNT to 80,
    )
        .apply { WINDOW_LABELS.forEach { put(windowKey(it), true) } }

    private var prefs: SharedPreferences? = null

    /** Bumped on every change, so a screen can tell whether anything changed while it was away. */
    @Volatile
    var version = 0
        private set

    fun init(context: Context) {
        if (prefs == null) prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    fun bool(key: String): Boolean {
        val def = DEFAULTS[key] as? Boolean ?: false
        return prefs?.getBoolean(key, def) ?: def
    }

    fun int(key: String): Int {
        val def = DEFAULTS[key] as? Int ?: 0
        return prefs?.getInt(key, def) ?: def
    }

    fun set(key: String, value: Boolean) {
        prefs?.edit()?.putBoolean(key, value)?.apply()
        version++
    }

    fun set(key: String, value: Int) {
        prefs?.edit()?.putInt(key, value)?.apply()
        version++
    }

    // Convenient typed reads.
    val mutedAmount: Float get() = int(MUTED) / 100f
    val emphasis: Float get() = int(EMPHASIS) / 100f
    val labelScale: Float get() = int(LABEL_SCALE) / 100f
    val doubleTapMs: Long get() = int(DOUBLE_TAP_MS).toLong()
    val trackpadSensitivity: Float get() = int(TP_SPEED) / 10f
    val trackpadAcceleration: Float get() = int(TP_ACCEL) / 10f
    val stickDeadzone: Float get() = int(STICK_DEADZONE) / 100f
    val dimIdleMs: Long get() = DIM_IDLE_SECONDS.getOrElse(int(DIM_IDLE)) { 0 } * 1000L
    val dimAmount: Float get() = int(DIM_AMOUNT) / 100f

    /** Haptics level: 0 off, 1 light, 2 normal, 3 strong. */
    fun haptic(view: View) {
        when (int(HAPTICS)) {
            1 -> view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            2 -> view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            3 -> view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    // Button remapping: pad button label -> XKeycode name.

    private fun remapJson(): JSONObject =
        runCatching { JSONObject(prefs?.getString(REMAP, null) ?: "{}") }.getOrDefault(JSONObject())

    fun remapped(label: String): String? = remapJson().optString(label).takeIf { it.isNotEmpty() }

    fun setRemap(label: String, keyName: String?) {
        val json = remapJson()
        if (keyName == null) json.remove(label) else json.put(label, keyName)
        prefs?.edit()?.putString(REMAP, json.toString())?.apply()
        version++
    }

    fun clearRemaps() {
        prefs?.edit()?.remove(REMAP)?.apply()
        version++
    }

    // Marker buttons, command buttons and quick phrases.

    /** The buttons in list [listKey] ([MARKERS], [COMMANDS] or [PHRASES]); always the default count. */
    fun actions(listKey: String): List<PadAction> {
        val defaults = ACTION_DEFAULTS.getValue(listKey)
        val saved = PadAction.listFromJson(prefs?.getString(listKey, null)) ?: return defaults
        return defaults.indices.map { saved.getOrNull(it) ?: defaults[it] }
    }

    fun defaultAction(listKey: String, index: Int): PadAction = ACTION_DEFAULTS.getValue(listKey)[index]

    // Button positions. Long-pressing a pad button and dropping it on another swaps the two, and the
    // new order is kept here as a JSON array of ids: window labels, or marker/command list indexes.

    const val ORDER_WINDOWS = "order_windows"
    const val ORDER_MARKERS = "order_markers"
    const val ORDER_COMMANDS = "order_commands"
    private val ORDER_KEYS = listOf(ORDER_WINDOWS, ORDER_MARKERS, ORDER_COMMANDS)

    /** The ids each order starts from, in their default positions. */
    fun defaultOrder(orderKey: String): List<String> = when (orderKey) {
        ORDER_WINDOWS -> WINDOW_LABELS
        ORDER_MARKERS -> ACTION_DEFAULTS.getValue(MARKERS).indices.map { it.toString() }
        else -> ACTION_DEFAULTS.getValue(COMMANDS).indices.map { it.toString() }
    }

    /** The saved order. Unknown ids are dropped and any missing ones are added at the end, so it always fits. */
    fun order(orderKey: String): List<String> {
        val defaults = defaultOrder(orderKey)
        val saved = runCatching {
            val array = org.json.JSONArray(prefs?.getString(orderKey, null) ?: return defaults)
            (0 until array.length()).map { array.getString(it) }
        }.getOrNull() ?: return defaults
        val kept = saved.filter { it in defaults }.distinct()
        return kept + defaults.filter { it !in kept }
    }

    /** The marker or command list indexes in the order they're shown on the pad. */
    fun displayOrder(listKey: String): List<Int> =
        order(if (listKey == MARKERS) ORDER_MARKERS else ORDER_COMMANDS).map { it.toInt() }

    /** Swaps the positions of two ids. */
    fun swap(orderKey: String, a: String, b: String) {
        val list = order(orderKey).toMutableList()
        val i = list.indexOf(a)
        val j = list.indexOf(b)
        if (i < 0 || j < 0 || i == j) return
        list[i] = b
        list[j] = a
        prefs?.edit()?.putString(orderKey, org.json.JSONArray(list).toString())?.apply()
        version++
    }

    fun resetOrders() {
        prefs?.edit()?.apply { ORDER_KEYS.forEach { remove(it) } }?.apply()
        version++
    }

    fun setAction(listKey: String, index: Int, action: PadAction) {
        val list = actions(listKey).toMutableList()
        list[index] = action
        prefs?.edit()?.putString(listKey, PadAction.listToJson(list))?.apply()
        version++
    }

    /** The chat command the quick phrases are sent with, e.g. "/p". */
    val phraseChannel: Pair<String, String> get() = PHRASE_CHANNELS.getOrElse(int(PHRASE_CHANNEL)) { PHRASE_CHANNELS[1] }

    // Reset, profiles, export and import.

    fun resetAll() {
        prefs?.edit()?.apply {
            DEFAULTS.keys.forEach { remove(it) }
            ACTION_DEFAULTS.keys.forEach { remove(it) }
            ORDER_KEYS.forEach { remove(it) }
            remove(REMAP)
        }?.apply()
        version++
    }

    /** Every setting, the remap list, the button lists and button positions as one JSON object. */
    fun snapshot(): JSONObject = JSONObject().apply {
        DEFAULTS.keys.forEach { key ->
            if (DEFAULTS[key] is Boolean) put(key, bool(key)) else put(key, int(key))
        }
        put(REMAP, remapJson())
        ACTION_DEFAULTS.keys.forEach { key -> put(key, org.json.JSONArray(actions(key).map { it.toJson() })) }
        ORDER_KEYS.forEach { key -> put(key, org.json.JSONArray(order(key))) }
    }

    /** Applies a [snapshot]. Unknown or mistyped entries are ignored. */
    fun applySnapshot(json: JSONObject) {
        val editor = prefs?.edit() ?: return
        DEFAULTS.forEach { (key, def) ->
            if (!json.has(key)) return@forEach
            if (def is Boolean) editor.putBoolean(key, json.optBoolean(key, def))
            else editor.putInt(key, json.optInt(key, def as Int))
        }
        json.optJSONObject(REMAP)?.let { editor.putString(REMAP, it.toString()) }
        ACTION_DEFAULTS.keys.forEach { key -> json.optJSONArray(key)?.let { editor.putString(key, it.toString()) } }
        ORDER_KEYS.forEach { key -> json.optJSONArray(key)?.let { editor.putString(key, it.toString()) } }
        editor.apply()
        version++
    }

    fun saveProfile(slot: Int) {
        prefs?.edit()?.putString(PROFILE_PREFIX + slot, snapshot().toString())?.apply()
    }

    fun hasProfile(slot: Int): Boolean = prefs?.contains(PROFILE_PREFIX + slot) == true

    fun loadProfile(slot: Int): Boolean {
        val text = prefs?.getString(PROFILE_PREFIX + slot, null) ?: return false
        val json = runCatching { JSONObject(text) }.getOrNull() ?: return false
        applySnapshot(json)
        return true
    }
}
