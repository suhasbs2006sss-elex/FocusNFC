# FocusNFC

[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-7F52FF.svg?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Android Gradle Plugin](https://img.shields.io/badge/AGP-9.3.1-3DDC84.svg?logo=android&logoColor=white)](https://developer.android.com/studio/releases/gradle-plugin)
[![Jetpack Compose](https://img.shields.io/badge/Compose-BOM%202026.02.01-4285F4.svg?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-26%20(Android%208.0)-blue.svg)](https://apilevels.com/)
[![Target SDK](https://img.shields.io/badge/Target%20SDK-37-darkgreen.svg)](https://apilevels.com/)
[![Hardware Requirement](https://img.shields.io/badge/Hardware%20Req-Physical%20NFC%20Tag-darkorange.svg)](#hardware--physical-requirements)
[![Architecture](https://img.shields.io/badge/Architecture-Service%20%2B%20Accessibility%20Blocker-orange.svg)]()

FocusNFC is an Android deep-work enforcement system that pairs physical NFC desk tags with real-time app blocking, automated Do Not Disturb (DND), and a low-level 40 Hz Gamma binaural beat sound generator.

**When a focus session is active, FocusNFC strictly blocks you from opening any selected distracting apps (such as Instagram, YouTube, Reddit, or games). Any attempt to open them immediately kicks you out to the home screen and terminates floating Picture-in-Picture windows.**

By requiring a physical somatic interaction (tapping an NFC tag on your desk or notebook) to initiate focus, it eliminates the cognitive friction and temptation loop of configuring digital timers on the very device that creates distraction.

---

## Hardware & Physical Requirements

To use FocusNFC as designed, the following hardware is **required**:

| Requirement | Specification | Details |
| :--- | :--- | :--- |
| **Physical NFC Tag** | NTAG213 / NTAG215 / NTAG216 | Standard rewritable NFC sticker, card, or keyfob. Programmed with URI `focusapp://start`. Minimum ~48 bytes memory required. |
| **NFC-Enabled Android Device** | Android 8.0+ (API 26 to 37) | Device must have an active built-in NFC reader chip enabled in System Settings. |
| **Tag Programmer** | Free NFC App (NFC Tools / NXP TagWriter) | Used once to write the `focusapp://start` NDEF URI record onto the physical tag. |

> **Why a physical tag is required:**  
> Digital blockers fail because entering the app to start a session presents screen notifications and social icons before work begins. Affixing a physical NFC sticker to your desk, laptop stand, or notebook creates an unavoidable somatic commitment: tap the tag, and the device immediately locks into focus mode.

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

### 2. Active Session App Blocking (`BlockAccessibilityService`)
**During an active focus session, FocusNFC strictly blocks and defuses all selected apps on your phone in real time:**

- **Custom Blocklist Selection:** You choose exactly which apps to block (Instagram, YouTube, Reddit, TikTok, Netflix, Games, etc.) using the in-app grid or search bar.
- **Instant OS-Level Deflection:** The microsecond a blocked app attempts to open, Android's `AccessibilityService` detects `TYPE_WINDOW_STATE_CHANGED` and triggers `GLOBAL_ACTION_HOME` within 10 milliseconds, kicking you straight out of the app.
- **Anti-Circumvention (PiP & Overlay Destruction):** To stop video apps from continuing in floating Picture-in-Picture (PiP) mode, FocusNFC forces itself to the foreground (`FLAG_ACTIVITY_CLEAR_TOP`), instantly collapsing background playback and popup overlays.
- **Persistent Enforcement:** As long as the countdown timer is running, the blocked apps remain inaccessible. A brief warning toast (`⛔ Focus Mode Active! App Blocked.`) notifies the user.
- **Emergency Payment Whitelist:** To ensure you are never stranded during emergencies, critical payment gateways (`PhonePe`, `Paytm`, `Google Pay`, `BHIM UPI`, `SBI UPI`) are explicitly whitelisted and will never be blocked.

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
- **Physical NFC Tag:** At least one rewritable NFC tag (NTAG213 / NTAG215 / NTAG216 sticker, card, or token)
- **Target Android Device:** Physical phone with hardware NFC controller (Android 8.0+ / API 26+)
- **Android Studio:** Ladybug (2024.2+) or newer
- **JDK:** OpenJDK 17 or 21
- **Android SDK:** Platform 37 (Build Tools 35.0.0+)

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
- **Physical Trigger:** NFC Forum Type 2 / Type 4 (NDEF URI `focusapp://start` on NTAG213/215/216)
- **Minimum OS Support:** Android 8.0 (Oreo, API level 26)
- **Target OS Support:** Android 15 / SDK 37

---

## License

This project is licensed under the [MIT License](LICENSE).
