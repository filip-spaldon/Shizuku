package moe.shizuku.manager.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import android.os.PowerManager
import android.provider.Settings
import android.content.ContentResolver
import android.util.Log
import moe.shizuku.manager.utils.SettingsPage

object SettingsHelper {

    /**
     * Android 17 QPR3 requires MANAGE_DEBUGGING, which apps can't hold, to change adb related global
     * settings, so these writes can fail even with WRITE_SECURE_SETTINGS. Returns whether the write went through.
     */
    fun putGlobalInt(cr: ContentResolver, name: String, value: Int): Boolean =
        try {
            Settings.Global.putInt(cr, name, value)
        } catch (e: SecurityException) {
            Log.w("SettingsHelper", "Not allowed to set $name", e)
            false
        }

    fun putGlobalLong(cr: ContentResolver, name: String, value: Long): Boolean =
        try {
            Settings.Global.putLong(cr, name, value)
        } catch (e: SecurityException) {
            Log.w("SettingsHelper", "Not allowed to set $name", e)
            false
        }

    fun launchOrHighlightWirelessDebugging(context: Context) {
        if (EnvironmentUtils.isAdbEnabled()) {
            SettingsPage.Developer.WirelessDebugging.launch(context)
        } else SettingsPage.Developer.HighlightWirelessDebugging.launch(context)
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun requestIgnoreBatteryOptimizations(context: Context, launcher: ActivityResultLauncher<Intent>? = null) {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            setData(Uri.parse("package:" + context.packageName))
        }
        if (launcher != null) {
            launcher.launch(intent)
        } else {
            context.startActivity(intent)
        }
    }

}