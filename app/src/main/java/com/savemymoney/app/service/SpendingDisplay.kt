package com.savemymoney.app.service

/** Something that shows this month's spending over Google Wallet. */
interface SpendingDisplay {
    fun show()

    /** Re-reads the numbers if it's on screen (e.g. a payment just came in). */
    fun refresh()

    fun hide(animate: Boolean = true)
}
