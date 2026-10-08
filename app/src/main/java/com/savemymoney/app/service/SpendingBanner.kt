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
import android.view.animation.DecelerateInterpolator
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isVisible
import com.savemymoney.app.R
import com.savemymoney.app.data.BalanceEstimate
import com.savemymoney.app.data.MonthSpending
import com.savemymoney.app.data.monthName
import java.time.YearMonth

/**
 * The card that slides in at the top of the screen with this month's total.
 *
 * It's an accessibility overlay rather than a normal "draw over other apps" window:
 * payment apps can hide normal overlays, but not this kind, and it needs no extra permission.
 */
class SpendingBanner(private val service: AccessibilityService) : SpendingDisplay {

    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val hideLater = Runnable { hide() }
    private var view: View? = null

    override fun show() {
        val v = view ?: createView().also { if (!attach(it)) return }
        bind(v)
        handler.removeCallbacks(hideLater)
        handler.postDelayed(hideLater, VISIBLE_MS)
    }

    override fun refresh() {
        view?.let(::bind)
    }

    override fun hide(animate: Boolean) {
        handler.removeCallbacks(hideLater)
        val v = view ?: return
        view = null
        val remove = Runnable { runCatching { windowManager.removeView(v) } }
        if (animate) {
            v.animate().alpha(0f).translationY(-slideDistance()).setDuration(200).withEndAction(remove).start()
        } else {
            remove.run()
        }
    }

    private fun createView(): View {
        val themed = ContextThemeWrapper(service, R.style.Theme_SaveMyMoney)
        val v = LayoutInflater.from(themed).inflate(R.layout.spending_banner, null)
        v.setOnClickListener { hide() }
        return v
    }

    private fun attach(v: View): Boolean {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP
            y = topOffset()
            if (Build.VERSION.SDK_INT >= 28) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        v.alpha = 0f
        v.translationY = -slideDistance()
        try {
            windowManager.addView(v, params)
        } catch (e: RuntimeException) {
            return false
        }
        view = v
        v.animate().alpha(1f).translationY(0f).setDuration(250).setInterpolator(DecelerateInterpolator()).start()
        return true
    }

    private fun bind(v: View) {
        val spending = MonthSpending.load(service, YearMonth.now())
        v.findViewById<TextView>(R.id.banner_label).text =
            service.getString(R.string.banner_label, spending.month.monthName())
        v.findViewById<TextView>(R.id.banner_total).text = spending.mainTotal
        v.findViewById<TextView>(R.id.banner_others).apply {
            text = spending.otherTotalsLine
            isVisible = spending.otherTotals.isNotEmpty()
        }
        val bar = v.findViewById<ProgressBar>(R.id.banner_budget)
        val detail = v.findViewById<TextView>(R.id.banner_detail)
        if (spending.hasBudget) {
            bar.isVisible = true
            bar.max = 1000
            bar.progress = (spending.budgetFraction * 1000).toInt()
            val color = service.getColor(if (spending.overBudget) R.color.banner_over else R.color.banner_ok)
            bar.progressTintList = ColorStateList.valueOf(color)
            detail.text = "${spending.countLine} · ${spending.budgetLine}"
        } else {
            bar.isVisible = false
            detail.text = spending.countLine
        }
        BalanceEstimate.load(service)?.let { balance ->
            detail.append("\n" + service.getString(R.string.banner_balance, balance.estimate))
        }
    }

    private fun topOffset(): Int {
        val statusBar = if (Build.VERSION.SDK_INT >= 30) {
            windowManager.currentWindowMetrics.windowInsets
                .getInsetsIgnoringVisibility(WindowInsets.Type.statusBars() or WindowInsets.Type.displayCutout())
                .top
        } else {
            dp(24f).toInt()
        }
        return statusBar + dp(8f).toInt()
    }

    private fun slideDistance() = dp(48f)

    private fun dp(value: Float) = value * service.resources.displayMetrics.density

    private companion object {
        const val VISIBLE_MS = 6_000L
    }
}
