package moe.shizuku.manager.service

import android.Manifest.permission.WRITE_SECURE_SETTINGS
import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import moe.shizuku.manager.AppConstants
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.receiver.ShizukuReceiverStarter
import moe.shizuku.manager.utils.SettingsPage
import moe.shizuku.manager.utils.ShizukuStateMachine
import moe.shizuku.manager.utils.ShizukuSystemApis
import moe.shizuku.manager.utils.UserHandleCompat
import rikka.shizuku.Shizuku

/**
 * Starts Shizuku in the background when one of the apps selected in settings comes to the foreground.
 * The event filter is narrowed to the selected packages, so the system never delivers events from other apps.
 *
 * If Shizuku came up because of such a start, it is stopped again once none of the selected apps
 * is in the foreground or running a foreground service. Process states are read through Shizuku itself.
 */
class AppTriggerAccessibilityService : AccessibilityService() {

    companion object {
        // Covers the gap between enqueueing AdbStartWorker and the state machine reaching STARTING
        private const val START_DEBOUNCE_MS = 30_000L
        // A trigger only claims a Shizuku start that happens within this window (AdbStartWorker may wait for Wi-Fi)
        private const val ARM_WINDOW_MS = 10 * 60_000L
        private const val CHECK_INTERVAL_MS = 30_000L
        private const val IDLE_CHECKS_BEFORE_STOP = 2

        // ActivityManager.PROCESS_STATE_* (hidden API): 0..5 covers persistent, top and foreground service states
        private const val PROCESS_STATE_PERSISTENT = 0
        private const val PROCESS_STATE_BOUND_FOREGROUND_SERVICE = 5

        private const val CHANNEL_ID = "app_trigger"
        private const val NOTIFICATION_ID = 1004

        private fun componentName(context: Context) =
            ComponentName(context, AppTriggerAccessibilityService::class.java).flattenToString()

        private fun enabledServices(context: Context): List<String> =
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?.split(":")
                ?.filter { it.isNotEmpty() }
                ?: emptyList()

        fun isEnabled(context: Context): Boolean =
            enabledServices(context).any { it.equals(componentName(context), ignoreCase = true) }

        /**
         * Turns the service on or off directly through secure settings.
         * Returns false when that needs WRITE_SECURE_SETTINGS and Shizuku doesn't have it.
         */
        fun setEnabled(context: Context, enable: Boolean): Boolean {
            if (isEnabled(context) == enable) return true
            if (context.checkSelfPermission(WRITE_SECURE_SETTINGS) != PackageManager.PERMISSION_GRANTED) return false

            val name = componentName(context)
            val services = enabledServices(context).filterNot { it.equals(name, ignoreCase = true) }
                .let { if (enable) it + name else it }
            return try {
                Settings.Secure.putString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    services.joinToString(":"),
                )
                if (enable) Settings.Secure.putInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
                isEnabled(context) == enable
            } catch (e: SecurityException) {
                Log.w(AppConstants.TAG, "Failed to toggle app trigger accessibility service", e)
                false
            }
        }

        /**
         * The system can turn the service off behind our back (seen after an OS update), which silently breaks
         * the app trigger. Turns it back on, or tells the user when that isn't possible.
         */
        fun ensureEnabled(context: Context) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (ShizukuSettings.getAppTriggerPackages().isEmpty() || setEnabled(context, true)) {
                nm.cancel(NOTIFICATION_ID)
                return
            }

            Log.w(AppConstants.TAG, "App trigger accessibility service is off and can't be turned on")
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.settings_app_trigger),
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
            val intent = SettingsPage.Accessibility.buildIntent(context)
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_system_icon)
                .setContentTitle(context.getString(R.string.app_trigger_disabled_notification_title))
                .setContentText(context.getString(R.string.app_trigger_disabled_notification_text))
                .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.app_trigger_disabled_notification_text)))
                .setAutoCancel(true)
                .setContentIntent(
                    PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                )
                .build()
            nm.notify(NOTIFICATION_ID, notification)
        }
    }

    private var lastStartAttempt = 0L
    private var armUntil = 0L
    private var idleChecks = 0

    private val handlerThread = HandlerThread("AppTrigger").apply { start() }
    private val handler = Handler(handlerThread.looper)
    private val checkRunnable = Runnable { checkApps() }

    private val stateListener: (ShizukuStateMachine.State) -> Unit = { state ->
        handler.post { onStateChanged(state) }
    }

    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == ShizukuSettings.Keys.KEY_APP_TRIGGER_PACKAGES) applyPackageFilter()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        ShizukuSettings.getPreferences().registerOnSharedPreferenceChangeListener(preferenceListener)
        applyPackageFilter()
        ShizukuStateMachine.addListener(stateListener)
    }

    override fun onDestroy() {
        ShizukuStateMachine.removeListener(stateListener)
        ShizukuSettings.getPreferences().unregisterOnSharedPreferenceChangeListener(preferenceListener)
        handlerThread.quitSafely()
        super.onDestroy()
    }

    private fun applyPackageFilter() {
        val info = serviceInfo ?: return
        // An empty filter would mean "all apps", so fall back to our own package
        info.packageNames = ShizukuSettings.getAppTriggerPackages().ifEmpty { setOf(packageName) }.toTypedArray()
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        if (packageName !in ShizukuSettings.getAppTriggerPackages()) return

        handler.post { idleChecks = 0 }
        if (ShizukuStateMachine.get() == ShizukuStateMachine.State.STARTING || Shizuku.pingBinder()) return

        val now = SystemClock.elapsedRealtime()
        if (lastStartAttempt != 0L && now - lastStartAttempt < START_DEBOUNCE_MS) return
        lastStartAttempt = now
        handler.post { armUntil = now + ARM_WINDOW_MS }

        Log.i(AppConstants.TAG, "Starting Shizuku for $packageName")
        ShizukuReceiverStarter.start(this)
    }

    private fun onStateChanged(state: ShizukuStateMachine.State) {
        when (state) {
            ShizukuStateMachine.State.RUNNING -> {
                if (SystemClock.elapsedRealtime() < armUntil) ShizukuSettings.setAppTriggerArmed(true)
                armUntil = 0L
                if (ShizukuSettings.getAppTriggerArmed()) {
                    idleChecks = 0
                    scheduleCheck()
                }
            }
            // Any deliberate stop ends the trigger session; after a crash keep it so a watchdog restart resumes it
            ShizukuStateMachine.State.STOPPED -> {
                ShizukuSettings.setAppTriggerArmed(false)
                handler.removeCallbacks(checkRunnable)
            }
            ShizukuStateMachine.State.CRASHED -> handler.removeCallbacks(checkRunnable)
            else -> {}
        }
    }

    private fun scheduleCheck() {
        handler.removeCallbacks(checkRunnable)
        handler.postDelayed(checkRunnable, CHECK_INTERVAL_MS)
    }

    private fun checkApps() {
        if (!ShizukuSettings.getAppTriggerArmed() || !ShizukuStateMachine.isRunning()) return
        // Keep polling while the option is off, so turning it on applies to the current session
        if (!ShizukuSettings.getAppTriggerStop()) {
            idleChecks = 0
            scheduleCheck()
            return
        }

        val userId = UserHandleCompat.myUserId()
        val active = try {
            ShizukuSettings.getAppTriggerPackages().any {
                ShizukuSystemApis.getPackageProcessState(it, userId) in
                    PROCESS_STATE_PERSISTENT..PROCESS_STATE_BOUND_FOREGROUND_SERVICE
            }
        } catch (e: Throwable) {
            Log.w(AppConstants.TAG, "Failed to read app process states", e)
            true
        }

        idleChecks = if (active) 0 else idleChecks + 1
        if (idleChecks < IDLE_CHECKS_BEFORE_STOP) {
            scheduleCheck()
            return
        }

        Log.i(AppConstants.TAG, "Selected apps closed, stopping Shizuku")
        ShizukuStateMachine.stop()
    }

    override fun onInterrupt() {}
}
