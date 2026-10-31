package moe.shizuku.manager.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import moe.shizuku.manager.AppConstants
import moe.shizuku.manager.MainActivity
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.receiver.ShizukuReceiverStarter
import moe.shizuku.manager.utils.ShizukuStateMachine
import moe.shizuku.manager.utils.ShizukuStateMachine.State
import rikka.shizuku.Shizuku

/**
 * Shows a silent status notification while Shizuku is starting, running or stopping, and removes it once
 * Shizuku is stopped. If Shizuku dies unexpectedly, a dismissible "crashed" notification stays instead.
 * On Android 16+ it is requested as a Live Update, so the state also shows as a status bar chip.
 * The foreground service keeps this process alive while Shizuku is active, so the state shown can't go stale.
 */
class ShizukuStatusService : Service() {

    companion object {
        private const val CHANNEL_ID = "shizuku_status"
        private const val NOTIFICATION_ID = 1003
        private const val ACTION_STOP_SHIZUKU = "moe.shizuku.manager.action.STOP_SHIZUKU"

        private val activeStates = setOf(State.STARTING, State.RUNNING, State.STOPPING)

        fun register(context: Context) {
            val appContext = context.applicationContext
            ShizukuStateMachine.addListener { onStateChanged(appContext, it) }
        }

        fun refresh(context: Context) = onStateChanged(context.applicationContext, ShizukuStateMachine.get())

        private fun onStateChanged(context: Context, state: State) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            // The background start's "Starting Shizuku…" notification can outlive the start, drop it once running
            if (state == State.RUNNING) nm.cancel(ShizukuReceiverStarter.NOTIFICATION_ID)

            // The service leaves the foreground itself on the same state change
            if (!ShizukuSettings.getStatusNotification() || state == State.STOPPED) {
                nm.cancel(NOTIFICATION_ID)
                return
            }
            if (state == State.CRASHED) {
                nm.notify(NOTIFICATION_ID, buildNotification(context, state))
                return
            }

            nm.notify(NOTIFICATION_ID, buildNotification(context, state))
            try {
                context.startForegroundService(Intent(context, ShizukuStatusService::class.java))
            } catch (e: IllegalStateException) {
                // Background start not allowed: the plain notification above still shows the state
                Log.w(AppConstants.TAG, "Couldn't start status service", e)
            }
        }

        private fun buildNotification(context: Context, state: State): Notification {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.status_notification_channel),
                    NotificationManager.IMPORTANCE_LOW
                )
            )

            val (title, chip) = when (state) {
                State.STARTING -> R.string.status_notification_starting to R.string.status_notification_chip_starting
                State.STOPPING -> R.string.status_notification_stopping to R.string.status_notification_chip_stopping
                State.CRASHED -> R.string.status_notification_crashed to 0
                else -> R.string.status_notification_running to R.string.status_notification_chip_running
            }

            val launchIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_system_icon)
                .setContentTitle(context.getString(title))
                .setOngoing(true)
                .setSilent(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .setContentIntent(
                    PendingIntent.getActivity(context, 0, launchIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                )

            if (state == State.CRASHED) {
                return builder
                    .setContentText(context.getString(R.string.status_notification_crashed_text))
                    .setOngoing(false)
                    .setAutoCancel(true)
                    .setShowWhen(true)
                    .setCategory(NotificationCompat.CATEGORY_ERROR)
                    .build()
            }

            if (state == State.RUNNING) {
                val stopIntent = Intent(context, ShizukuStatusService::class.java).setAction(ACTION_STOP_SHIZUKU)
                builder.addAction(
                    R.drawable.ic_close_24,
                    context.getString(R.string.status_notification_stop),
                    PendingIntent.getService(context, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                )
            } else {
                builder.setProgress(0, 0, true)
            }

            val notification = builder.build()
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) return notification

            return Notification.Builder.recoverBuilder(context, notification)
                .setRequestPromotedOngoing(true)
                .setShortCriticalText(context.getString(chip))
                .build()
        }
    }

    private var listening = false

    private val stateListener: (State) -> Unit = {
        if (it !in activeStates || !ShizukuSettings.getStatusNotification()) {
            stopForeground(if (it == State.CRASHED) STOP_FOREGROUND_DETACH else STOP_FOREGROUND_REMOVE)
            // Repost once detached, so the "crashed" notification doesn't keep the foreground service's ongoing flag
            if (it == State.CRASHED && ShizukuSettings.getStatusNotification()) {
                val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(NOTIFICATION_ID, buildNotification(this, it))
            }
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SHIZUKU) {
            if (ShizukuStateMachine.isRunning()) {
                ShizukuStateMachine.set(State.STOPPING)
                runCatching { Shizuku.exit() }
            }
            return START_NOT_STICKY
        }

        // Always enter the foreground first: a startForegroundService() call must be answered even if Shizuku already stopped
        val state = ShizukuStateMachine.get().takeIf { it in activeStates } ?: State.RUNNING
        val notification = buildNotification(this, state)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        if (!listening) {
            listening = true
            ShizukuStateMachine.addListener(stateListener)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        ShizukuStateMachine.removeListener(stateListener)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
