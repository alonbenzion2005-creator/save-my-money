package com.savemymoney.app.service

import android.accessibilityservice.AccessibilityService
import android.content.res.ColorStateList
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isVisible
import com.savemymoney.app.R
import com.savemymoney.app.data.MonthSpending
import com.savemymoney.app.data.Prefs
import com.savemymoney.app.data.monthName
import java.time.YearMonth

/**
 * A full-screen page with this month's spending, shown in front of Google Wallet
 * until "Continue to Wallet" (or back) is pressed, or the countdown runs out.
 */
class SpendingScreen(private val service: AccessibilityService) : SpendingDisplay {

    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var view: OverlayRootLayout? = null
    private var secondsLeft = 0

    private val countdown = object : Runnable {
        override fun run() {
            secondsLeft--
            if (secondsLeft <= 0) {
                hide()
            } else {
                updateButton()
                handler.postDelayed(this, 1_000)
            }
        }
    }

    override fun show() {
        val v = view ?: createView().also { if (!attach(it)) return }
        bind(v)
        handler.removeCallbacks(countdown)
        secondsLeft = Prefs(service).autoCloseSeconds
        updateButton()
        if (secondsLeft > 0) handler.postDelayed(countdown, 1_000)
    }

    override fun refresh() {
        view?.let(::bind)
    }

    override fun hide(animate: Boolean) {
        handler.removeCallbacks(countdown)
        val v = view ?: return
        view = null
        val remove = Runnable { runCatching { windowManager.removeView(v) } }
        if (animate) {
            v.animate().alpha(0f).setDuration(150).withEndAction(remove).start()
        } else {
            remove.run()
        }
    }

    private fun createView(): OverlayRootLayout {
        val themed = ContextThemeWrapper(service, R.style.Theme_SaveMyMoney)
        val v = LayoutInflater.from(themed).inflate(R.layout.spending_screen, null) as OverlayRootLayout
        v.onBack = { hide() }
        v.findViewById<View>(R.id.screen_continue).setOnClickListener { hide() }
        return v
    }

    private fun attach(v: OverlayRootLayout): Boolean {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            // Focusable (no FLAG_NOT_FOCUSABLE) so back comes to us rather than Wallet, but not
            // touch-modal, so taps outside the window still reach whatever is there.
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            if (Build.VERSION.SDK_INT >= 30) {
                // Cover everything, status bar included. This window sits above the system bars,
                // so it doesn't get their insets and has to be sized by hand.
                fitInsetsTypes = 0
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                if (hasButtonNavigation()) {
                    // Stop at the back/home/recents buttons so they can still be tapped.
                    val metrics = windowManager.currentWindowMetrics
                    val nav = metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars())
                    gravity = Gravity.TOP or Gravity.LEFT
                    x = nav.left
                    y = 0
                    width = metrics.bounds.width() - nav.left - nav.right
                    height = metrics.bounds.height() - nav.bottom
                }
            } else if (Build.VERSION.SDK_INT >= 28) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        // Keep the content clear of whatever bars the window does cover.
        val bars = coveredInsets()
        val side = dp(28f)
        v.findViewById<View>(R.id.screen_content)
            .setPadding(side + bars[0], dp(16f) + bars[1], side + bars[2], dp(24f) + bars[3])
        v.alpha = 0f
        try {
            windowManager.addView(v, params)
        } catch (e: RuntimeException) {
            return false
        }
        view = v
        v.animate().alpha(1f).setDuration(150).start()
        return true
    }

    private fun bind(v: View) {
        val spending = MonthSpending.load(service, YearMonth.now())
        v.setBackgroundColor(
            service.getColor(if (spending.overBudget) R.color.screen_background_over else R.color.screen_background),
        )
        v.findViewById<TextView>(R.id.screen_label).text =
            service.getString(R.string.banner_label, spending.month.monthName())
        v.findViewById<TextView>(R.id.screen_total).text = spending.mainTotal
        v.findViewById<TextView>(R.id.screen_others).apply {
            text = spending.otherTotalsLine
            isVisible = spending.otherTotals.isNotEmpty()
        }
        v.findViewById<TextView>(R.id.screen_detail).text = "${spending.countLine} · ${spending.todayLine}"

        v.findViewById<View>(R.id.screen_budget_group).isVisible = spending.hasBudget
        if (spending.hasBudget) {
            val bar = v.findViewById<ProgressBar>(R.id.screen_budget)
            bar.max = 1000
            bar.progress = (spending.budgetFraction * 1000).toInt()
            val color = service.getColor(if (spending.overBudget) R.color.banner_over else R.color.banner_ok)
            bar.progressTintList = ColorStateList.valueOf(color)
            v.findViewById<TextView>(R.id.screen_budget_text).text = spending.budgetLine
        }
    }

    private fun updateButton() {
        val button = view?.findViewById<TextView>(R.id.screen_continue) ?: return
        button.text = if (secondsLeft > 0) {
            service.getString(R.string.screen_continue_countdown, secondsLeft)
        } else {
            service.getString(R.string.screen_continue)
        }
    }

    /** True when the navigation bar has buttons (back/home/recents) rather than the gesture handle. */
    private fun hasButtonNavigation(): Boolean {
        if (Build.VERSION.SDK_INT < 30) return true
        val tappable = windowManager.currentWindowMetrics.windowInsets
            .getInsetsIgnoringVisibility(WindowInsets.Type.tappableElement())
        // The status bar is a tappable element too, so only the bottom and sides tell.
        return tappable.bottom > 0 || tappable.left > 0 || tappable.right > 0
    }

    /** Left, top, right and bottom space inside the window taken by system bars and the camera cutout. */
    private fun coveredInsets(): IntArray {
        if (Build.VERSION.SDK_INT < 30) return intArrayOf(0, dp(24f), 0, 0)
        val insets = windowManager.currentWindowMetrics.windowInsets
        val all = insets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
        if (!hasButtonNavigation()) return intArrayOf(all.left, all.top, all.right, all.bottom)
        // The window ends where the button bar starts, so leave that part out.
        val nav = insets.getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars())
        return intArrayOf(
            (all.left - nav.left).coerceAtLeast(0),
            all.top,
            (all.right - nav.right).coerceAtLeast(0),
            (all.bottom - nav.bottom).coerceAtLeast(0),
        )
    }

    private fun dp(value: Float): Int = (value * service.resources.displayMetrics.density).toInt()
}
