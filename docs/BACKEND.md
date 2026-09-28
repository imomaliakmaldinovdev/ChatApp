# Firebase setup
## Local isolation (no cloud account needed)
Requires Node 22+, pnpm and Java 21+. From the project root:
```sh
pnpm install --frozen-lockfile
pnpm test:rules
pnpm emulators
```
Rules tests start/stop a Firestore emulator automatically and use only project demo-chat-app. Emulators bind localhost. Do not point the tests at a real Firebase project; they clear test data. Android emulator accesses host services at 10.0.2.2.

Copy firebase.properties.example to firebase.properties and set useEmulators=true. Launch emulators before running the Android app. Auth port 9099, Firestore 8080. Local fake client configuration is used automatically. No service-account credentials needed. If using a physical device, create a deliberate local development connection; do not expose emulator ports publicly.

## Real development environment (owner action pending)
1. Create/select a dedicated Firebase development project in your own Google account. Choose region deliberately; any billing/terms choices belong to the owner.
2. Register Android package com.imomali.chatapp. Enable Email/Password authentication and create Firestore with restrictive rules (not test mode).
3. Copy public client projectId, applicationId (Firebase app ID, not Android package), apiKey into ignored firebase.properties. Set useEmulators=false. Never supply an admin/service-account key. Client config is compiled into the APK and is not a secret; rules secure the data.
4. After reviewing the target project ID, deploy firestore.rules and firestore.indexes.json with Firebase CLI using an explicit --project YOUR_DEV_PROJECT_ID. No deployment has been performed by the scaffold.
5. Create two test users and verify real auth, participant read/write, non-member denial and server persistence. Record the result before calling cloud setup complete.
6. Use a separate Firebase project for production. Do not put production data into development tests. Configure release credentials deliberately and rerun release/access checks.

The current application initializes Firebase when configured and observes its session. Registration/login forms and message repository implementation are future milestones. SDK initialization alone is not a connectivity check.
Official references: https://firebase.google.com/docs/android/setup and https://firebase.google.com/docs/rules/unit-tests
