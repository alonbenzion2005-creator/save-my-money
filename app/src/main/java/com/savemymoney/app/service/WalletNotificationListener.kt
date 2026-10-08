package com.savemymoney.app.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.savemymoney.app.data.Currencies
import com.savemymoney.app.data.Money
import com.savemymoney.app.data.PaymentStore
import com.savemymoney.app.parse.PaymentParser

/** Reads the notification Google Wallet posts after each tap-to-pay and records the amount. */
class WalletNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        // Pick up payments that came in while the listener wasn't running (e.g. right after an update).
        val active = try {
            activeNotifications
        } catch (e: RuntimeException) {
            null
        }
        active?.forEach(::handle)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) = handle(sbn)

    private fun handle(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        if (packageName != WalletApps.GOOGLE_WALLET && packageName != WalletApps.PLAY_SERVICES) return
        val notification = sbn.notification ?: return
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val extras = notification.extras
        val title = (extras.getCharSequence(Notification.EXTRA_TITLE_BIG)
            ?: extras.getCharSequence(Notification.EXTRA_TITLE))?.toString()
        val texts = listOf(
            Notification.EXTRA_TEXT,
            Notification.EXTRA_BIG_TEXT,
            Notification.EXTRA_SUB_TEXT,
            Notification.EXTRA_SUMMARY_TEXT,
            Notification.EXTRA_INFO_TEXT,
        ).mapNotNull { extras.getCharSequence(it)?.toString() } +
            extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES).orEmpty().map { it.toString() }

        val fromWallet = packageName == WalletApps.GOOGLE_WALLET
        val payment = PaymentParser.parse(
            title,
            texts,
            requirePaymentWording = !fromWallet,
            dollarCurrency = Currencies.dollarCurrency(this),
        )
        // Play services posts plenty of unrelated notifications; only keep the payment ones.
        if (payment == null && !fromWallet) return

        val store = PaymentStore.get(this)
        val result = when {
            payment == null -> "Not counted: no amount in this notification"
            store.addWalletPayment(payment, sbn.postTime, packageName, sbn.key) ->
                "Counted " + Money.format(payment.amountCents, payment.currency)
            else -> "Already counted"
        }
        store.logNotification(
            timeMillis = sbn.postTime,
            packageName = packageName,
            notificationKey = sbn.key,
            title = title,
            body = texts.distinct().joinToString("\n").ifEmpty { null },
            result = result,
        )
    }
}
