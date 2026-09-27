# FocusNFC

[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-7F52FF.svg?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Android Gradle Plugin](https://img.shields.io/badge/AGP-9.3.1-3DDC84.svg?logo=android&logoColor=white)](https://developer.android.com/studio/releases/gradle-plugin)
[![Jetpack Compose](https://img.shields.io/badge/Compose-BOM%202026.02.01-4285F4.svg?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-26%20(Android%208.0)-blue.svg)](https://apilevels.com/)
[![Target SDK](https://img.shields.io/badge/Target%20SDK-37-darkgreen.svg)](https://apilevels.com/)
[![Architecture](https://img.shields.io/badge/Architecture-Service%20%2B%20Accessibility%20Blocker-orange.svg)]()

FocusNFC is an Android deep-work enforcement system that pairs physical NFC desk tags with operating-system-level app isolation, automated Do Not Disturb (DND), and a low-level 40 Hz Gamma binaural beat sound generator.

By requiring a physical somatic interaction (tapping an NFC tag on your desk or notebook) to initiate focus, it eliminates the cognitive friction and temptation loop of configuring digital timers on the very device that creates distraction.

---

## System Architecture Overview

```
+-------------------------------------------------------------+
|                     Physical NFC Tag                        |
|             (NDEF URI: focusapp://start)                    |
+------------------------------+------------------------------+
                               |
                        [NFC Tap Detect]
                               v
+-------------------------------------------------------------+
|                      MainActivity                           |
|  - NDEF_DISCOVERED Intent filter                            |
|  - Jetpack Compose OLED Dark Interface                      |
|  - Custom Canvas Rotary Dial (1 to 120 min)                 |
|  - Installed Package Picker & Search                        |
+--------------+-------------------------------+--------------+
               |                               |
       [Starts Session]                 [Configures]
               v                               v
+-------------------------------+  +--------------------------+
|         FocusService          |  | BlockAccessibilityService|
|  - Foreground Service         |  |  - Intercepts Window     |
|    (mediaPlayback)            |  |    State Changes         |
|  - System DND Mode            |  |  - Kicks to Home Screen  |
|    (INTERRUPTION_FILTER)      |  |    via GLOBAL_ACTION_HOME|
|  - Chronometer Notification   |  |  - Kills PiP Overlays    |
|  - VibratorManager Haptics    |  |  - UPI / Banking Safety  |
+--------------+----------------+  |    Whitelist Bypass      |
               |                   +--------------------------+
       [Synthesis Engine]
               v
+-------------------------------------------------------------+
|            AudioTrack Real-Time PCM Synthesis               |
|  - GammaGenerator: 40 Hz Binaural Beat (200 Hz L / 240 Hz R)|
|  - CompletionChime: A-Major Zen Bell Harmonic Decay         |
+-------------------------------------------------------------+
```

---

## Key Engineering Highlights

### 1. Physical Somatic Triggering (NFC NDEF Dispatch)
- Listens for `android.nfc.action.NDEF_DISCOVERED` with data scheme `focusapp://start`.
- Tapping an authorized desk tag launches the session instantly using cached duration and target package filters without unlocking other apps.
- Eliminates decision fatigue and removes phone browsing before study sessions.

### 2. Deep OS-Level Distraction Shielding (`BlockAccessibilityService`)
- Operates via Android's `AccessibilityService` (`TYPE_WINDOW_STATE_CHANGED` and `TYPE_WINDOW_CONTENT_CHANGED`).
- When a blocked package (e.g. social feeds, video apps, games) enters the foreground during an active session:
  1. Issues `performGlobalAction(GLOBAL_ACTION_HOME)` within 10ms.
  2. Brings `MainActivity` back to front with `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP` to actively terminate Picture-in-Picture (PiP) windows and floating overlay players.
  3. Displays a cooldown-throttled haptic HUD warning.
- **Fail-Safe UPI / Payment Whitelist:** Explicitly permits critical payment and banking gateways (`com.phonepe.app`, `net.one97.paytm`, `com.google.android.apps.nbu.paisa.user`, `in.org.npci.upiapp`, `com.sbi.upi`) so emergency transactions or bill payments are never trapped.

### 3. Programmatic DND & Foreground Lifecycle (`FocusService`)
- Bound to an ongoing `mediaPlayback` Foreground Service with public lockscreen chronometer notification.
- Automatically claims priority interruption access (`NotificationManager.INTERRUPTION_FILTER_PRIORITY`), silencing phone notifications, buzzes, and alert popups during sessions.
- Automatically restores original system notification filters and delivers a timed completion haptic pulse via `VibratorManager` on finish.

### 4. Low-Level 16-Bit PCM Neuro-Audio Synthesis
- Does not rely on looped MP3s or network audio streams. Synthesizes frequencies mathematically in real-time using raw stereo 16-bit PCM written directly into an Android `AudioTrack`:
  - **40 Hz Gamma Wave Entrainment (`GammaGenerator`):** Left channel outputs 200.0 Hz, right channel outputs 240.0 Hz. The resulting 40 Hz auditory beat frequency matches gamma brainwave frequencies associated with heightened cognitive processing, attentiveness, and working memory.
  - **Harmonic Zen Bell Chime (`CompletionChimePlayer`):** Renders a multi-frequency A-Major bell chord (440.0 Hz, 554.37 Hz, 659.25 Hz, 880.0 Hz) shaped by an exponential decay envelope `envelope = exp(-1.2 * t)` for a clean, non-jarring session conclusion.

### 5. High-Performance Jetpack Compose OLED Interface
- Pure `#000000` AMOLED-friendly theme for zero battery waste on OLED panels.
- Custom polar-coordinate drag gesture rotary dial built with `detectDragGestures` and `atan2(y, x)` calculation, providing sweep-gradient visual feedback.
- Real-time installed package search and dynamic filtering with multi-selection persistence via `SharedPreferences`.

---

## Programming Physical NFC Tags

You can use standard, inexpensive NFC stickers or cards (NTAG213, NTAG215, or NTAG216).

### Quick Setup with NFC Tools (Android / iOS):
1. Open **NFC Tools** (or any NDEF writer app like NXP TagWriter).
2. Tap **Write** -> **Add a record**.
3. Select **Custom URL / URI**.
4. Enter the URI:
   ```text
   focusapp://start
   ```
5. Tap **Write** and touch your NFC tag to the back of your phone.
6. Affix the tag to your desk, monitor stand, or study notebook. Tapping your phone against the tag will now instantly trigger FocusNFC.

---

## Android Permissions & System Requirements

FocusNFC requires explicit system privileges to enforce strict device focus:

| Permission | Purpose |
| :--- | :--- |
| `android.permission.NFC` | Detects physical NFC tags and receives NDEF payloads. |
| `android.permission.BIND_ACCESSIBILITY_SERVICE` | Intercepts attempts to open distracting apps and defuses them immediately. |
| `android.permission.ACCESS_NOTIFICATION_POLICY` | Toggles Do Not Disturb (DND) mode automatically when a session starts and ends. |
| `android.permission.PACKAGE_USAGE_STATS` | Enumerates installed applications for the custom blocker selection grid. |
| `android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Prevents the Android system from killing the countdown timer and audio generator in the background. |
| `android.permission.SYSTEM_ALERT_WINDOW` | Overrides background app transitions and handles focus HUD notifications. |
| `android.permission.POST_NOTIFICATIONS` | Displays the persistent timer chronometer on Android 13+ (API 33+). |

---

## Getting Started & Building

### Prerequisites
- **Android Studio:** Ladybug (2024.2+) or newer
- **JDK:** OpenJDK 17 or 21
- **Android SDK:** Platform 37 (Build Tools 35.0.0+)
- **Device:** Physical Android device with NFC hardware running Android 8.0 (API 26) or higher.

### Clone & Build via Gradle

```bash
# Clone the repository
git clone https://github.com/suhasbs2006sss-elex/FocusNFC.git
cd FocusNFC

# Build debug APK
./gradlew assembleDebug

# Install directly to a connected USB / Wi-Fi debugging device
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## Repository Structure

```text
FocusNFC/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/example/focusnfc/
│   │   │   │   ├── MainActivity.kt               # Jetpack Compose UI, Rotary dial, NFC intent handler
│   │   │   │   ├── FocusService.kt               # Foreground session lifecycle, DND & timer controller
│   │   │   │   ├── BlockAccessibilityService.kt  # Strict app deflection & payment whitelist
│   │   │   │   ├── GammaGenerator.kt             # Real-time 40 Hz binaural beat AudioTrack synthesizer
│   │   │   │   └── ui/theme/                     # Color palette, OLED typography & Material3 theme
│   │   │   ├── res/                              # Vector graphics (ic_focus_logo), drawables, mipmaps
│   │   │   └── AndroidManifest.xml               # Service bindings, NFC intent filters & permissions
│   │   └── test/                                 # Unit & instrumented test suites
│   └── build.gradle.kts                          # App-level dependencies & compiler configurations
├── gradle/
│   └── libs.versions.toml                        # Centralized dependency catalog (BOM, AGP, Kotlin)
├── build.gradle.kts                              # Root buildscript
├── settings.gradle.kts                           # Module & repository declarations
└── README.md
```

---

## Technical Specifications

- **Language:** Kotlin 2.2.10
- **UI Toolkit:** Jetpack Compose (Compose BOM 2026.02.01, Material3)
- **Audio Architecture:** Direct Android `AudioTrack` 16-bit PCM stereo (Sample Rate: 44,100 Hz)
- **Minimum OS Support:** Android 8.0 (Oreo, API level 26)
- **Target OS Support:** Android 15 / SDK 37
- **NFC Standard:** NFC Forum Type 2 / Type 4 (NDEF URI Records)

---

## License

This project is licensed under the [MIT License](LICENSE).
