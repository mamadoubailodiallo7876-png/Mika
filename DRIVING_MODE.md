# Jarvis driving mode

A hands-free layer on top of Mika: say "Jarvis", ask something, and Mika's normal task engine
answers out loud in an offline British voice. All code is in
`app/src/main/java/com/wangzan101/mika/driving/`; nothing in Mika's existing packages was changed.

```
"Jarvis" ──► WakeWordManager (Porcupine)
                 │ detection
                 ▼
            DrivingService ── listen ──► Mika's VoiceInput (your configured STT engine)
                 │ text
                 ▼
            TaskService.start()  ◄── the same path the chat screen uses
                 │ TaskState.Succeeded(answer)
                 ▼
            SentenceSplitter ──► JarvisTTS (sherpa-onnx, Piper en_GB-alan-medium)
                 │
        DrivingService.state / transcript / response  (StateFlows)
                 ├──► DrivingActivity / DrivingScreen   (phone)
                 └──► JarvisCarAppService / MainCarScreen (Android Auto)
```

State machine: `IDLE → LISTENING → PROCESSING → SPEAKING → IDLE`. Interrupt or any error goes back to `IDLE`.
The wake word is paused outside `IDLE` so Jarvis doesn't hear itself.

## 1. Get a free Porcupine access key

1. Sign up at <https://picovoice.ai> (free tier) and open the Console.
2. Copy your **AccessKey**.
3. Put it in `local.properties` (git-ignored; see `local.properties.example`):

   ```properties
   PORCUPINE_ACCESS_KEY=your-key-here
   ```
4. Rebuild. The key is baked into `BuildConfig.PORCUPINE_ACCESS_KEY`.

Without a key the app still builds and runs; the wake word is simply off and you use the on-screen **SPEAK** button.
Check the "Wake word" section of the **Jarvis Driving** screen to see whether your build has a key.

The "Jarvis" keyword is built into Porcupine (`Porcupine.BuiltInKeyword.JARVIS`), so no `.ppn` file is needed.
Picovoice's free tier is limited to personal/non-commercial use and has a cap on active devices; read their terms.

## 2. The voice model (downloads on first use, ~67 MB, Wi-Fi only)

The Piper voice `vits-piper-en_GB-alan-medium` is downloaded from the
[sherpa-onnx `tts-models` release](https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models)
into `filesDir/tts/`. The download starts the first time driving mode starts (or when you tap
**Download voice**), shows a progress notification, and is skipped once the files exist.

- It is a `.tar.bz2` of about 67 MB, not 60 MB. Besides the `.onnx` and `.onnx.json` it contains `tokens.txt`
  and `espeak-ng-data/`, which synthesis cannot run without, so the whole archive is unpacked.
- By default it only downloads on an unmetered network (Wi-Fi). Toggle this on the **Jarvis Driving** screen.
- Until the model is installed (or if it fails to load), Jarvis falls back to Android's built-in text-to-speech,
  so it is never silent.
- The archive is not checksum-pinned (the release asset is mutable and no hash is published).

## 3. Testing the Bluetooth trigger

There is no setting inside Mika's own settings screen (that package is off-limits for this change).
Instead the app has a second launcher icon, **Jarvis Driving**, with:

- permission buttons (microphone, `BLUETOOTH_CONNECT`, notifications),
- toggles: wake word, Bluetooth trigger, "any Bluetooth audio device counts", Wi-Fi-only download,
- **Simulate car connecting**: runs exactly the code the real trigger runs, no car needed,
- **Start driving mode**.

To test with real hardware, grant `BLUETOOTH_CONNECT` and connect to any device whose class is audio/video or whose
name contains one of: `car, auto, bmw, audi, mercedes, toyota, honda, ford, hyundai, kia, volvo, mazda, subaru,
head unit, stereo, radio` (case-insensitive).

**What "auto-launch" really does.** Android blocks background apps from opening screens (10+) and from starting
microphone foreground services (14+). So on connect:
- if you granted Mika "Display over other apps", the driving screen opens directly;
- otherwise you get a high-priority notification, **tap it to start**. Tapping runs from the foreground, which is allowed.

Two things you will notice:
- *Any* Bluetooth audio device (earbuds, speakers) matches the device-class rule, and short keywords like `car`,
  `auto` and `radio` match names such as "Carrie's AirPods". Turn off "Any Bluetooth audio device counts" to rely on
  names only.

## 4. Adjusting the voice speed

`JarvisTTS.DEFAULT_SPEED = 0.92f` in `driving/JarvisTTS.kt`. `1.0f` is the model's natural speed, lower is slower.
It is also a runtime property (`tts.speed`) and is applied to the fallback voice too.

## Android Auto

`JarvisCarAppService` shows a message card (state + last answer, truncated to 120 characters) with one button:
**SPEAK** when idle, **STOP** while it is thinking or talking.

Limitations you should know about:
- **`androidx.car.app.category.OTHER` does not exist** in Car App Library 1.4.0. The manifest declares it as requested,
  but also declares `androidx.car.app.category.IOT`, which the library does define. Android Auto decides which
  categories it will show; a voice assistant does not fit any of them cleanly, so expect Play review friction.
  For development use Android Auto's developer mode (*Unknown sources*) or the Desktop Head Unit.
- `LongMessageTemplate` needs Car API level 2; the manifest sets `minCarApiLevel=1`, so plain `MessageTemplate` is used.
- The host validator allows any host in debug builds and the library's sample allow-list in release builds.
- When the car screen is the only thing in front of you the phone is usually in the background, so it may not be
  allowed to start the microphone service. Start driving mode on the phone first (the Bluetooth notification does this).

## Safety notes

- Mika can operate apps on the phone. Driving mode prefixes each request with an instruction to answer in a few spoken
  sentences and not to start screen-dependent work. That is a prompt, not a guarantee.
- If a task needs a confirmation, a takeover or on-screen input, driving mode **cancels it** and tells you to stop and
  open Mika. It never auto-approves anything.
- "Jarvis" will also be triggered by anyone or anything in the car (passengers, radio) that says it.

## Files

| File | Role |
|---|---|
| `WakeWordManager.kt` | Porcupine on a dedicated thread; `start/stop/pause/resume`; `detections` Flow |
| `JarvisTTS.kt` | sherpa-onnx Piper voice, sentence pipeline, `AudioTrack` playback, Android TTS fallback |
| `JarvisModel.kt` | Model download (OkHttp), tar.bz2 extraction, progress notification |
| `SentenceSplitter.kt` | Strips Markdown, splits an answer into short spoken sentences |
| `DrivingService.kt` | Foreground service (microphone), state machine, STT → Mika task → TTS |
| `DrivingActivity.kt`, `DrivingScreen.kt` | Full-screen Compose UI |
| `DrivingSettingsActivity.kt` | Launcher entry: permissions, toggles, test buttons |
| `BluetoothTrigger.kt` | `ACL_CONNECTED` receiver |
| `JarvisCarAppService.kt` | Android Auto service and `MainCarScreen` |
| `DrivingState.kt`, `DrivingNotifications.kt` | State enum and prefs; notifications |

## Dependencies added

`ai.picovoice:porcupine-android:4.0.2`, `com.github.k2-fsa.sherpa-onnx:sherpa-onnx:v1.13.8` (from JitPack, scoped to
`com.github.*` groups), `androidx.car.app:app:1.4.0`, `androidx.lifecycle:lifecycle-service:2.8.7`, and the existing
`commons-compress` for unpacking the model. The sherpa-onnx AAR is about 49 MB and ships all ABIs; the project's
`arm64-v8a` release split keeps the arm64 APK smaller.

## Licensing: read before you ship

- **sherpa-onnx is pinned to v1.13.8 on purpose.** v2.0.0 removed espeak-ng from the library (its log says
  `--vits-data-dir is deprecated and ignored`), and Piper voices need espeak-ng to turn text into phonemes, so the
  2.x line would not speak this voice correctly. Before moving to 2.x you need an external phonemizer.
- **The 1.x native library bundles espeak-ng, which is GPL-3.0.** I did not confirm how it is linked, but the 1.x
  changelog only drops it in 2.0.0. Mika itself is Apache-2.0, so have someone decide whether shipping that library
  (and the `espeak-ng-data` the model download unpacks) is acceptable for your distribution.
- The voice's training data (`apope_low`, from Mycroft's mimic3-voices) is marked "License: See URL" in the model card
  and I could not find a licence file there. Check it before redistributing audio generated by it.
- Porcupine requires a Picovoice AccessKey and is subject to Picovoice's licence, not Apache-2.0.
- `licenses/THIRD_PARTY_NOTICES.md` and the in-app licenses screen were **not** updated: `LicensesTest` requires every
  notices heading to also appear in `ui/Licenses.kt`, which is outside the `driving` package. Add entries for
  Porcupine, sherpa-onnx, ONNX Runtime and the Android Car App Library in both places.
