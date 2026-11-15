# HAIRA AI 2.0

Kotlin + Jetpack Compose Android app targeting API 26+.

## Current functionality

- Compose navigation for HAIRA, Studio, Actions, Builder and System.
- Bundled SmolLM2-360M-Instruct Q4_K_M model is copied to app-private storage, SHA-256 verified, and loaded through the llama.cpp JNI backend.
- Local chat messages and assistant responses are stored in Room; recent conversation context is passed to local inference.
- Conversation search, selection, rename and delete controls.
- Android speech-recognition intent and TextToSpeech output. Offline speech recognition depends on the installed Android recognition service and language packs.
- GGUF import validates the file header and calculates SHA-256; known catalog files are checked against pinned hashes.
- User-controlled saved memories and local notes.
- Actions tab can connect public GitHub repositories, save their metadata and README excerpts, and include those excerpts as untrusted reference context in chats. Repository code is never executed.
- System settings support an optional HTTPS OpenAI-compatible gateway (such as a self-hosted FreeLLMAPI or OmniRoute instance). API keys are encrypted with Android Keystore. Local SmolLM2 remains the default when online mode is disabled.
- Builder exports a Kotlin + Jetpack Compose Android Studio starter-project ZIP.

## Verification

GitHub Actions downloads and SHA-256 verifies the bundled model, builds the debug APK for arm64-v8a and x86_64, runs unit tests, uploads the APK, and then attempts an API 35 x86_64 emulator inference smoke test.

The emulator smoke test is separate from physical-device testing. A successful APK build and unit-test run alone do not prove that native inference works on every phone.

## Known limitations

- Builder does not yet compile APKs inside HAIRA. It exports a starter Android Studio source ZIP.
- GitHub repository connection currently supports public repositories and README-based context only. It does not run or install FreeLLMAPI/OmniRoute. Those projects are server-side gateways (Docker/Node) and must be hosted separately; HAIRA can call them only after you supply a reachable HTTPS endpoint, model ID and API key. Private repository OAuth and full source indexing are not implemented.
- Android speech recognition may use a cloud recognition service unless the device's selected speech service and language pack support offline recognition.
- Optional model downloads use Android Download Manager; in-app pause/resume and retry controls are not implemented.
- The uploaded artifact is a debug APK, not a release-signed APK.
- Physical testing on the target Redmi device remains required before calling the release production-ready.

No repository source code is executed by the repository connector. Repository README content is treated as untrusted reference material.
