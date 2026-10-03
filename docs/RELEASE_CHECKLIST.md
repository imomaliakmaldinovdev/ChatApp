# Week-one release gate

Release status: **blocked pending teacher review and live Firebase setup**. A successful local build is a review artifact, not a production release.

## Reproduce local verification

Use the Android Studio JDK, a running Android emulator and the Firebase CLI described in BACKEND.md.

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
firebase emulators:exec --project demo-chat-app --only auth,firestore './gradlew -PuseFirebaseEmulators=true connectedDebugAndroidTest'
./gradlew -PuseFirebaseEmulators=false assembleDebug connectedDebugAndroidTest
```

The last command restores a normal debug APK after emulator testing. The release build always disables emulator mode and is unsigned; configure owner-controlled signing only when preparing an approved release. Do not distribute an emulator APK to users. Never commit a keystore or signing password.

## Two-user acceptance run

Use disposable test accounts in an explicitly selected development project. The local tests create unique fixtures in `demo-chat-app`; they neither wipe shared data nor contact the teacher's cloud project. Stopping the fresh local emulators discards their in-memory fixtures.

1. Register Bob, log out, register Alice. Verify empty chats and validation errors.
2. Search an unmatched name, then Bob. Open Bob's profile and private conversation. Reopening must reuse the same conversation.
3. Send multiline text as Alice. Check its acknowledgement, local timestamp and Bob's incoming message. Send a reply as Bob and verify live delivery without a reload.
4. Disable the client network. Attempt sending; preserve the draft and show an honest failure/waiting state. Reconnect and retry; there must be only one saved message ID.
5. Try creating a conversation offline. Within 15 seconds the spinner must give way to a safe retry. Retrying must reuse the canonical conversation.
6. Recreate the Activity, then sign out and sign in again. Confirm persisted server history and no previous user's draft/history after logout.
7. Verify a non-member cannot read or send to either participant's conversation. A network error is not evidence of authorization rejection.
8. Check narrow portrait, landscape and tablet layouts, a larger font, keyboard visibility, focusable controls and accessible message direction/time. Inspect long names and multiline messages.

`FridayJourneyTest` exercises form registration for both accounts, empty/search states, conversation creation, sending/reply, reload and logout through native controls. AuthFlowTest, DiscoveryFlowTest, MessagingFlowTest and FirestoreRulesTest cover invalid credentials, orphan profiles, network failures, live delivery, deduplication and explicit access rejection. Screenshot inspection complements these tests; it does not replace a physical-device/TalkBack pass.

## Before teacher approval and release

- Accept the GitHub collaborator invitation; review pending PRs in dependency order. Only approved PRs merge into develop. Do not bypass branch protection.
- Supply matching `app/google-services.json`, enable Email/Password Auth and verify Firestore schema, restrictive rules and indexes with the project owner. Never overwrite shared rules without agreement.
- Run the acceptance journey against that cloud development project on two devices. Record account identities only in private test records, not in public issue reports.
- Confirm the approved release has emulator mode off, no debug-only cleartext permission, no debug/wake-lock setting, and no service credentials or private test artifacts.
- Configure release signing/versioning with the owner, validate the signed artifact, and promote reviewed develop to master only after the gate passes.

Deferred: presence, read receipts/unread counts, profile editing, password recovery UI, settings/theme, durable offline outbox, older-history pagination, broad device/OS coverage and advanced visual polish. This gate covers the reduced private text-messaging MVP only.
