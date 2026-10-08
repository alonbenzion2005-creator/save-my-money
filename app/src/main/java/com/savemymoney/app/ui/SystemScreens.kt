package com.savemymoney.app.ui

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.savemymoney.app.service.WalletNotificationListener
import com.savemymoney.app.service.WalletWatcherService

/** Checks for, and shortcuts to, the system switches the app needs. */
object SystemScreens {

    fun hasNotificationAccess(context: Context): Boolean =
        context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

    fun hasWalletWatcher(context: Context): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val me = ComponentName(context, WalletWatcherService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    fun openNotificationAccess(context: Context) {
        val component = ComponentName(context, WalletNotificationListener::class.java)
        val ourSwitch = if (Build.VERSION.SDK_INT >= 30) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component.flattenToString())
        } else {
            null
        }
        open(context, ourSwitch, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    fun openAccessibility(context: Context) = open(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    fun openAppInfo(context: Context) = open(
        context,
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
    )

    /** Opens the first of [intents] the phone can handle. */
    private fun open(context: Context, vararg intents: Intent?) {
        for (intent in intents.filterNotNull()) {
            try {
                context.startActivity(intent)
                return
            } catch (e: ActivityNotFoundException) {
                // Try the next, more general screen.
            }
        }
    }
}
