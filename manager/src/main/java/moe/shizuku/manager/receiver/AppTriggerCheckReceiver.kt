package moe.shizuku.manager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import moe.shizuku.manager.service.AppTriggerAccessibilityService

/** Rechecks the app trigger accessibility service after boot and after Shizuku is updated. */
class AppTriggerCheckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED ->
                AppTriggerAccessibilityService.ensureEnabled(context)
        }
    }
}
