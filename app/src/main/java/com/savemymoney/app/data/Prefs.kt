package com.savemymoney.app.data

import android.content.Context
import androidx.core.content.edit

class Prefs(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Monthly budget in the main currency, or 0 for none. */
    var budgetCents: Long
        get() = prefs.getLong("budget_cents", 0)
        set(value) = prefs.edit { putLong("budget_cents", value) }

    /** A currency the user picked, or null to work it out automatically. */
    var currencyOverride: String?
        get() = prefs.getString("currency", null)?.takeIf { Currencies.isValid(it) }
        set(value) = prefs.edit { if (value == null) remove("currency") else putString("currency", value) }

    var bannerEnabled: Boolean
        get() = prefs.getBoolean("banner_enabled", true)
        set(value) = prefs.edit { putBoolean("banner_enabled", value) }
}
