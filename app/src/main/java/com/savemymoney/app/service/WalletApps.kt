package com.savemymoney.app.service

object WalletApps {
    const val GOOGLE_WALLET = "com.google.android.apps.walletnfcrel"

    /** Some tap-to-pay notifications are posted by Play services rather than the Wallet app. */
    const val PLAY_SERVICES = "com.google.android.gms"

    private const val SYSTEM_UI = "com.android.systemui"

    /**
     * True for the Google Wallet app; for Wallet screens that run inside Play services
     * (e.g. card details, com.google.android.gms.pay.main.PayActivity), so moving between
     * them doesn't count as leaving Wallet; and for the cards screen the system shows from
     * the lock screen / quick settings Wallet button (a System UI activity).
     */
    fun isWalletWindow(packageName: String, className: String?): Boolean = when (packageName) {
        GOOGLE_WALLET -> true
        PLAY_SERVICES -> className?.startsWith("com.google.android.gms.pay.") == true
        SYSTEM_UI -> className?.contains(".wallet.", ignoreCase = true) == true
        else -> false
    }

    /**
     * True for the screen Play services shows while paying by tapping the phone on a reader
     * (com.google.android.gms.tapandpay.tap.TapActivity). Nothing should cover it: it can
     * ask the user to unlock or confirm the payment.
     */
    fun isTapToPayWindow(packageName: String, className: String?): Boolean =
        packageName == PLAY_SERVICES && className?.startsWith("com.google.android.gms.tapandpay.") == true

    /**
     * Whether a notification from [packageName] on [channelId] can be a purchase. Wallet's
     * purchases come on "tapandpay.transactions.low"; its promotions and tips never are.
     * Play services posts plenty of other notifications, so for it only that channel counts.
     */
    fun isPurchaseChannel(packageName: String, channelId: String?): Boolean {
        val channel = channelId.orEmpty()
        val isTransactions = channel.startsWith("tapandpay.transactions")
        return when (packageName) {
            GOOGLE_WALLET -> isTransactions || ("promotion" !in channel && "tips" !in channel)
            PLAY_SERVICES -> isTransactions
            else -> false
        }
    }
}
