# Chat App
Native Android private messaging MVP. Foundation, authentication, discovery, public profiles, user search, private conversations and text messaging are implemented. Conversations include a multiline composer, live incoming/outgoing messages, safe retry, local timestamps and the latest 50 persisted messages. Friday adds bounded profile/conversation write waits, retry recovery and keyboard/accessibility improvements. See docs/FRIDAY_STATUS.md for verification and pending teacher/cloud review.

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
- docs/THURSDAY_STATUS.md — messaging flows, verification and remaining release work
- docs/FRIDAY_STATUS.md — quality checks, artifacts and release blockers
- docs/RELEASE_CHECKLIST.md — repeatable two-user checks and release gate
- docs/UI_REVIEW.md — visual polish, screen matrix and APK UI readiness

Trello: https://trello.com/b/CXiHwZj7/chat-app
Plan: https://trello.com/c/34sHAx3P

GitHub: https://github.com/imomaliakmaldinovdev/ChatApp (public). The default integration branch is develop; unapproved work remains on feature branches. master remains the stable baseline. Feature branches normally start from develop; dependent work may stack on a pending feature branch, with that dependency recorded in its PR. Pull requests require teacher review before merging. See CONTRIBUTING.md for approval and auto-merge steps.
