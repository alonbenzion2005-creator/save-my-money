package com.savemymoney.app.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
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
    private var walletInFront = false

    /** Windows that pop up over an app without the user leaving it. */
    private var passingPackages: Set<String> = emptySet()

    override fun onServiceConnected() {
        val banner = SpendingBanner(this)
        this.banner = banner
        passingPackages = setOf(
            packageName,
            "android",
            "com.android.systemui",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
        ) + keyboardPackages()
        running = this
        // A payment made while the banner is up (e.g. tapping to pay) updates it straight away.
        scope.launch { PaymentStore.get(this@WalletWatcherService).changes.drop(1).collect { banner.refresh() } }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        val isWallet = WalletApps.isWalletWindow(packageName, event.className?.toString())
        if (!isWallet && packageName in passingPackages) return
        if (isWallet && !walletInFront && Prefs(this).bannerEnabled) banner?.show()
        walletInFront = isWallet
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (running === this) running = null
        scope.cancel()
        banner?.hide(animate = false)
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

        /** Shows the banner now, for the preview button. False if the service isn't turned on. */
        fun preview(): Boolean {
            val banner = running?.banner ?: return false
            banner.show()
            return true
        }
    }
}
