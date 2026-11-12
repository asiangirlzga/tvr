package com.example.privatevoice.access

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.graphics.Path
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import com.example.privatevoice.command.Direction

/**
 * Presses system buttons and swipes on your behalf.
 *
 * Privacy by design: the service config asks for no events and no window content, and
 * [onAccessibilityEvent] ignores everything. It cannot see your screen, apps, notifications or typing.
 */
class ControlAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Intentionally empty. This service never reads anything from the screen.
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    /** Moves a finger across the screen in [direction]. Returns false if the gesture couldn't start. */
    fun swipe(direction: Direction): Boolean {
        val m = resources.displayMetrics
        val w = m.widthPixels.toFloat()
        val h = m.heightPixels.toFloat()
        val path = Path()
        when (direction) {
            Direction.UP -> { path.moveTo(w / 2, h * 0.72f); path.lineTo(w / 2, h * 0.28f) }
            Direction.DOWN -> { path.moveTo(w / 2, h * 0.28f); path.lineTo(w / 2, h * 0.72f) }
            Direction.LEFT -> { path.moveTo(w * 0.8f, h / 2); path.lineTo(w * 0.2f, h / 2) }
            Direction.RIGHT -> { path.moveTo(w * 0.2f, h / 2); path.lineTo(w * 0.8f, h / 2) }
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 280)
        return dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    companion object {
        @Volatile
        var instance: ControlAccessibilityService? = null
            private set

        /** True if the user has switched the service on in system settings. */
        fun isEnabled(context: Context): Boolean {
            val me = ComponentName(context, ControlAccessibilityService::class.java).flattenToString()
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            return enabled.split(':').any { it.equals(me, ignoreCase = true) }
        }
    }
}
