# SOUMYA AI Assistant — Feature Status (v1.2.0, LIA edition)

Status labels: **Implemented** · **Permission-dependent** · **Platform-restricted** · **Pending (v2)**

| Feature | Status | Notes |
|---|---|---|
| Splash → Setup Wizard → Character Select → Home | Implemented | LIA-style 7-step wizard on first run (Play Protect, Restricted Settings, Accessibility, Overlay + Background, API keys, Auto-Send Permission, Personality/Voice/Model); reopenable from Home/Settings |
| Groq AI (llama-3.3-70b-versatile, llama-3.1-8b-instant fallback) | Implemented | Free console.groq.com key, EncryptedSharedPreferences, test connection, same friendly Hindi errors as Gemini |
| AI provider picker (Auto / Gemini only / Groq only) | Implemented | Auto = Gemini first, Groq fallback on failure |
| Personality presets (JARVIS / Helpful / Funny / Formal) | Implemented | Injected into the system prompt; Settings + Setup Wizard |
| Model picker (Gemini + Groq) | Implemented | Gemini: gemini-3.6-flash/3.7-flash/2.5-flash; Groq: llama-3.3-70b-versatile/llama-3.1-8b-instant |
| WhatsApp message (chat opens, message prefilled) | Implemented | Contact name or direct number; default composer mode (user taps Send); Auto-Send ON ho to khud bhej deta hai |
| WhatsApp Auto-Send (one-time toggle + Accessibility) | Implemented, default OFF | User ke khud ke account se, sirf uske bole hue message par; AccessibilityService clicks Send once per armed request (com.whatsapp:id/send, fallback content-description); 30s timeout par honest fallback; ek tap me band hota hai (Home / Settings / Automation Center) |
| Facebook Auto-Post (one-time toggle + Accessibility) | Implemented, default OFF | Same design as WhatsApp Auto-Send; Post button by label/content-description (no stable resource id — phone par test baaki) |
| YouTube Music play | Implemented | YouTube Music app → YouTube app → browser; reports which opened |
| YouTube video play | Implemented | YouTube app → browser search |
| Facebook post (composer opens, text prefilled) | Implemented | Default composer mode (user taps Post); Auto-Post ON ho to khud publish kar deta hai |
| Overlay / Draw over other apps permission | Implemented | SYSTEM_ALERT_WINDOW in manifest; PermissionGate OVERLAY key + Permissions dashboard row + Setup Wizard step |
| Animated character (breathing, speaking overlay, mood states) | Implemented | Generated portraits + Compose animation |
| Real-time 3D human + full lip-sync | Pending (v2) | Needs a separate Unity project + heavy GPU work; NOT in v1 |
| AI chat with Gemini + Groq (dynamic model: gemini-3.6-flash first, 404 fallback) | Implemented | Needs the user's free API key(s) in Settings; model auto-resolved per key, specific friendly errors, Test connection button, verified-key chip |
| Gemini/Groq function calling (11 tools) | Implemented | open_application, get_battery_status, get_device_info, open_system_settings, create_reminder, search_memory, save_memory, send_whatsapp_message, play_youtube_music, play_youtube_video, create_facebook_post |
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
| Permissions dashboard | Implemented | 14 permissions (incl. Overlay + Auto-Send) with plain-language purpose, state, and settings deep-links |
| Auto-Send permission gate (PermissionGate AUTO_SEND) | Implemented | isGranted = koi Auto-Send toggle ON + Accessibility granted; send_whatsapp_message / create_facebook_post Auto-Send ON hone par sirf Accessibility maangte hain, OFF hone par composer mode (koi extra permission nahi) |
| Gemini API key storage | Implemented | EncryptedSharedPreferences (Android Keystore); never hardcoded |
| Settings (character, voice, language, wake word, background) | Implemented | |
| About screen with honest limits | Implemented | |
| Real-device testing | Pending | Build-verified via GitHub Actions only; no real phone test yet |
| Play Store release | Pending | Needs a signed release build + store listing (future) |
