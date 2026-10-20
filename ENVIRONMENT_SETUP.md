# Environment Setup & Build Guide

This document describes how to configure the development environment to build, deploy, and debug **Bookzor**.

---

## ❓ Do You Need Android Studio?

**Short answer:** **No, Android Studio is NOT strictly required.**

You have two choices:
- **Option A (CLI Only — Lightweight & Fast):** Install OpenJDK 17 + Android SDK Command-Line Tools. Build with `./gradlew assembleDebug`, deploy directly to the tablet with `adb install`, and debug with `adb logcat`. Perfect for automated agents and terminal enthusiasts.
- **Option B (Android Studio — GUI):** Provides visual Compose previews, a visual layout inspector, profiler (CPU/memory), and integrated debugger. Recommended if you want to visually tweak UI components without deploying to the tablet each time.

Both workflows are documented below.

---

## 📱 Attached Test Device Details

The current development host has the following target device connected via USB:
- **Device Model:** Samsung Galaxy Tab A7 (`SM-T505`)
- **Android Version:** Android 12 (API Level 31/32)
- **CPU Architecture:** `arm64-v8a`
- **Device Serial:** `R9TR605RELJ` (verified via `adb devices`)

---

## 🛠️ Step 1: Install OpenJDK 17 or 21

Android Gradle Plugin (AGP) 8.x requires Java 17 or Java 21.

### On Arch / CachyOS (Current Host OS)
```bash
sudo pacman -Syu jdk17-openjdk
# Verify installation
archlinux-java status
sudo archlinux-java set java-17-openjdk
java -version
```

### On Ubuntu / Debian
```bash
sudo apt update
sudo apt install -y openjdk-17-jdk
java -version
```

### On macOS (Homebrew)
```bash
brew install openjdk@17
java -version
```

---

## 🧰 Step 2: Android SDK Setup

### Option A: Command-Line Tools Only (No Android Studio)

1. Create Android SDK directory:
   ```bash
   mkdir -p $HOME/Android/Sdk/cmdline-tools
   ```

2. Download Android Command-Line Tools (Linux):
   ```bash
   cd /tmp
   curl -O https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
   unzip commandlinetools-linux-*_latest.zip
   mkdir -p $HOME/Android/Sdk/cmdline-tools/latest
   mv cmdline-tools/* $HOME/Android/Sdk/cmdline-tools/latest/
   ```

3. Export environment variables (add to `~/.bashrc` or `~/.zshrc`):
   ```bash
   export ANDROID_HOME=$HOME/Android/Sdk
   export ANDROID_SDK_ROOT=$HOME/Android/Sdk
   export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools
   ```
   Apply them:
   ```bash
   source ~/.bashrc
   ```

4. Install Required SDK Platforms & Build Tools:
   ```bash
   sdkmanager --install "platform-tools" \
                        "platforms;android-34" \
                        "build-tools;34.0.0" \
                        "ndk;25.2.9519653" \
                        "cmake;3.22.1"
   ```

5. Accept SDK Licenses:
   ```bash
   yes | sdkmanager --licenses
   ```

---

### Option B: Installing Android Studio

If you prefer Android Studio:
1. **On CachyOS/Arch:**
   ```bash
   sudo pacman -S android-studio
   # Or via Flatpak:
   # flatpak install flathub com.google.AndroidStudio
   ```
2. Launch Android Studio:
   ```bash
   android-studio &
   ```
3. Complete the Setup Wizard:
   - Select "Standard" installation.
   - It will automatically download the Android SDK, platform tools, and accept licenses.
4. Open the `Bookzor` folder as an Android project.

---

## 🔌 Step 3: Setting Up the Samsung Tablet (SM-T505)

1. **Verify Developer Options & USB Debugging:**
   - On the tablet: Settings -> About tablet -> Software information -> Tap **"Build number"** 7 times.
   - Settings -> Developer options -> Enable **USB Debugging**.
   - If prompted on the tablet screen with *"Allow USB debugging?"*, check *"Always allow from this computer"* and tap **OK**.

2. **Verify connection from the command line:**
   ```bash
   adb devices
   ```
   Output must show:
   ```text
   List of devices attached
   R9TR605RELJ    device
   ```
   *(If it says `unauthorized`, unlock the tablet screen and accept the prompt).*

---

## 🏗️ Step 4: Building and Deploying Bookzor

### 1. Build the APK via Gradle Wrapper
From the project root directory:
```bash
./gradlew assembleDebug
```
The output APK is generated at:
`app/build/outputs/apk/debug/app-debug.apk`

### 2. Install onto the Connected Tablet
```bash
adb -s R9TR605RELJ install -r app/build/outputs/apk/debug/app-debug.apk
```

### 3. Launch the App
```bash
adb shell am start -n com.bookzor.app/.MainActivity
```

### 4. Monitor Real-time Logs
Filter specifically for Bookzor log tags:
```bash
adb logcat -v time -s BookzorTorrent BookzorPlayer BookzorScraper AndroidRuntime
```

---

## ⚡ Quick Troubleshooting

| Issue | Cause | Fix |
| :--- | :--- | :--- |
| `adb: device unauthorized` | Tablet hasn't accepted RSA fingerprint key | Unlock tablet, check prompt, tap "Always allow" |
| `SDK location not found` | Missing `local.properties` or `$ANDROID_HOME` | Create `local.properties` with `sdk.dir=/home/tpredrag/Android/Sdk` |
| `libtorrent4j: no native library found` | Missing ABI native `.so` files | Verify `ndk.abiFilters` in `app/build.gradle.kts` includes `arm64-v8a` |
| Gradle build out of memory | Default JVM heap too small | Add `org.gradle.jvmargs=-Xmx4096m` to `gradle.properties` |
