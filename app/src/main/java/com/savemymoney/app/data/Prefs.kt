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

    /** Whether anything is shown when Google Wallet opens. */
    var bannerEnabled: Boolean
        get() = prefs.getBoolean("banner_enabled", true)
        set(value) = prefs.edit { putBoolean("banner_enabled", value) }

    /** Full screen in front of Wallet (true), or the small banner at the top (false). */
    var fullScreen: Boolean
        get() = prefs.getBoolean("full_screen", true)
        set(value) = prefs.edit { putBoolean("full_screen", value) }

    /** Seconds before the full screen closes by itself, or 0 to wait for the button. */
    var autoCloseSeconds: Int
        get() = prefs.getInt("auto_close_seconds", 10)
        set(value) = prefs.edit { putInt("auto_close_seconds", value) }

    /** The bank balance the user last typed in, or null if they haven't. */
    var enteredBalance: EnteredBalance?
        get() {
            if (!prefs.contains("balance_cents")) return null
            val currency = prefs.getString("balance_currency", null)?.takeIf { Currencies.isValid(it) } ?: return null
            return EnteredBalance(prefs.getLong("balance_cents", 0), currency, prefs.getLong("balance_time", 0))
        }
        set(value) = prefs.edit {
            if (value == null) {
                remove("balance_cents")
                remove("balance_currency")
                remove("balance_time")
            } else {
                putLong("balance_cents", value.cents)
                putString("balance_currency", value.currency)
                putLong("balance_time", value.timeMillis)
            }
        }
}

data class EnteredBalance(val cents: Long, val currency: String, val timeMillis: Long)
