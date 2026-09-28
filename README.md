# Chat App
Native Android private messaging MVP. Monday foundation implemented; login/registration UI and messaging are later milestones, not functional features yet.

## Open and build
Open this directory in Android Studio. Use its bundled JDK 21 (or newer supported JDK), SDK 36, and Gradle wrapper 9.4.1. Android Gradle Plugin 9.2.1 provides built-in Kotlin. Min SDK 24; target SDK 36.

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug
```
Android Studio creates local.properties with your SDK path. It is intentionally ignored. Dependencies need internet on the first build. Run the app configuration on an Android emulator/device; the initial screen is a foundation screen, not simulated authentication.

## Backend modes
With no configuration, the app starts safely without Firebase and explains setup in DEBUG builds. It never reports successful cloud connection just because the SDK initialized.
When received, put the teacher’s google-services.json in app/ and sync Gradle. For local emulator mode, copy firebase.properties.example to firebase.properties and set useEmulators=true. See docs/BACKEND.md. No secrets or admin keys belong in this Android app.

## Documentation
- docs/SCOPE.md — UI reference and reduced five-day scope
- docs/ARCHITECTURE.md — modules, routes and ownership
- docs/DATA_MODEL.md — collections, constraints, queries and index
- docs/BACKEND.md — local testing and pending cloud setup
- CONTRIBUTING.md — GitFlow and verification
- docs/MONDAY_STATUS.md — actual verification and outstanding work

Trello: https://trello.com/b/CXiHwZj7/chat-app
Plan: https://trello.com/c/34sHAx3P
