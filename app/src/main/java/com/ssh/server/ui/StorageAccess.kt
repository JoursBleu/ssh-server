package com.ssh.server.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings

internal object StorageAccess {
    val supported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    fun isGranted(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
        Environment.isExternalStorageManager()

    fun openSettings(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        val destinations = listOf(
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${context.packageName}")),
            Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
        )
        for (intent in destinations) {
            try {
                context.startActivity(intent)
                return true
            } catch (_: ActivityNotFoundException) {
                // Some manufacturers expose only the list of eligible apps.
            } catch (_: SecurityException) {
                // Try the general settings page if the app-specific page is blocked.
            }
        }
        return false
    }
}
