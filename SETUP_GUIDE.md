# NOVA ULTIMATE V4 — Complete Setup Guide

## What This App Does
A fully voice-controlled AI assistant for Android with:
- 🎤 Always-on wake word detection
- 🧠 Claude AI brain (answers any question)
- 📞 Call & WhatsApp/SMS via voice
- 🔦 Flashlight, 🔒 Lock screen, 🔊 Volume control
- 🎵 Music control & YouTube search
- 📄 Create PDF, PPT, TXT files
- 🖼️ AI image generation
- 🇮🇳 Bengali, Hindi & English support
- ✍️ Type text into any app via voice

---

## STEP 1 — Install Android Studio

1. Go to: **developer.android.com/studio**
2. Download and install Android Studio (free)
3. Open Android Studio → First-time setup completes automatically

---

## STEP 2 — Open the Project

1. Open Android Studio
2. Click **"Open"** → select the `NovaUltimateV4` folder
3. Wait for Gradle sync (2–5 min first time)
4. If it asks to upgrade Gradle → click **"Update"**

---

## STEP 3 — Add Your API Key

### Get Claude API Key (FREE):
1. Go to: **console.anthropic.com**
2. Sign up (free account)
3. Click **API Keys → Create Key**
4. Copy the key (starts with `sk-ant-api03-...`)

### Add to project:
1. Open `local.properties` file in project root
2. Add this line:
```
CLAUDE_API_KEY=sk-ant-api03-YOUR_KEY_HERE
```
3. Save the file

> ⚠️ NEVER share this file or upload to GitHub

---

## STEP 4 — Connect Your Android Phone

1. On your phone: **Settings → About Phone → tap "Build Number" 7 times**
2. Go to **Settings → Developer Options → Enable USB Debugging**
3. Connect phone to PC via USB cable
4. On phone: tap **"Allow"** for USB debugging dialog

---

## STEP 5 — Build & Install APK

### Option A — Install directly from Android Studio:
1. In Android Studio → top bar → select your phone name in device dropdown
2. Click the **▶️ Run** button (green play button)
3. App installs and opens on your phone automatically!

### Option B — Build APK file to share:
1. **Build → Generate Signed Bundle/APK**
2. Choose **APK**
3. Create a keystore (one-time): fill any details
4. Build → APK saved to `app/release/app-release.apk`
5. Send this APK to your phone and install

---

## STEP 6 — First-Time Phone Setup

When NOVA opens on your phone:

### A. Grant Microphone (required):
- Tap **"Allow"** on the microphone permission popup

### B. Enable Device Admin (for lock screen):
- NOVA will prompt you automatically
- Or go: **Settings → Security → Device Admin Apps → NOVA AI → Enable**
- This ONLY lets NOVA lock the screen — nothing else

### C. Enable Accessibility Service (for typing):
- Go: **Settings → Accessibility → Downloaded Apps → NOVA AI → Enable**
- This lets NOVA type text into any app (WhatsApp, SMS, Notes, etc.)

### D. Enable Notification Permission:
- Allow notifications so NOVA's microphone indicator shows

---

## STEP 7 — Test Voice Commands

Say these commands (default wake word: **"Hey NOVA"**):

| Command | What Happens |
|---------|-------------|
| "Hey NOVA, flashlight on" | Turns on torch |
| "Hey NOVA, call Mom" | Calls Mom from contacts |
| "Hey NOVA, send WhatsApp to John: Hi there" | Opens WhatsApp |
| "Hey NOVA, volume up" | Increases volume |
| "Hey NOVA, lock screen" | Locks phone |
| "Hey NOVA, create PDF: My Report" | Creates PDF in Downloads |
| "Hey NOVA, who is Albert Einstein?" | Claude AI answers |
| "टॉर्च चालू करो" | Hindi: Flashlight on |
| "টর্চ চালু করো" | Bengali: Flashlight on |

---

## STEP 8 — Settings In-App

Open the app → **Settings tab**:
- Change assistant name (e.g., "JARVIS")
- Change wake word (e.g., "Hey JARVIS")
- Switch language (English/Hindi/Bengali)
- Enter Claude API key
- Toggle always-listening mode
- Upload custom voice file
- Change voice speed

---

## Troubleshooting

**Voice not working?**
- Check: Settings → Apps → NOVA → Permissions → Microphone → Allow
- Make sure you're using Chrome/Google app voice engine

**Lock screen not working?**
- Enable Device Admin: Settings → Security → Device Admin → NOVA

**Typing not working in apps?**
- Enable Accessibility: Settings → Accessibility → NOVA AI → ON

**AI not answering?**
- Check API key in Settings
- Make sure phone has internet

**Build error "Gradle sync failed"?**
- File → Invalidate Caches → Restart
- Check internet connection (Gradle downloads libraries)

---

## File Structure
```
NovaUltimateV4/
├── app/
│   ├── build.gradle              ← Dependencies
│   └── src/main/
│       ├── AndroidManifest.xml   ← Permissions & services
│       ├── java/com/nova/ai/
│       │   ├── NovaApp.kt        ← App class
│       │   ├── models/           ← Data classes
│       │   ├── voice/            ← STT, TTS, Wake word
│       │   ├── ai/               ← Claude API brain
│       │   ├── commands/         ← Intent parser + executor
│       │   ├── services/         ← Foreground service
│       │   ├── utils/            ← Storage, file gen
│       │   └── ui/               ← Activities & ViewModel
│       └── res/
│           ├── xml/              ← Service configs
│           ├── values/           ← Strings
│           └── layout/           ← UI layouts
├── build.gradle                  ← Root Gradle
├── settings.gradle               ← Module settings
├── gradle.properties             ← JVM config
└── local.properties.template     ← API keys (copy → local.properties)
```

---

## Security Notes

- ✅ API keys encrypted with AES256-GCM (Android Keystore)
- ✅ Keys stored in EncryptedSharedPreferences
- ✅ Keys never hardcoded in source code
- ✅ local.properties excluded from version control
- ✅ Lock screen uses official DevicePolicyManager.lockNow()
- ✅ Accessibility only for text insertion (no data extraction)
- ✅ No root required
- ✅ No system app required

---

*NOVA Ultimate V4 — Built with Claude AI · Bengali · Hindi · English*
