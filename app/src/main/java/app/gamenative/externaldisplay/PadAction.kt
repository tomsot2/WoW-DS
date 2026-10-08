package app.gamenative.externaldisplay

import android.os.Handler
import android.os.Looper
import com.winlator.xserver.XKeycode
import com.winlator.xserver.XServer
import org.json.JSONArray
import org.json.JSONObject

/**
 * What a marker or command button on the pad does. Two kinds:
 *
 * - **Chat command** ([text]): typed into WoW's chat box and sent, e.g. "/tm 8" or "/readycheck".
 *   The line always starts with "/", so WoW's default "/" binding opens the chat box first.
 * - **Key** ([key] plus [modifiers]): one key press, e.g. Ctrl+F1, for an in-game macro or key binding.
 *
 * Either way one tap is exactly one line or one key press. Nothing repeats, loops or runs on a timer,
 * so a button only ever does what a player could do with a single press or a single typed line.
 */
data class PadAction(
    val label: String,
    val text: String = "",
    val key: String = "",
    val modifiers: Set<String> = emptySet(),
) {
    val isKey: Boolean get() = key.isNotEmpty()

    /** The X key for [key], or null if it isn't a known key name. */
    val keycode: XKeycode? get() = runCatching { XKeycode.valueOf(key) }.getOrNull()

    fun toJson(): JSONObject = JSONObject().apply {
        put("label", label)
        put("text", text)
        put("key", key)
        put("mods", JSONArray(modifiers.toList()))
    }

    companion object {
        const val SHIFT = "SHIFT"
        const val CTRL = "CTRL"
        const val ALT = "ALT"
        val MODIFIER_KEYS = linkedMapOf(SHIFT to XKeycode.KEY_SHIFT_L, CTRL to XKeycode.KEY_CTRL_L, ALT to XKeycode.KEY_ALT_L)

        fun fromJson(json: JSONObject): PadAction = PadAction(
            label = json.optString("label"),
            text = json.optString("text"),
            key = json.optString("key"),
            modifiers = json.optJSONArray("mods")?.let { a -> (0 until a.length()).map { a.optString(it) }.toSet() }.orEmpty(),
        )

        fun listToJson(actions: List<PadAction>): String = JSONArray(actions.map { it.toJson() }).toString()

        fun listFromJson(text: String?): List<PadAction>? = runCatching {
            val array = JSONArray(text ?: return null)
            (0 until array.length()).map { fromJson(array.getJSONObject(it)) }
        }.getOrNull()
    }
}

/**
 * Types a line into WoW's chat box and sends it, one key at a time.
 *
 * The first key is "/", which opens the chat box through WoW's default binding; the rest wait a moment
 * so the box is open before they arrive (otherwise letters would trigger key bindings instead).
 * While a line is being typed, further lines are ignored, so two lines can never mix.
 */
object ChatTyper {
    private const val OPEN_DELAY_MS = 120L
    private const val KEY_DELAY_MS = 12L

    private val handler = Handler(Looper.getMainLooper())
    @Volatile
    private var busy = false

    /** Puts "/" in front of a line that doesn't start with one, as a /say. */
    fun normalize(line: String): String {
        val trimmed = line.trim()
        return if (trimmed.startsWith("/")) trimmed else "/s $trimmed"
    }

    /**
     * Types [line] (see [normalize]). With [send] the line is sent with Enter; without, the chat box is
     * left open with the text in it, ready for the player to finish typing.
     */
    fun type(xServer: XServer, line: String, send: Boolean = true) {
        if (busy) return
        val keys = normalize(line).mapNotNull { keyFor(it) }.toMutableList()
        if (send) keys += XKeycode.KEY_ENTER to false
        if (keys.isEmpty()) return
        busy = true
        var delay = 0L
        keys.forEachIndexed { index, (key, shift) ->
            handler.postDelayed({ tap(xServer, key, shift) }, delay)
            delay += if (index == 0) OPEN_DELAY_MS else KEY_DELAY_MS
        }
        handler.postDelayed({ busy = false }, delay)
    }

    private fun tap(xServer: XServer, key: XKeycode, shift: Boolean) {
        val shiftWasDown = xServer.keyboard.modifiersMask.isSet(1)
        if (shift && !shiftWasDown) xServer.injectKeyPress(XKeycode.KEY_SHIFT_L)
        xServer.injectKeyPress(key)
        xServer.injectKeyRelease(key)
        if (shift && !shiftWasDown) xServer.injectKeyRelease(XKeycode.KEY_SHIFT_L)
    }

    /** US keyboard: the key for a character, and whether it needs Shift. Unknown characters are skipped. */
    fun keyFor(c: Char): Pair<XKeycode, Boolean>? {
        if (c in 'a'..'z') return XKeycode.valueOf("KEY_${c.uppercaseChar()}") to false
        if (c in 'A'..'Z') return XKeycode.valueOf("KEY_$c") to true
        if (c in '0'..'9') return XKeycode.valueOf("KEY_$c") to false
        SYMBOLS[c]?.let { return it }
        return null
    }

    private val SYMBOLS: Map<Char, Pair<XKeycode, Boolean>> = mapOf(
        ' ' to (XKeycode.KEY_SPACE to false),
        '!' to (XKeycode.KEY_1 to true), '@' to (XKeycode.KEY_2 to true), '#' to (XKeycode.KEY_3 to true),
        '$' to (XKeycode.KEY_4 to true), '%' to (XKeycode.KEY_5 to true), '^' to (XKeycode.KEY_6 to true),
        '&' to (XKeycode.KEY_7 to true), '*' to (XKeycode.KEY_8 to true), '(' to (XKeycode.KEY_9 to true),
        ')' to (XKeycode.KEY_0 to true),
        '-' to (XKeycode.KEY_MINUS to false), '_' to (XKeycode.KEY_MINUS to true),
        '=' to (XKeycode.KEY_EQUAL to false), '+' to (XKeycode.KEY_EQUAL to true),
        '[' to (XKeycode.KEY_BRACKET_LEFT to false), '{' to (XKeycode.KEY_BRACKET_LEFT to true),
        ']' to (XKeycode.KEY_BRACKET_RIGHT to false), '}' to (XKeycode.KEY_BRACKET_RIGHT to true),
        '\\' to (XKeycode.KEY_BACKSLASH to false), '|' to (XKeycode.KEY_BACKSLASH to true),
        ';' to (XKeycode.KEY_SEMICOLON to false), ':' to (XKeycode.KEY_SEMICOLON to true),
        '\'' to (XKeycode.KEY_APOSTROPHE to false), '"' to (XKeycode.KEY_APOSTROPHE to true),
        '`' to (XKeycode.KEY_GRAVE to false), '~' to (XKeycode.KEY_GRAVE to true),
        ',' to (XKeycode.KEY_COMMA to false), '<' to (XKeycode.KEY_COMMA to true),
        '.' to (XKeycode.KEY_PERIOD to false), '>' to (XKeycode.KEY_PERIOD to true),
        '/' to (XKeycode.KEY_SLASH to false), '?' to (XKeycode.KEY_SLASH to true),
    )
}
