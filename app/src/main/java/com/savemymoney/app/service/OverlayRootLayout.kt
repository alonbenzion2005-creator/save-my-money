package com.savemymoney.app.service

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.widget.FrameLayout

/**
 * Root of the full-screen overlay. The overlay window takes focus, so the back
 * button or gesture comes here, and closes the overlay rather than Wallet.
 */
class OverlayRootLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    var onBack: (() -> Unit)? = null

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) onBack?.invoke()
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}
