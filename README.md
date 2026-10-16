# Pdf Kit

An offline PDF toolkit for Android, built with Kotlin and Jetpack Compose. Every tool runs on the phone: no uploads, no account, no backend.

## Download the latest APK

**[Download PdfKit.apk](https://github.com/Phaneendraaa/Pdf-App/releases/latest/download/PdfKit.apk)**

Every push to `main` is built by GitHub Actions and published to [Releases](https://github.com/Phaneendraaa/Pdf-App/releases). The link above always points to the newest build. On your phone, allow "Install unknown apps" for your browser the first time.

## Tech

- Kotlin, Jetpack Compose, Material 3
- PdfBox-Android for PDF editing, Android `PdfRenderer` for viewing
- ML Kit Document Scanner, Gemini Nano (ML Kit GenAI) where the phone supports it
- minSdk 26, targetSdk 35

## Build locally

Open the project in Android Studio and run the `app` configuration, or:

```
./gradlew assembleRelease
```

## Signing

`app/keystore/dev.jks` is a **development key** committed so that each CI build installs over the previous one. Do not use it for the Play Store. Create a private upload key and keep it out of the repo before publishing.

## Progress

See [PROGRESS.md](PROGRESS.md) for what is built and what comes next.
