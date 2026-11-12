# Private Voice Control

Control your Android phone by voice. **Works fully offline. Nothing leaves your phone.**

Say things like "open YouTube", "go home", "scroll down", "volume up three times", "flashlight on",
"take a screenshot". Speech is recognised on the phone by [Vosk](https://alphacephei.com/vosk/)
(open source), using a small English model bundled inside the app.

## Install

1. Download `private-voice-control.apk` from the
   [`voice-latest` release](../../../releases/tag/voice-latest) and install it
   (allow "install unknown apps" for your browser/file manager when asked).
2. Open the app. The first start unpacks the speech model (a few seconds, once).
3. Tap **Allow microphone**.
4. Tap **Open accessibility**, find **Private Voice Control**, switch it on.
   - If the switch is greyed out ("restricted setting"): open **App info** → **⋮** (top right) →
     **Allow restricted settings**, then try again. Android does this for any app installed outside the Play Store.

## Use

- **Tap to talk (default):** tap the mic, say one command. The microphone turns off afterwards.
- **Hands-free:** switch on "Hey phone", then say "Hey phone, volume up". Android shows a
  notification and the microphone indicator for as long as it listens. Uses more battery.

| Say | What happens |
| --- | --- |
| open YouTube / camera / settings | Opens the app (names are matched loosely, e.g. "what's up" finds WhatsApp) |
| go home, go back, recent apps | Phone buttons |
| open notifications, quick settings | Pulls down the panels |
| scroll down / up, swipe left / right | Scrolls the current screen |
| volume up, volume down 3 times, mute, max volume | Media volume |
| flashlight on / off | Torch |
| take a screenshot, lock the phone | Screenshot, lock screen |
| Wi-Fi settings, Bluetooth settings, brightness | Opens that settings page (Android doesn't allow apps to flip these switches) |
| what time is it, battery level, what's the date | Shown on screen |
| call 9 8 7 6 5 4 3 2 1 0 | Opens the dialer with the number. You press call. |
| stop listening | Turns the microphone off |

## Privacy and security

- **No internet permission.** The manifest removes `INTERNET` and the network-state permissions, and the
  build fails if the APK ever requests one. Check any time: Settings → Apps → Private Voice Control → Permissions
  shows only Microphone (and Notifications for the hands-free notice).
- **Audio is processed in memory and never stored.** Heard text is kept only to display the last command, and is lost when the app closes.
- **The accessibility service cannot read your screen.** It requests no events and no window content; it can only
  press system buttons and swipe. Read `access/ControlAccessibilityService.kt` (about 60 lines).
- **Only harmless actions.** There is no command to delete, send messages, make calls without you pressing call, or change security settings.
- No accounts, ads, analytics or cloud backup (`allowBackup=false`).
- Anyone near the phone can speak a command in hands-free mode. Use tap-to-talk if that matters to you.

## Limits

- English only for now. Accuracy of the small model is good for short commands but can struggle with strong accents,
  noise or unusual app names. Speak clearly and close to the phone.
- Android doesn't let apps toggle Wi-Fi, Bluetooth or mobile data directly, so those open the settings page instead.
- Some apps (banking, etc.) block gestures or screenshots. That is the app's choice.

## Build

GitHub Actions builds the APK on every change to this folder (see `.github/workflows/build-voice-apk.yml`). It
downloads the speech model, runs the unit tests, builds, and checks the APK has no network permission.

To build locally: download `vosk-model-small-en-us-0.15.zip` from alphacephei.com/vosk/models, unzip it to
`app/src/main/assets/model-en-us/`, add a file named `uuid` inside it (any text), then run
`gradle :app:assembleDebug` with Gradle 8.9 and the Android SDK.

The speech model is © Alpha Cephei, Apache 2.0.
