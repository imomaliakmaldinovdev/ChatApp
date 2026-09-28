# Firebase setup
## Local isolation (no cloud account needed)
Requires Node 22+, pnpm and Java 21+. From the project root:
```sh
pnpm install --frozen-lockfile
pnpm test:rules
pnpm emulators
```
Rules tests start/stop a Firestore emulator automatically and use only project demo-chat-app. Emulators bind localhost. Do not point the tests at a real Firebase project; they clear test data. Android emulator accesses host services at 10.0.2.2.

Copy firebase.properties.example to firebase.properties and set useEmulators=true. Launch emulators before running the Android app. Auth port 9099, Firestore 8080. Local fake client configuration is used automatically. You can also run ./gradlew -PuseFirebaseEmulators=true connectedDebugAndroidTest to test emulator connectivity without editing firebase.properties. Keep pnpm emulators running for this command. The instrumentation suite wakes the test emulator temporarily, releases its wake lock afterward, and skips tests that do not apply to the current backend mode. No service-account credentials needed. If using a physical device, create a deliberate local development connection; do not expose emulator ports publicly.

## Teacher-provided google-services.json (preferred)
When the teacher sends the Firebase Android config, place it at app/google-services.json and sync Gradle. The official Google Services plugin is enabled only when a config file exists, so the project builds while waiting for the file. The file is ignored by Git.

The JSON must contain an Android client matching applicationId com.imomali.chatapp. If the teacher registered a different package, ask for a matching client configuration (or deliberately agree to change the application ID); do not edit the JSON to fake a match. The plugin rejects mismatched packages. Release-specific files may be placed under app/src/release/ and development files under app/src/debug/; each built variant must have a matching configuration.

Configuration priority: explicit debug emulator mode > Google Services generated resources > legacy firebase.properties client values. Set useEmulators=false or remove that property before testing the teacher's cloud project. No Firebase file is needed for the local emulator tests.

The JSON connects the client to a project; it does NOT enable authentication, create Firestore, deploy rules, or grant administrator rights. Verify the teacher's intended backend is Cloud Firestore (this scaffold uses it), that Email/Password auth is enabled, and that its schema/rules are compatible before testing. Do not overwrite shared teacher-project rules without agreement.

## Real development environment (only if the teacher is not supplying one)
1. Create/select a dedicated Firebase development project in your own Google account. Choose region deliberately; any billing/terms choices belong to the owner.
2. Register Android package com.imomali.chatapp. Enable Email/Password authentication and create Firestore with restrictive rules (not test mode).
3. Download the Android client google-services.json into app/. Alternatively copy public client projectId, applicationId (Firebase app ID, not Android package), apiKey into ignored firebase.properties. Set useEmulators=false. Never supply an admin/service-account key. Client config is compiled into the APK and is not a secret; rules secure the data.
4. After reviewing the target project ID, deploy firestore.rules and firestore.indexes.json with Firebase CLI using an explicit --project YOUR_DEV_PROJECT_ID. No deployment has been performed by the scaffold.
5. Create two test users and verify real auth, participant read/write, non-member denial and server persistence. Record the result before calling cloud setup complete.
6. Use a separate Firebase project for production. Do not put production data into development tests. Configure release credentials deliberately and rerun release/access checks.

The current application initializes Firebase when configured and observes its session. Registration/login forms and message repository implementation are future milestones. SDK initialization alone is not a connectivity check.
Official references: https://firebase.google.com/docs/android/setup and https://firebase.google.com/docs/rules/unit-tests
