# CLAUDE.md

Guidance for Claude Code when working in this repository.

## What this is

LookPe is a single-module Android app (`:app`, package and applicationId `dev.redfox.lookpe`). It reads
**PhonePe Business** "money received" notifications on the phone and shows the day's payments and
total in an app and a resizable Glance home-screen widget. It was built for one user: a
shopkeeper (the developer's father) on a **Poco X7 Pro running HyperOS 3 / Android 16**. Keep the
UI simple and readable. Most HyperOS quirks below were learned on that phone.

The app has **no INTERNET permission**, on purpose. Don't add networking.

The repo (github.com/debz-g/LookPe) is **public** and GPL-3.0. See "Secrets and real data".

## Commands

```bash
./gradlew check            # lint + all unit tests + verifyDesignRules: run before every commit
./gradlew testDebugUnitTest --tests "dev.redfox.lookpe.PaymentParserTest"   # one test class
./gradlew assembleDebug    # debug APK (also accepts shell-posted test notifications)
./gradlew :app:releaseApk -PversionCode=6 -PversionName=1.2.3  # hand-out APK: assembleRelease + copy to LookPe.apk
```

Toolchain: AGP 9.2.1 with built-in Kotlin (no `kotlin-android` plugin), Kotlin 2.2.10, KSP,
compileSdk 36.1, minSdk 31, targetSdk 36, JDK 17+. Add dependencies through
`gradle/libs.versions.toml`, never as hard-coded versions.

### Shipping an update the phone can install over the old one

- Keep `applicationId` as it is, and build on the **same machine** (same `~/.android/debug.keystore`).
- The version is passed per build; `app/build.gradle.kts` only holds the fallback (`5` / `1.2.2`). A
  same-or-lower `versionCode` fails with `INSTALL_FAILED_VERSION_DOWNGRADE`.

**Never build a release APK in-session.** When asked to "generate a release APK" / "bump the version and
build", reply with the command below instead, changing only the `-P` values. Nothing in
`app/build.gradle.kts` needs editing.

```bash
./gradlew :app:releaseApk -PversionCode=6 -PversionName=1.2.3
```

The `releaseApk` task runs `assembleRelease` and copies the result to `LookPe.apk` in the repo root
(git-ignored); it fails if `assembleRelease` produced no APK. Both flags are optional, so plain
`assembleDebug` and IDE builds still work, but **always pass both when handing out** — otherwise the APK
gets the stale fallback version.

- `versionCode` must go **up** relative to what the phone already has. The last handed-out build is `5`,
  so the next one is `6` or higher; `-PversionCode=2` would be rejected by the phone.
- Then `adb install -r LookPe.apk` over the old build once, to exercise the Room migration + launch repair.

## Architecture

```
PhonePeListenerService ─► PaymentParser ─► Repository (Room) ─► UI screens + TodayWidget (Glance)
        │                      │ null
        │                      └─► skipped table (PhonePe Business only)
        └─► ListenerHealth (connected flag; hourly worker alerts if it stops)
```

- `service/PhonePeListenerService`: `NotificationListenerService`. The first check is `isSource(pkg)`: only `com.phonepe.app.business`, plus `com.android.shell` in **debug builds only** (testing hook). It skips group summaries and reads `EXTRA_BIG_TEXT ?: EXTRA_TEXT`. On connect it re-processes `activeNotifications`, which picks up payments that arrived while the listener was down. Only real PhonePe Business posts are written to the skipped table, never shell posts.
- `parse/PaymentParser`: pure Kotlin. See "Parser rules".
- `data/`: Room DB `lookpe.db`, **version 2** (`payment`, `skipped`). `Repository` is a plain singleton (no DI; the app is small).
  - Payments are de-duplicated by unique `dedupeKey`: the txn ID, or `notificationKey|postTime` when there is none. When there's no txn ID, the same amount from the same payer within 60 s is also treated as a duplicate.
  - `raw` stores `"$title\n$text"`. It's what `repairAmounts()` and `reparseSkipped()` re-read.
- `widget/TodayWidget`: Glance with `SizeMode.Exact`. Three layouts by height: compact (<110dp: total + eye), summary (<190dp), full (list). `MidnightRefresh` re-arms on boot and also re-arms `DailySummary`. In `full`, tapping a payment row starts `ui/PaymentDialogActivity`: a transparent activity that draws its own scrim and card, because a Glance widget can't host a dialog. It reuses `PaymentDetailsBody` (shared with the app's sheet) and keeps amounts masked while the lock is on.
- `reveal/`: hide/show state (`RevealState`: prefs + StateFlows `revealed`, `lockEnabled`), the biometric prompt, `RevealActivity`, and an exact-alarm `HideReceiver`.
- `backup/`: the `.lookpe` format (`BackupCodec`), `BackupKey`, and `Backups` (automatic WorkManager backup, export, merge-import).
- `notify/`: channels (`alerts`, `summary`) and `DailySummary` (exact alarm at the chosen hour, 9 PM by default).
- `setup/SystemSettings.kt`: setup status and intents into system settings (notification access, battery, HyperOS Autostart, notification permission, pin widget).
- `ui/`: Compose screens. `MainActivity` owns a hand-rolled back stack (`Screen` enum, `listSaver`) with Obsidian `ObTransitions` push/pop. Home's selected day is hoisted there, so History can open a day on Home.
- On every launch `MainActivity` runs `reparseSkipped()` then `repairAmounts()`, and refreshes the widget if anything changed.

## Parser rules (the part most likely to break)

PhonePe styles the digits, and the style changes between notifications. All of these are real:

- Mathematical digits: bold `𝟏𝟔𝟎`, monospace `𝟽𝟻𝟶`, double-struck `𝟚𝟟𝟘`, sans-bold `𝟯𝟰𝟬`. NFKC turns them into ASCII.
- **Keycap emoji** `2️⃣9️⃣0️⃣`, which is `2` + U+FE0F + U+20E3 (first seen 29 Oct 2026). `forNumbers()` strips `DECORATIONS` and maps 🔟.

Invariants. Keep them, and add a test for any new format:

1. **Never record a truncated amount.** The `AMOUNT` regex has a boundary lookahead, so an amount followed by an unknown character fails to match. The notification then goes to Skipped instead of being logged wrong. Before this rule, keycap ₹290 was logged as ₹2.
2. If both the title and the text contain an amount, they must agree, otherwise `parse` returns null.
3. Amounts are read from `forNumbers(...)` text; payer names come from plain `normalize(...)` text, so names aren't altered.
4. `EXCLUDE` keeps settlements, refunds, cashback, failures and debits out.
5. When the parser changes, saved rows are fixed automatically by `repairAmounts()` on next launch and after import. It re-parses `raw`, and only rows that parse to a different amount change. Don't add one-off data migrations for parser fixes.

To debug a real problem, ask for the user's `.lookpe` backup. Decrypt it with `BackupCodec.decode` in a
**temporary** unit test that reads the path from an env var and writes to `/tmp`. Inspect `raw` with
non-ASCII shown as `U+XXXX`, then **delete the temp test and the decrypted output**.

## Secrets and real data (public repo)

- **`backup.key`** (project root, 64 hex chars) is the AES-256 key for every `.lookpe` backup. It's **git-ignored and must never be committed.** `app/build.gradle.kts` reads it, creates a random one if it's missing, and compiles it in as two random-masked halves (`BuildConfig.BACKUP_KEY_A/B`; `BackupKey` XORs them).
  - The copy on the developer's Mac matches the key the user's phone already uses. **Never regenerate, rotate or change it**, or all of the father's existing backups become unreadable.
  - It can't move to the Android Keystore: Keystore keys die with the app, and backups must survive a reinstall.
- **No real payment data in the repo.** Tests, comments and docs use made-up values: names like `ASHA ROY` / `Test Payer`, masked numbers like `******1234`, txn IDs like `T2601011030000000000001`. Never paste customer names, phone digits or txn IDs from screenshots or backups into code.
- `.gitignore` also excludes `*.apk`, `*.aab`, `*.lookpe`, keystores and `local.properties`. Check `git diff --cached --name-only` before committing.

## Git

- Commits use the repo-local identity **Debayan G <debz.exe@gmail.com>** (the `debz-g` account). The machine's global identity is a work account (`debz-artium`); don't use it here.
- Push with the `debz-g` gh credentials, e.g. `git -c credential.helper= -c credential.helper='!gh auth git-credential' push`, after confirming `gh auth status` shows `debz-g` active.
- Work happens on `main`.

## Design system: Obsidian

The UI uses [Obsidian](https://github.com/Redfox-OSS/obsidian) (`dev.redfox:obsidian:2.0.0`, Maven repo
`https://redfox-oss.github.io/obsidian/maven`). It's always dark, with the **Emerald** accent on the
**Dark** ramp, both set once in `ui/Brand.kt`. Its full guide is in that repo's `docs/GUIDE.md`; a local
clone may be at `~/debz/obsidian`.

- Read tokens from `ObsidianTheme.colors` / `.typography`, **never `MaterialTheme.`**. Use `Modifier.pressable { }`, never `clickable` or ripples. Don't hard-code `Color(0x…)`.
  - `verifyDesignRules` (part of `check`) enforces these in `src/main/java`, except `widget/`.
- Prefer Obsidian components: `ObCard`, `ObListRow`, `PillButton`, `ActionPillButton`, `SegmentedPill`, `ObSwitch`, `DetailPage`, `LargeHeader`, `ObSheet`, `CopyPill`, `ObSearchField`, `EmptyState`, `ObSnackbarHost`.
- Screens: frosted header over a `glassSource()` `LazyColumn`, `ScreenGutter` padding, `revealOnEnter(index)` entrances inside a `RevealScope`.
- Icons are Material Symbols Rounded vector drawables in `res/drawable/ic_*.xml`. Note that `ObListRow` subtitles are single-line; use a custom row (see `SetupRow`) for wrapping text.
- **The widget can't use Compose/Obsidian components.** It builds `ColorProvider(Color)` values from `LookPeColors = obsidianColors(...)`. `ColorProvider(@ColorRes)` is a restricted Glance API that fails lint.

## Hard-won gotchas

- **Widget eye button:** both Show and Hide go through `RevealActivity` (`actionStartActivity` with an `action` extra). A Glance `ActionCallback` broadcast for Hide did nothing on HyperOS.
- **Glance flows:** inside `provideContent`, `remember` any Flow before `collectAsState`. A new Flow per recomposition restarts collection and loops.
- **Timers on HyperOS:** WorkManager one-off delays get deferred (the process is frozen). Use exact alarms (`USE_EXACT_ALARM`, auto-granted for sideloaded apps) for anything time-critical: auto-hide after 60 s, the daily summary. WorkManager is fine for periodic backups and the hourly health check.
- **ListenerHealth.connected** is deliberately in-memory, not persisted. If the process dies, a fresh process starts with `false` until the system rebinds the listener.
- **`FragmentActivity` + biometric 1.1.0:** this pulls in an old `androidx.fragment` that crashes `rememberLauncherForActivityResult` ("Can only use lower 16 bits for requestCode"). `fragment-ktx 1.8.9` is pinned explicitly; keep it.
- **`Money.format`** does Indian lakh grouping by hand (₹1,21,050). JVM locale data doesn't, so don't switch back to `NumberFormat`.
- **Room:** schemas are exported to `app/schemas/` and committed. For a schema change, bump `version` and add an `AutoMigration`, because users upgrade in place. Never use destructive migration; it wipes the payment history.
- **Backups** go to `Download/LookPe` via MediaStore, with no permission needed, and they survive uninstall.
  - They're skipped when there are no payments, so a fresh reinstall can't bury the real backup under an empty one, and when nothing has changed.
  - Only the 5 newest backups this install made are kept.
  - Import merges, using `INSERT IGNORE` on `dedupeKey`.
- **Sideloaded installs** on Android 13+ get "restricted settings": the notification-access switch is greyed out until App info → ⋮ → Allow restricted settings. `adb install` avoids this.

## Testing

- Unit tests in `app/src/test`. Robolectric (`@Config(sdk = [35])`) covers Android-dependent tests: widget layouts via `glance-appwidget-testing`, the daily summary via `ShadowNotificationManager`, and repair against a real Room DB.
- **Debug builds** accept shell notifications, so the flow can be tested without PhonePe:
  ```bash
  adb shell cmd notification allow_listener dev.redfox.lookpe/dev.redfox.lookpe.service.PhonePeListenerService
  adb shell "cmd notification post -S bigtext -t \"Received Rs 160\" t1 \"You've received Rs 160 from ******1234 via PhonePe for txn T2601011030000000000001.\""
  ```
  Quote the whole command for the device shell; the apostrophe in "You've" breaks naive quoting.
- Emulator (`Pixel_10_Pro` AVD) caveats:
  - It gets very slow when the host is loaded. Scripted launcher swipes may register as long-presses. Drive the app with `uiautomator dump` + tap-by-text.
  - The shell can't send `APPWIDGET_UPDATE`.
  - A forced run of a periodic WorkManager job before it's due is silently skipped.
  - The Play image can't be rooted, so the clock can't be changed.
- Before handing out an APK, install the release build **over the previous version** (`adb install -r`) and open it once. This exercises the Room migration and the launch-time repair.
