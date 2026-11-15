# HAIRA AI 2.0

Kotlin + Jetpack Compose Android app targeting API 26+ and arm64 devices.

## Current functionality
- Compose navigation for HAIRA, Studio, Actions, Builder and System.
- Builder exports a Kotlin + Jetpack Compose Android Studio starter project as a ZIP, with app name and validated package ID. It is source export, not in-app APK compilation.
- Room persistence for conversations, messages, notes, approved memories and imported model records.
- DataStore settings for memory enablement and assistant style.
- Android speech-recognition intent and Android TextToSpeech output. Offline recognition depends on installed Android language packs.
- GGUF file picker/import, GGUF header validation and SHA-256 calculation for known catalog models.
- SmolLM2-360M-Instruct Q4_K_M is bundled as an uncompressed APK asset, copied into app-private storage, checksum-verified, and automatically loaded through the llama.cpp JNI backend on first launch.
- Qwen3-0.6B Q4_K_M remains an optional manual download.
- Local chat messages and assistant responses are stored in Room. The app does not fabricate placeholder AI responses when inference is unavailable.
- Chat UI shows actual initialization, installation, verification, loading, ready and failure states. Conversation selector is horizontally scrollable.

## Verification
GitHub Actions:
1. Downloads and verifies the bundled SmolLM2 asset using its pinned SHA-256.
2. Builds the Android debug APK for arm64-v8a and x86_64.
3. Runs unit tests.
4. Starts an Android API 35 x86_64 emulator.
5. Runs an instrumentation smoke test that waits for the bundled model to load, sends a prompt, and verifies that an assistant response is displayed.
6. Uploads the debug APK only after all preceding checks pass.

The emulator test is a runtime smoke test; it does not replace testing on the user's physical Android device.

## Known limitations
- Builder currently exports a starter Android Studio source project; full in-app APK compilation, project templates and asset generation are not implemented.
- Download progress for optional models is handled by Android Download Manager; in-app pause/resume and retry controls are not implemented.
- Conversation search, rename/delete, response streaming/regeneration/stop, advanced Studio tools and full device diagnostics remain incomplete.
- The artifact is a debug APK, not a release-signed APK.
