package com.savemymoney.app.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import com.savemymoney.app.data.PaymentStore
import com.savemymoney.app.data.Prefs
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Watches which app is on screen and shows the monthly total when Google Wallet comes
 * to the front. It only looks at app switches; it never reads what's on the screen.
 */
class WalletWatcherService : AccessibilityService() {

    private val scope = MainScope()
    private var banner: SpendingBanner? = null
    private var screen: SpendingScreen? = null
    private var walletInFront = false

    /** Windows that pop up over an app without the user leaving it. */
    private var passingPackages: Set<String> = emptySet()

    /** Don't leave the full screen up over the lock screen. */
    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Opening Wallet again from the lock screen is a fresh open.
            walletInFront = false
            screen?.hide(animate = false)
            banner?.hide(animate = false)
        }
    }

    override fun onServiceConnected() {
        val banner = SpendingBanner(this)
        val screen = SpendingScreen(this)
        this.banner = banner
        this.screen = screen
        passingPackages = setOf(
            packageName,
            "android",
            "com.android.systemui",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
        ) + keyboardPackages()
        ContextCompat.registerReceiver(
            this,
            screenOff,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        running = this
        // A payment made while the numbers are up (e.g. tapping to pay) updates them straight away.
        scope.launch {
            PaymentStore.get(this@WalletWatcherService).changes.drop(1).collect {
                banner.refresh()
                screen.refresh()
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        val className = event.className?.toString()
        if (WalletApps.isTapToPayWindow(packageName, className)) {
            // Paying by tapping the phone: get out of the way of the payment screen.
            screen?.hide()
            return
        }
        val isWallet = WalletApps.isWalletWindow(packageName, className)
        if (!isWallet && packageName in passingPackages) return
        if (isWallet && !walletInFront && Prefs(this).bannerEnabled) showSpending()
        // Left Wallet (home, recents, another app): don't leave the full screen behind.
        if (!isWallet) screen?.hide()
        walletInFront = isWallet
    }

    private fun showSpending() {
        if (Prefs(this).fullScreen) screen?.show() else banner?.show()
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (running === this) running = null
        scope.cancel()
        runCatching { unregisterReceiver(screenOff) }
        banner?.hide(animate = false)
        screen?.hide(animate = false)
        super.onDestroy()
    }

    private fun keyboardPackages(): List<String> =
        try {
            getSystemService(InputMethodManager::class.java).enabledInputMethodList.map { it.packageName }
        } catch (e: RuntimeException) {
            emptyList()
        }

    companion object {
        private var running: WalletWatcherService? = null

        /** Shows what Wallet will show, for the preview button. False if the service isn't turned on. */
        fun preview(): Boolean {
            val service = running ?: return false
            service.showSpending()
            return true
        }
    }
}
