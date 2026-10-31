package moe.shizuku.manager.utils

import android.os.ParcelFileDescriptor
import android.util.Log
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import java.io.File
import java.util.concurrent.TimeUnit

object ShizukuShell {

    private const val TAG = "ShizukuShell"
    private const val TIMEOUT_SECONDS = 10L
    private const val INPUT_TIMEOUT_SECONDS = 120L

    /**
     * Runs a command as the Shizuku server's user (shell or root) and waits for it.
     * Returns the exit code, or null when Shizuku isn't running or the command didn't finish.
     */
    fun run(vararg cmd: String): Int? {
        val binder = Shizuku.getBinder() ?: return null
        return try {
            val process = IShizukuService.Stub.asInterface(binder).newProcess(arrayOf(*cmd), null, null)
            try {
                if (process.waitForTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS.name)) process.exitValue() else null
            } finally {
                process.destroy()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to run ${cmd.joinToString(" ")}", e)
            null
        }
    }

    /**
     * Like [run], but streams [input] to the command's stdin.
     * Returns the exit code with the command's combined output, or null as [run] does.
     */
    fun run(input: File, vararg cmd: String): Pair<Int, String>? {
        val binder = Shizuku.getBinder() ?: return null
        return try {
            val process = IShizukuService.Stub.asInterface(binder).newProcess(arrayOf(*cmd), null, null)
            try {
                ParcelFileDescriptor.AutoCloseOutputStream(process.outputStream).use { stdin ->
                    input.inputStream().use { it.copyTo(stdin) }
                }
                val output = ParcelFileDescriptor.AutoCloseInputStream(process.inputStream).bufferedReader().readText() +
                    ParcelFileDescriptor.AutoCloseInputStream(process.errorStream).bufferedReader().readText()
                if (process.waitForTimeout(INPUT_TIMEOUT_SECONDS, TimeUnit.SECONDS.name)) process.exitValue() to output.trim() else null
            } finally {
                process.destroy()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to run ${cmd.joinToString(" ")}", e)
            null
        }
    }
}
