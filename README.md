# HAIRA AI 2.0

Kotlin + Jetpack Compose Android app targeting API 26+ and arm64 devices.

## Implemented in the current repository
- Compose navigation for HAIRA, Studio, Actions, Builder and System.
- Room persistence for conversations, messages, notes, approved memories and imported model records.
- DataStore settings for memory enablement and assistant style.
- Android speech-recognition intent and Android TextToSpeech output (offline recognition depends on installed device language packs).
- GGUF file picker/import, GGUF magic-header check and SHA-256 calculation; known catalog files are checked against pinned SHA-256 values.
- Android DownloadManager entries for two public GGUF files.
- A llama.cpp CMake/JNI integration configured for CPU inference with mmap enabled.

## Important current limitations
- The native llama.cpp build and real on-device inference have not yet been verified by a successful CI build and device test.
- Model files are copied into app-specific external storage for the native file-path API. Keep the original GGUF file in user storage if you need to re-import after uninstall.
- Download progress is provided by Android DownloadManager notifications; in-app progress controls, pause/resume and retry UI are not implemented yet.
- Conversation search, rename/delete controls, regeneration/streaming/stop, full memory retrieval controls, advanced Studio tools, Builder, full system diagnostics and device installation tests remain incomplete.
- No feature should be considered production-ready until the GitHub Actions build and real Android runtime tests pass.

## Build
GitHub Actions builds a debug APK on pushes and pull requests to main, installs JDK 17, Android NDK 27 and CMake 3.22.1, then uploads HAIRA-AI-2.0-debug-apk if the build succeeds.

No model is bundled in the APK. Download or import a compatible GGUF model after installing the app. The app does not generate placeholder AI responses when no working native model is loaded.
