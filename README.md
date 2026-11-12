# SOUMYA AI Assistant — Android app (v1.0.0)

**"Your Personal AI. Your World."**

A production Android personal AI assistant: JARVIS / ALEXA characters, Gemini AI brain
with function calling, Hindi/English/Hinglish voice chat, long-term memory (Room),
phone control & automation, background assistant with wake word, notification reading,
reminders, and a full permissions dashboard.

- Package: `ai.soumya.assistant`
- Kotlin + Jetpack Compose, minSdk 29 (Android 10), targetSdk 34
- Build: GitHub Actions (JDK 17) — see `.github/workflows/android.yml`
- Free-only tooling. No secrets in code: the Gemini API key is entered by the user in
  Settings and stored in EncryptedSharedPreferences (Android Keystore).

## Quick start

1. Clone the repo and open `android-app/` in Android Studio (or run `./gradlew assembleDebug` in `android-app/`).
2. Install the debug APK on a phone (Android 10+).
3. Pick your Soumya (Male/Female) on first launch.
4. Get a free Gemini API key at [Google AI Studio](https://aistudio.google.com) and paste it
   in **Settings → Gemini API key**. Without a key the app still works on-device:
   open apps ("open YouTube"), battery/device info, reminders, memory, time/date.

## Screens

Splash → Character Select → Home (animated character, mic, text input, quick actions,
recent memories) → Chat → Memory Center → Automation Center → Permissions → Settings → About.

## Honest limits (v1)

- **No real-time 3D human with lip-sync in v1.** True Unity/Unreal 3D characters need a
  separate Unity project and heavy GPU work. v1 uses high-quality generated portraits with
  Compose-driven animation (breathing, speaking overlay, expression/mood states).
- **Always-on lock-screen wake word is platform-limited.** Android offers third-party
  apps no system hotword API; manufacturers restrict background microphone use. The wake
  word ("Hey Jarvis / Hey Alexa") works via an on-device recognition loop while the foreground
  background service runs; on some phones only with the screen on.
- An ordinary app cannot silently control every setting or third-party app. Every action
  goes through official Android APIs/intents, and the app reports verified success or
  failure — it never claims success without checking.

See [FEATURE_STATUS.md](FEATURE_STATUS.md) for the per-feature status table.

## Privacy

- Memories never store passwords, OTPs or secrets (filtered before saving).
- Notification reading and accessibility automation are optional and need your explicit
  grant in system settings. Nothing is recorded or uploaded without your action.
- Conversations and the Gemini key stay on the device.

## Project layout

```
android-app/                  Android Studio project (Gradle 8.7, AGP 8.5.2, Kotlin 2.0.21)
  app/src/main/
    kotlin/ai/soumya/assistant/
      MainActivity.kt         NavHost: splash → select → home → chat/memory/…
      data/                   Prefs (encrypted key), Room memory DB, secret filter
      ai/                     Gemini REST client + function calling, tool executor,
                              on-device local commands
      speech/                 TextToSpeech + SpeechRecognizer wrapper
      service/                Foreground background service + wake word,
                              NotificationListenerService, AccessibilityService,
                              reminders (AlarmManager + WorkManager)
      ui/                     Theme, animated CharacterView, all screens
    res/drawable/             Logo + JARVIS / ALEXA portraits (webp)
.github/workflows/android.yml  CI: JDK 17 → assembleDebug → APK artifact `app-debug`
```

## Build status

GitHub Actions builds every push to `main` and uploads the debug APK as artifact `app-debug`.
