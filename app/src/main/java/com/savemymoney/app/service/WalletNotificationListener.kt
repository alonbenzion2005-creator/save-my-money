package com.savemymoney.app.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.savemymoney.app.data.Currencies
import com.savemymoney.app.data.Money
import com.savemymoney.app.data.PaymentStore
import com.savemymoney.app.parse.BitParser
import com.savemymoney.app.parse.ParsedPayment
import com.savemymoney.app.parse.PaymentParser

/**
 * Records payments from notifications: the one Google Wallet posts after each tap-to-pay,
 * and bit's message (push or SMS) saying someone received money you sent.
 */
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
        val fromWallet = packageName == WalletApps.GOOGLE_WALLET || packageName == WalletApps.PLAY_SERVICES
        if (!fromWallet && !BitApp.mightBeBit(packageName)) return
        val notification = sbn.notification ?: return
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (fromWallet && !WalletApps.isPurchaseChannel(packageName, notification.channelId)) return

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

        // Other SMS (anything not from the sender "bit") is never read further or stored.
        if (!fromWallet && !BitApp.isBitNotification(packageName, title)) return

        val payment: ParsedPayment?
        val source: String
        if (fromWallet) {
            payment = PaymentParser.parse(title, texts, dollarCurrency = Currencies.dollarCurrency(this))
            source = PaymentStore.SOURCE_WALLET
        } else {
            payment = BitParser.parse(title, texts)
            source = PaymentStore.SOURCE_BIT
        }

        val store = PaymentStore.get(this)
        val result = when {
            payment == null && fromWallet -> "Not counted: no amount in this notification"
            payment == null -> "Not counted: not money you sent (or the wording wasn't recognised)"
            store.addNotificationPayment(payment, sbn.postTime, packageName, sbn.key, source) ->
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
