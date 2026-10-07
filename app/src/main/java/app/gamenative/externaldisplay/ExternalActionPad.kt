package app.gamenative.externaldisplay

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import com.winlator.widget.TouchpadView
import com.winlator.xserver.XServer
import app.gamenative.R

/**
 * Whole second-screen UI: a slim header with a trackpad button (left) and a keyboard button (right),
 * and below it either the 12 action buttons or a laptop-style trackpad. The keyboard is the app's own
 * on-screen keyboard, drawn over the bottom of whichever is showing.
 */
class ExternalActionPad(
    context: Context,
    xServer: XServer,
    private val theme: PadTheme,
    touchpadViewProvider: () -> TouchpadView?,
) : LinearLayout(context) {

    private val density = resources.displayMetrics.density
    private val trackpadButton: ImageButton
    private val keyboardButton: ImageButton
    private val keyboardView: ExternalOnScreenKeyboardView
    private val padView: ExternalActionBarView
    private val trackpadView: TouchpadView

    init {
        orientation = VERTICAL
        setBackgroundColor(theme.background)

        padView = ExternalActionBarView(context, xServer, theme).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        trackpadView = TouchpadView(context, xServer, false).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                val m = (10 * density).toInt()
                setMargins(m, 0, m, m)
            }
            background = GradientDrawable().apply {
                cornerRadius = 16 * density
                setColor(theme.key)
                setStroke((2 * density).toInt(), theme.border)
            }
            touchpadViewProvider()?.let { setSimTouchScreen(it.isSimTouchScreen) }
            visibility = View.GONE
        }
        keyboardView = ExternalOnScreenKeyboardView(context, xServer).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { gravity = Gravity.BOTTOM }
            visibility = View.GONE
        }

        trackpadButton = circleButton(R.drawable.icon_trackpad, "Trackpad") { setTrackpad(trackpadView.visibility != View.VISIBLE) }
        keyboardButton = circleButton(R.drawable.icon_keyboard, "Keyboard") { setKeyboard(keyboardView.visibility != View.VISIBLE) }

        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            val m = (6 * density).toInt()
            setPadding(m * 2, m, m * 2, m)
            addView(trackpadButton)
            addView(View(context), LayoutParams(0, 1, 1f))
            addView(keyboardButton)
        }
        val body = FrameLayout(context).apply {
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            addView(padView)
            addView(trackpadView)
            addView(keyboardView)
        }
        addView(header)
        addView(body)
    }

    private fun setTrackpad(on: Boolean) {
        trackpadView.visibility = if (on) View.VISIBLE else View.GONE
        padView.visibility = if (on) View.GONE else View.VISIBLE
        styleButton(trackpadButton, on)
    }

    private fun setKeyboard(on: Boolean) {
        keyboardView.visibility = if (on) View.VISIBLE else View.GONE
        styleButton(keyboardButton, on)
    }

    private fun circleButton(@DrawableRes icon: Int, label: String, onClick: () -> Unit): ImageButton {
        return ImageButton(context).apply {
            val size = (40 * density).toInt()
            layoutParams = LayoutParams(size, size)
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
        button.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (active) theme.pressed else theme.key)
            setStroke((2 * density).toInt(), theme.border)
        }
        button.setColorFilter(theme.text)
    }
}
