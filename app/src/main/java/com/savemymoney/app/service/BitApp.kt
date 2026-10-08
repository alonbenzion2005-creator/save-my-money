package com.savemymoney.app.service

import java.util.Locale

/** bit (Bank Hapoalim's money-transfer app), which tells you by push or SMS when someone got money you sent. */
object BitApp {
    const val PACKAGE = "com.bnhp.payments.paymentsapp"

    private val SMS_APPS = setOf(
        "com.google.android.apps.messaging",
        "com.samsung.android.messaging",
        "com.android.mms",
        "com.android.messaging",
    )

    /** SMS apps title a notification with the sender's name. */
    private val SMS_SENDERS = setOf("bit", "ביט")

    /** True for bit's own notifications, and for SMS messages whose sender is "bit". */
    fun isBitNotification(packageName: String, title: String?): Boolean =
        packageName == PACKAGE ||
            (packageName in SMS_APPS && title?.trim()?.lowercase(Locale.ROOT) in SMS_SENDERS)

    /** Packages worth looking at before reading a notification's text. */
    fun mightBeBit(packageName: String): Boolean = packageName == PACKAGE || packageName in SMS_APPS
}
