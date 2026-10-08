package com.savemymoney.app.service

object WalletApps {
    const val GOOGLE_WALLET = "com.google.android.apps.walletnfcrel"

    /** Some tap-to-pay notifications are posted by Play services rather than the Wallet app. */
    const val PLAY_SERVICES = "com.google.android.gms"

    private const val SYSTEM_UI = "com.android.systemui"

    /**
     * True for the Google Wallet app, and for the cards screen the system shows from
     * the lock screen / quick settings Wallet button (a System UI activity).
     */
    fun isWalletWindow(packageName: String, className: String?): Boolean =
        packageName == GOOGLE_WALLET ||
            (packageName == SYSTEM_UI && className?.contains(".wallet.", ignoreCase = true) == true)
}
