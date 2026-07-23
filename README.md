# vLock 2.0 🚘🔒

[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-purple.svg)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4.svg)](https://developer.android.com/jetpack/compose)
[![Room DB](https://img.shields.io/badge/Database-Room-orange.svg)](https://developer.android.com/training/data-storage/room)
[![Creator](https://img.shields.io/badge/Creator-mbr31-blue.svg)](https://github.com/mbr31)

> **vLock 2.0** is an advanced, feature-rich, modern SMS-based vehicle security & GPS tracker controller for Android. Built as an enhanced successor to the official Play Store app **V-Lock Lite**, vLock 2.0 brings real-time incoming SMS reply pairing, customizable Glassmorphism themes, full offline history logs, and instant preset management.

---

## 📌 About vLock 2.0

**vLock 2.0** was created by **[mbr31](https://github.com/mbr31)** to solve the limitations of standard SMS vehicle control applications. Modern GPS trackers and vehicle security systems rely on SMS commands for essential functions like engine cut-off, location querying, speed alerts, and status checks. 

While basic apps like [V-Lock Lite on Google Play](https://play.google.com/store/apps/details?id=com.vehiclelock.vlocklite) provide standard SMS triggering, **vLock 2.0** completely redefines the user experience with:

- **Hassle-free Single-Tap command**: Open the app, tap a button and get the task done with the reply in this same app! No need to open any messaging app, ever.
- **Automatic Reply Matching**: Automatically captures incoming reply SMS messages sent by the specific vehicle tracker number and pairs them to the command sent, popping up an instant real-time response modal on your screen.
- **Sleek Glassmorphism UI**: High-contrast, futuristic design that avoids outdated stock Android UI patterns.
- **Complete Command & Reply History**: Every command sent and reply received is stored locally in an offline Room SQLite database with detailed timestamps and status badges.
- **Customizable Buttons & Command Presets**: Reorganize, customize, or add new SMS codes for any vehicle or tracker model.
- **Multi-Theme Support**: Choose between *Glassmorphism*, *Cyberpunk Neon*, *Gold Accent*, and *Midnight Dark* UI themes.
- **Accessibility even on your HomeScreen**: You can add a widget on home-screen and add upto four frequently used buttons to send command even without opening the app itself!

---

## 🚀 Key Features

### 📡 1. Intelligent SMS Command & Reply System
- Send vehicle security SMS commands with a single tap.
- Direct background SMS transmission (`SEND_SMS`) or default SMS app routing (`Messaging Intent`).
- **Real Receiver Reply Pairing**: Listens specifically for incoming SMS (`RECEIVE_SMS`) originating from the target vehicle's number sent after a command action, matching the reply directly to the command log.
- **Instant Response Popup**: Displays the incoming tracker reply message in a clean popup alert over the app interface as soon as it arrives.

### 📊 2. Comprehensive History & Activity Logs
- Offline local database (Android Room) stores all command logs and received responses.
- Status indicators (*Delivered*, *Awaiting Reply*, *Replied*).
- Tap any log entry to view full command code, timestamp, target receiver number, and exact reply text.

### 🎨 3. Customizable UI & Visual Themes
- Glassmorphic card layouts with translucent frosted glass effects, subtle borders, and glow accents.
- 4 distinct theme color palettes:
  - **Glassmorphism** (Emerald & Deep Navy)
  - **Cyberpunk** (Neon Cyan & Electric Purple)
  - **Gold Accent** (Luxury Gold & Obsidian Black)
  - **Midnight Dark** (Amoled Black & Cool Blue)
- Custom grid sizing, header toggle, and haptic vibration controls.

### ⚙️ 4. Vehicle & Command Management
- Store multiple vehicle contact numbers.
- Easily customize command names, SMS codes, icons, and layout arrangement.
- Fast preset buttons for common actions (*Lock Engine*, *Unlock Engine*, *Get Location*, *Check Status*, *Set Geofence*, *Speed Limit*, *Arm Alarm*, etc.).

---

## 🔄 Comparison: vLock 2.0 vs. V-Lock Lite

| Feature | Official V-Lock Lite | **vLock 2.0 (by mbr31)** |
| :--- | :---: | :---: |
| **SMS Command Sending** | Basic | Advanced |
| **Incoming Tracker Reply Pairing** | ❌ No | **Automatic (Targeted Number)** |
| **Real-time Reply Modal Popup** | ❌ No | **Instant On-Screen Popup** |
| **Command & Reply Local Database** | ❌ Limited | **Full Offline Room DB** |
| **UI Design Style** | Basic Stock UI | **Modern Glassmorphism** |
| **Theme Customization** | ❌ None | **4 Themes (Glass, Cyber, Gold, Dark)** |
| **Custom Button Mapping** | ❌ Fixed | **Fully Customizable** |
| **Haptic & Visual Feedback** | ❌ Basic | **Tactile Vibration & Animations** |

---

## 🛠️ Tech Stack & Architecture

- **Language**: 100% Kotlin
- **UI Framework**: Jetpack Compose (Material 3 with custom Glassmorphism elevation & surface shaders)
- **Local Persistence**: Android Room Database with KSP (Kotlin Symbol Processing)
- **State Management**: Android ViewModel & Kotlin `StateFlow`
- **SMS Integration**: Android `BroadcastReceiver` (`SmsReceiver`), `SmsManager`, and `Telephony.Sms.Intents`
- **Asynchronous Execution**: Kotlin Coroutines & Flow

---

## 📦 Permissions Required

For full functionality, **vLock 2.0** requires the following Android permissions:

- `Manifest.permission.SEND_SMS` - Allows sending SMS commands directly to your vehicle tracker.
- `Manifest.permission.RECEIVE_SMS` - Allows the app to detect incoming tracker reply messages.
- `Manifest.permission.READ_SMS` - Required on certain Android versions to process incoming SMS broadcasts.
- `Manifest.permission.VIBRATE` - Enables tactile haptic feedback when pressing command buttons.

---

[📥 Download Latest APK (v2.0.0)] 
( https://github.com/mbir31/vLock/releases/download/v2.0.0/vLock.2.0.0.apk )

---

## 👨‍💻 Author & Credits

- **Creator & Developer**: **[mbr31](https://github.com/mbr31)**
- **Original Concept Reference**: Inspired by [V-Lock Lite on Google Play Store](https://play.google.com/store/apps/details?id=com.vehiclelock.vlocklite).

---

## 📄 License

This project is released under the **MIT License**. Feel free to use, modify, and distribute with attribution to the original creator **mbr31**.
