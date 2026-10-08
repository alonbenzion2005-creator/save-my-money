# Save My Money

An Android app that shows **how much you've spent this month** full screen whenever you open **Google Wallet**, before you get to your cards. It was built for a Pixel 7a and should work on any phone running Android 8 or newer.

```
┌────────────────────────────────┐
│          SAVE MY MONEY         │
│                                │
│        Spent in October        │
│          ₪1,234.50             │
│  23 payments · ₪85.00 today    │
│                                │
│  ▓▓▓▓▓▓▓▓▓▓▓▓▓░░░░░░░░░░░░░░   │
│   ₪765.50 left of ₪2,000.00    │
│                                │
│  ┌──────────────────────────┐  │
│  │  Continue to Wallet (10) │  │
│  └──────────────────────────┘  │
└────────────────────────────────┘
```

The screen turns red when you're over budget. Tap **Continue to Wallet** or press back to go on to Wallet. You can also set it to close by itself after 5 or 10 seconds. If you prefer, Settings can switch it to a small banner at the top instead.

## How it works

Google Wallet doesn't let other apps read your payment history. So the app works like this:

1. **It reads Wallet's payment notifications.** After each tap-to-pay, Google Wallet shows a notification such as "Shufersal · ₪45.90 with Mastercard •••• 1234". The app saves the amount from it. English, Hebrew and other formats are all understood, including ₪, $, €, £, ש״ח, ILS and so on.
2. **It notices when Wallet opens.** When Google Wallet comes to the front, the app covers it with this month's total. It also shows what you spent today and, if you set a budget, how much is left. If a payment comes in while the screen is up, the numbers update straight away.

The app also has its own screen. There you can browse months, see each payment, add cash or other payments by hand, fix or delete entries, and set a monthly budget.

**Privacy:** the app has no internet permission, so nothing it records can leave your phone. It only keeps notifications from Google Wallet and from payment notifications posted by Google Play services.

## Install it on your phone

1. On your phone, open **https://github.com/alonbenzion2005-creator/save-my-money/releases/latest** and tap **SaveMyMoney.apk** to download it.
2. Open the downloaded file. If Android asks, allow your browser to install unknown apps. If Play Protect warns about an unknown app, choose **Install anyway**. It warns because the app wasn't installed from the Play Store.
3. Open **Save My Money** and follow its two setup steps:
   - **Allow notification access** and turn on *Save My Money – Wallet payments*.
   - **Open Accessibility settings**, tap *Save My Money – Wallet spending* (under *Downloaded apps*), and turn it on.

   > **Switch greyed out / "Restricted setting"?** Android blocks these two switches for apps installed from a file until you allow it. Go to **Settings → Apps → Save My Money**, tap **⋮** (top right), choose **Allow restricted settings**, then go back and turn the switches on. The ⋮ option only appears after you've tried the switch once.
4. Make sure Google Wallet's notifications are on: **Settings → Notifications → App notifications → Google Wallet**. Also check the notification settings inside the Wallet app.
5. Back in the app, tap **Preview what Wallet will show** to see it.

Every push to this repo builds a fresh APK, and the link above always points to the newest one. Installing it again updates the app and keeps your data.

## Good to know

- **Only payments made after setup are counted automatically.** For anything earlier this month, or paid another way, use **Add**.
- **Is a payment missing or wrong?** Tap the bell icon in the app. It lists every Wallet notification the app saw and what it did with each one. Tap a payment to edit or delete it.
- **Several currencies** are totalled separately. The big number uses your main currency, which is the one you pay in most. You can change it in Settings.
- Refunds that Wallet reports are subtracted. Declined payments, cashback offers and balance updates are ignored.

## Building it yourself

GitHub Actions builds the app (see `.github/workflows/build.yml`). To build locally, install Android Studio or the Android SDK with JDK 17, then run:

```sh
./gradlew testDebugUnitTest assembleRelease
# → app/build/outputs/apk/release/app-release.apk
```

The code layout:

| Path | What it does |
| --- | --- |
| `app/src/main/java/com/savemymoney/app/parse/PaymentParser.kt` | Pulls amount, currency and merchant out of notification text |
| `.../service/WalletNotificationListener.kt` | Reads Google Wallet's notifications and records payments |
| `.../service/WalletWatcherService.kt` | Notices when Google Wallet opens |
| `.../service/SpendingScreen.kt` | The full-screen page shown in front of Wallet |
| `.../service/SpendingBanner.kt` | The small banner (the alternative style) |
| `.../data/` | Local SQLite storage, settings, currency and monthly totals |
| `.../ui/` | The app's screens (Jetpack Compose) |

The APK is signed with `app/signing.keystore`, a key kept in the repo so that each new build installs over the last one. That's fine for a personal app you install yourself. Use a private key if you ever publish it.
