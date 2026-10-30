# LookPe

**Today's PhonePe Business collection at a glance, on your home screen.**

LookPe is a small Android app and home-screen widget for shopkeepers who take UPI payments through
[PhonePe Business](https://play.google.com/store/apps/details?id=com.phonepe.app.business). It reads
PhonePe Business's own "money received" notifications and keeps a running list of the day's payments
with the total on top, with no digging through the PhonePe app and no paid SMS alerts.

It was built for a family shop, where the day's total sat several screens deep inside the PhonePe
Business app. Everything stays on the phone: **LookPe has no internet permission.**

## Features

- **Home-screen widget** with today's total and payments. It resizes from 2×1 (just the total) up to a full list, and shows what fits.
- **Hide amounts** behind fingerprint, face or PIN, on the widget and in the app. Amounts hide again after 60 seconds. Hiding can be turned off.
- **Payment list** for any day, with **vs yesterday**. Tap a payment for the payer, date and time, and the txn ID with a copy button.
- **Search** across all days by name, last digits of the number, txn ID or amount.
- **History:** a month calendar with each day's total, the month total, best day and week-by-week totals.
- **Daily summary** notification at 7, 8, 9 or 10 PM.
- **"Stopped reading" alert** if notification access is turned off or the system stops LookPe in the background.
- **Encrypted backups** that survive uninstalling the app. Automatic every 5, 12 or 24 hours, plus Back up now, Export and Import.
- **Skipped notifications** list: PhonePe Business notifications that weren't counted, with anything that looks like a payment flagged, so a change in PhonePe's wording gets noticed.

## How it works

```
PhonePe Business notification
        │  NotificationListenerService (only com.phonepe.app.business; everything else is ignored)
        ▼
PaymentParser ── amount, payer, txn ID ──► Room database ──► app screens + Glance widget
        │
        └─ not a payment (offer, settlement…) ──► Skipped list
```

### What a PhonePe Business notification contains

| Field | Example | Used for |
|---|---|---|
| Title | `Received Rs 160` / `Money received` | amount (cross-checked against the text) |
| Text | `You've received Rs 160 from ******1234 via PhonePe for txn T2601…` | amount, payer, txn ID |
| Payer | a name, or a masked number like `******1234` | display, search |
| Txn ID | `T2601011030000000000001` (not always present) | de-duplication, details |
| Time posted | 10:31 AM | the payment's time |

The payer's full number or UPI ID, their bank, and settlement status aren't in the notification, so
LookPe doesn't have them.

### Reading the amounts

PhonePe styles the digits in its notifications, and the style changes from one notification to the
next:

| Style | Looks like |
|---|---|
| Mathematical bold / monospace / double-struck / sans-bold | `𝟏𝟔𝟎` `𝟽𝟻𝟶` `𝟚𝟟𝟘` `𝟯𝟰𝟬` |
| Keycap emoji | `2️⃣9️⃣0️⃣` |

The parser NFKC-normalises the text and strips the emoji and keycap decorations before reading the
amount. An amount followed by a character it doesn't recognise is **rejected rather than cut
short**, and the title and text amounts must agree. A new digit style therefore lands in the
Skipped list instead of being logged as a wrong amount. When the parser improves, the app re-reads
the saved text of every payment on its next launch and corrects any amounts that change.

## Requirements

- Android 12 (API 31) or newer
- The PhonePe Business app, with its notifications switched on (silent is fine)
- Tested on a Poco X7 Pro (HyperOS 3, Android 16)

## Install and set up

LookPe isn't on the Play Store. Build it (see below) or install a release APK.

1. Install the APK. `adb install LookPe.apk` is the simplest route.
2. Open LookPe, tap ⚙️ and follow **Finish setup**:
   - **Notification access:** turn LookPe on. Android 13+ greys this switch out for apps installed from a file. If so, go to **App info → ⋮ → Allow restricted settings** and try again.
   - **Battery:** set to *No restrictions*, otherwise the system may stop LookPe in the background.
   - **Notifications:** allow, for the daily summary and the "stopped reading" alert.
3. On Xiaomi / HyperOS / MIUI, also turn on **Autostart** and lock LookPe in Recents.
4. **Phone setup → Add widget** to place the widget.

LookPe only sees payments that arrive as notifications after it's set up. It can't read the PhonePe
app's history.

## Backups

Backups are `.lookpe` files encrypted with AES-256-GCM. The file header is authenticated, so a
changed or foreign file is rejected.

- **Automatic** backups (every 5, 12 or 24 hours; 12 by default) go to `Download/LookPe`, which Android keeps when an app is uninstalled. They're skipped when nothing changed or there are no payments, so a fresh reinstall never writes an empty backup over the real one. The newest 5 are kept.
- **Export** saves a backup anywhere (Drive, a chat to yourself…).
- **Import** merges: payments already in the app are skipped, so importing the same file twice is harmless.

**After an accidental uninstall:** reinstall the same APK, do the setup, then **Settings → Backup →
Import** → `Download/LookPe` → the newest file.

## Building from source

Requires JDK 17+ and the Android SDK (compile SDK 36.1).

```bash
git clone https://github.com/debz-g/LookPe.git
cd LookPe
./gradlew assembleRelease        # app/build/outputs/apk/release/app-release.apk
```

### The backup key (`backup.key`)

The encryption key for backups is **not in this repository**. It's read at build time from
`backup.key` in the project root (64 hex characters) and compiled into the app as two masked halves.

- On a fresh clone, the first build creates a random `backup.key` for you. Your builds can only open backups made by your builds.
- **Keep that file safe and keep using it.** If it changes, backups made with the old key can't be restored.
- It's git-ignored. Never commit it: anyone with the key can read backups made with it.

A key built into an APK stops casual access, but someone determined can extract it from the APK. It
can't live in the Android Keystore, because Keystore keys are deleted with the app and backups have
to survive a reinstall.

### Signing and updates

Release builds are signed with the local debug key, which is fine for sideloading. To install an
update over an existing copy, build on the **same machine** (same `~/.android/debug.keystore`) and
raise `versionCode` in `app/build.gradle.kts`.

## Tests

```bash
./gradlew check                  # lint, unit tests, design rules
```

- **Parser:** every digit style above, payer and txn ID extraction, and rejection of settlements, promos, mismatched or unreadable amounts.
- **Backup format:** round trip, contents not readable in the file, tampering rejected.
- **Repair:** misread amounts are corrected from the saved notification text.
- **Widget:** each size shows the right content (Glance test harness under Robolectric).
- **Daily summary:** text, and lock-screen hiding.

Debug builds also accept notifications posted from the shell, so you can try it without PhonePe:

```bash
adb shell cmd notification allow_listener dev.redfox.lookpe/dev.redfox.lookpe.service.PhonePeListenerService
adb shell "cmd notification post -S bigtext -t \"Received Rs 160\" t1 \"You've received Rs 160 from ******1234 via PhonePe for txn T2601011030000000000001.\""
```

## Project structure

```
app/src/main/java/dev/redfox/lookpe/
├── service/   PhonePeListenerService, ListenerHealth (hourly "still reading?" check)
├── parse/     PaymentParser
├── data/      Room: payments, skipped notifications, repository (search, ranges, repair)
├── widget/    Glance widget (size-aware layouts), midnight refresh
├── reveal/    hide/show state, biometric prompt, auto-hide alarm
├── backup/    .lookpe codec, key, automatic backup, export/import
├── notify/    notification channels, daily summary
├── setup/     system settings shortcuts (notification access, battery, autostart, widget)
└── ui/        Home, Settings, Search, History, Skipped, payment details
```

## Design

The UI uses [Obsidian](https://github.com/Redfox-OSS/obsidian), a dark Jetpack Compose design system,
with the Emerald accent. `./gradlew check` runs `verifyDesignRules`, which fails the build on
`MaterialTheme.`, `clickable`, ripples or hard-coded colours. The Glance widget can't use Compose
components, so it takes the same colour tokens from `obsidianColors(…)`.

## Limitations

- Only payments that produce a notification are logged. Payments made while notification access was off, or while the system had LookPe stopped (and the notification was already cleared), are missed. Check against PhonePe Business now and then.
- LookPe depends on PhonePe's notification wording. If it changes, payments show up in **Settings → Troubleshooting → Skipped notifications**.
- Not affiliated with PhonePe.

## License

[GPL-3.0](LICENSE)
