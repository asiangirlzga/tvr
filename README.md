# Voice TV Remote (tvr)

An Android remote for **Android TV / Google TV** that works over **Wi-Fi** (no Bluetooth needed), with
on-screen buttons and **voice commands**. One APK installs on phones, tablets and Android TV.

## How it works

- **Discovery:** finds TVs on your Wi-Fi via mDNS (`_androidtvremote2._tcp`), or add one by IP address.
- **Pairing:** the TV shows a 6-character code; you type it into the app once. After that the TV
  remembers the phone.
- **Control:** the same protocol the official Google TV remote uses (TLS on ports 6467 for pairing and
  6466 for remote control). The TV's certificate is pinned at pairing time.
- **Voice:** tap the mic and speak. The phone's speech recognizer turns speech into text; the text is
  matched to commands locally (`VoiceCommandParser`) and sent to the TV.

Your phone and TV must be on the **same Wi-Fi network**. It works with:

- **Android TV / Google TV** (pair once with a 6-character code), and
- **CloudWalker "Cloud TV" / Cloud TV OS** (no pairing: tap the TV when it appears).

It does not work with Samsung Tizen, LG webOS, Roku or Fire TV.

### Cloud TV notes

Cloud TV support was written by reading how the official *Cloud TV Remote* app talks to the TV (it announces
itself as `_cloudwalker._tcp` and takes button presses and app launches as small web requests). It has not been
tested on a real Cloud TV yet. The app works out http vs https and key numbers vs key names by itself on the first
connection. If buttons do nothing, the grey note under the TV's name shows the TV's last reply (for example
`GET /input/?keycode=24 → 200`); send that to the developer. There is also a **Send button names** switch on the
remote screen for Cloud TVs. You can add a Cloud TV by IP address if you also know its port.

Voice in this app is recognised on the phone and prefers offline recognition. (The official Cloud TV Remote
sends speech to CloudWalker's servers.)

## Voice commands

| Say | Does |
| --- | --- |
| "volume up", "volume down 5 times", "mute" | Volume / mute |
| "channel 12", "channel up" | Channel |
| "play", "pause", "next", "rewind", "fast forward" | Media |
| "home", "back", "menu", "settings", "input" | Navigation |
| "up", "down", "left", "right", "OK" | D-pad |
| "turn off", "power" | Power (toggles) |
| "open YouTube", "open Netflix", "open Prime Video" | Launch an app |
| "search funny cats on YouTube" | YouTube search (best effort) |
| "आवाज़ बढ़ाओ", "आवाज़ कम", "यूट्यूब खोलो", "band karo" | A few Hindi / Hinglish phrases |

Pick the voice language (English India / Hindi / English US) next to the mic button.
Recognition uses your phone's speech service, which may need internet.

## Getting the APK

**From GitHub:** every push to `main` builds the app via GitHub Actions. Download `tvr-debug.apk` from the
[Releases page](../../releases/tag/latest), or from the workflow run's *Artifacts*.

**Locally:** open the folder in Android Studio and press Run. Or, with Gradle 8.9 and the Android SDK installed:

```
gradle :app:assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/`.

### Installing on Android TV

Enable *Developer options → USB/network debugging* on the TV, then `adb connect <tv-ip>` and
`adb install tvr-debug.apk`; or use a "Send files to TV" app and install from the TV's file manager.
On the TV the app uses D-pad focus (highlighted border), and shows a banner in the launcher.
Voice needs a microphone, which many TVs lack, so use the phone for voice.

## Customizing

- Apps list and voice aliases: `voice/TvApps.kt` (package names can vary by region/TV).
- Voice phrases: `voice/VoiceCommandParser.kt`. Tests: `gradle :app:testDebugUnitTest`.

## Security notes

- The pairing key (a self-signed certificate) is stored in app-private storage and excluded from backups.
- Commands go only to the TV on your local network; voice text is parsed on the phone.
- To revoke a phone, remove it from the TV's paired remotes (or use *Forget* in the app and re-pair later).

## Status

Debug-signed build. Not published to the Play Store. If your TV model behaves differently, open an issue
with what happened at pairing or when pressing keys.
