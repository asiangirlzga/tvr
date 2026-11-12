package com.example.privatevoice

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import com.example.privatevoice.access.ControlAccessibilityService
import com.example.privatevoice.command.AppEntry
import com.example.privatevoice.command.AppMatcher
import com.example.privatevoice.command.Command
import com.example.privatevoice.command.GlobalKind
import com.example.privatevoice.command.InfoKind
import com.example.privatevoice.command.PanelKind
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Carries out a [Command] on this phone and returns a short message to show. */
object CommandExecutor {

    private const val NEEDS_ACCESS = "Turn on the accessibility service in the app first."

    fun execute(context: Context, command: Command): String = try {
        run(context, command)
    } catch (e: Exception) {
        "That didn't work on this phone."
    }

    private fun run(context: Context, command: Command): String = when (command) {
        is Command.Global -> global(command.kind)
        is Command.Swipe -> {
            val svc = ControlAccessibilityService.instance
            if (svc == null) NEEDS_ACCESS else if (svc.swipe(command.finger)) "Scrolled" else "Couldn't scroll here."
        }
        is Command.Volume -> volume(context, command)
        is Command.Mute -> {
            val am = audio(context)
            am.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                if (command.mute) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE,
                AudioManager.FLAG_SHOW_UI,
            )
            if (command.mute) "Muted" else "Unmuted"
        }
        Command.MaxVolume -> {
            val am = audio(context)
            am.setStreamVolume(AudioManager.STREAM_MUSIC, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC), AudioManager.FLAG_SHOW_UI)
            "Volume at maximum"
        }
        is Command.Torch -> torch(context, command.on)
        is Command.Panel -> panel(context, command.kind)
        is Command.Dial -> {
            start(context, Intent(Intent.ACTION_DIAL, android.net.Uri.fromParts("tel", command.digits, null)))
            "Dialer opened for ${command.digits}. Press call yourself."
        }
        is Command.Info -> info(context, command.kind)
        is Command.OpenApp -> openApp(context, command.query)
        Command.StopListening -> "Stopped listening"
    }

    private fun global(kind: GlobalKind): String {
        val svc = ControlAccessibilityService.instance ?: return NEEDS_ACCESS
        val (action, label) = when (kind) {
            GlobalKind.HOME -> AccessibilityService.GLOBAL_ACTION_HOME to "Home"
            GlobalKind.BACK -> AccessibilityService.GLOBAL_ACTION_BACK to "Back"
            GlobalKind.RECENTS -> AccessibilityService.GLOBAL_ACTION_RECENTS to "Recent apps"
            GlobalKind.NOTIFICATIONS -> AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS to "Notifications"
            GlobalKind.QUICK_SETTINGS -> AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS to "Quick settings"
            GlobalKind.LOCK -> {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return "Locking needs Android 9 or newer."
                AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN to "Screen locked"
            }
            GlobalKind.SCREENSHOT -> {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return "Screenshots need Android 9 or newer."
                AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT to "Screenshot taken"
            }
        }
        return if (svc.performGlobalAction(action)) label else "Couldn't do that right now."
    }

    private fun audio(context: Context) = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private fun volume(context: Context, c: Command.Volume): String {
        val am = audio(context)
        val dir = if (c.up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        repeat(c.times) { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, AudioManager.FLAG_SHOW_UI) }
        val level = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        return (if (c.up) "Volume up" else "Volume down") + (if (c.times > 1) " ×${c.times}" else "") + " ($level/$max)"
    }

    private fun torch(context: Context, on: Boolean): String {
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return "This phone has no flashlight."
        cm.setTorchMode(id, on)
        return if (on) "Flashlight on" else "Flashlight off"
    }

    private fun panel(context: Context, kind: PanelKind): String {
        val (action, label) = when (kind) {
            PanelKind.SETTINGS -> Settings.ACTION_SETTINGS to "Settings"
            PanelKind.WIFI -> Settings.ACTION_WIFI_SETTINGS to "Wi-Fi settings"
            PanelKind.BLUETOOTH -> Settings.ACTION_BLUETOOTH_SETTINGS to "Bluetooth settings"
            PanelKind.DISPLAY -> Settings.ACTION_DISPLAY_SETTINGS to "Display settings"
            PanelKind.SOUND -> Settings.ACTION_SOUND_SETTINGS to "Sound settings"
            PanelKind.BATTERY_SAVER -> Settings.ACTION_BATTERY_SAVER_SETTINGS to "Battery saver"
            PanelKind.LOCATION -> Settings.ACTION_LOCATION_SOURCE_SETTINGS to "Location settings"
        }
        start(context, Intent(action))
        return "Opened $label"
    }

    private fun info(context: Context, kind: InfoKind): String = when (kind) {
        InfoKind.TIME -> "It's " + LocalTime.now().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
        InfoKind.DATE -> "Today is " + LocalDate.now().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))
        InfoKind.BATTERY -> {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            "Battery is at ${bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)}%"
        }
    }

    private fun launchableApps(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { AppEntry(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .distinctBy { it.packageName }
    }

    private fun openApp(context: Context, query: String): String {
        val app = AppMatcher.best(query, launchableApps(context)) ?: return "Couldn't find an app called “$query”."
        val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
            ?: return "Couldn't open ${app.label}."
        start(context, intent)
        return "Opening ${app.label}"
    }

    /** Uses the accessibility service when available, which may start screens while the app is in the background. */
    private fun start(context: Context, intent: Intent) {
        val ctx: Context = ControlAccessibilityService.instance ?: context
        ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
