# Chat App
Native Android private messaging MVP. Foundation, authentication, live chats discovery, public profiles, user search and reusable private conversations are implemented. Message composition and history remain Thursday's milestone. See docs/WEDNESDAY_STATUS.md for verification and pending cloud setup.

## Open and build
Open this directory in Android Studio. Use its bundled JDK 21 (or newer supported JDK), SDK 36, and Gradle wrapper 9.4.1. Android Gradle Plugin 9.2.1 provides built-in Kotlin. Min SDK 24; target SDK 36.

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug
```
Android Studio creates local.properties with your SDK path. It is intentionally ignored. Dependencies need internet on the first build. Run the app configuration on an Android emulator/device. Without Firebase configuration, the forms validate input and explain that setup is pending; they never simulate successful authentication.

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
- docs/TUESDAY_STATUS.md — authentication implementation and verification
- docs/WEDNESDAY_STATUS.md — discovery flows and review status

Trello: https://trello.com/b/CXiHwZj7/chat-app
Plan: https://trello.com/c/34sHAx3P

GitHub: https://github.com/imomaliakmaldinovdev/ChatApp (public). The default branch, develop, contains current development work. master remains the stable baseline. Feature branches start from develop; pull requests require teacher review before merging. See CONTRIBUTING.md for approval and auto-merge steps.
