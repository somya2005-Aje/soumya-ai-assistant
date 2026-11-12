# SOUMYA AI Assistant — Feature Status (v1.1.1)

Status labels: **Implemented** · **Permission-dependent** · **Platform-restricted** · **Pending (v2)**

| Feature | Status | Notes |
|---|---|---|
| Splash → Character Select → Home | Implemented | Logo + loading; JARVIS / ALEXA, switchable in Settings |
| Animated character (breathing, speaking overlay, mood states) | Implemented | Generated portraits + Compose animation |
| Real-time 3D human + full lip-sync | Pending (v2) | Needs a separate Unity project + heavy GPU work; NOT in v1 |
| AI chat with Gemini (dynamic model: gemini-3.6-flash first, 404 fallback) | Implemented | Needs the user's free API key in Settings; model auto-resolved per key, specific friendly errors, Test connection button, verified-key chip |
| Gemini function calling (7 tools) | Implemented | open_application, get_battery_status, get_device_info, open_system_settings, create_reminder, search_memory, save_memory |
| Voice chat: STT + TTS, Hindi/English/Hinglish | Implemented | Android SpeechRecognizer + TextToSpeech; male/female voice preference, speed/pitch settings |
| On-device commands without a Gemini key | Implemented | Open apps, battery, device info, time/date, reminders, memory save |
| Long-term memory (Room) | Implemented | Save/search/edit/delete/export/clear-all; passwords & OTPs never saved |
| Open apps by name | Implemented | PackageManager lookup + launch intent, verified result |
| System settings shortcuts | Implemented | Wi-Fi, Bluetooth, display, sound, location, battery via Settings intents |
| File browse / create folder / delete (with confirmation) | Implemented | Storage Access Framework picker + app storage folder |
| Reminders | Implemented | Exact alarms (permission-dependent) + WorkManager fallback |
| Background assistant (foreground service) | Implemented | Persistent labelled notification, Pause / Resume / Disable |
| Wake word "Hey Jarvis / Hey Alexa" (old "Hey Soumya" still works) | Platform-restricted | SpeechRecognizer loop in the foreground service; always-on lock-screen hotword is platform/manufacturer-limited |
| Notification reading & summary | Permission-dependent | NotificationListenerService; grant Notification Access in system settings |
| Accessibility automation (inspect/click/scroll) | Permission-dependent | Optional, disabled by default; never bypasses passwords/OTP/biometrics/payments |
| Permissions dashboard | Implemented | 10 permissions with plain-language purpose, state, and settings deep-links |
| Gemini API key storage | Implemented | EncryptedSharedPreferences (Android Keystore); never hardcoded |
| Settings (character, voice, language, wake word, background) | Implemented | |
| About screen with honest limits | Implemented | |
| Real-device testing | Pending | Build-verified via GitHub Actions only; no real phone test yet |
| Play Store release | Pending | Needs a signed release build + store listing (future) |
