# Friday quality delivery — completed 3 October 2026

Friday's reduced week-one work is prepared for teacher review. Planned budget: 8 development hours, not an actual time log. **Release remains blocked** until teacher approval and live Firebase configuration/verification.

| Task | Estimate | Delivered |
| --- | --- | --- |
| Error handling and retry | 1 hour | Profile writes and conversation creation stop indefinite busy states after 15 seconds; safe retry, generic errors, retained message drafts and stale-result guards |
| Loading and empty states | 0.5 hours | Existing async/empty states verified; labelled loading indicator, explicit unavailable-profile state, duplicate submissions prevented |
| Automated logic and access tests | 1 hour | Essential non-member read/send and immutable-message checks; offline profile/conversation recovery and failed-history/stale-listener coverage |
| End-to-end messaging tests | 1.5 hours | New native-control journey registers both users, searches, creates a conversation, exchanges messages, reloads and logs out; existing realtime/offline suites retained |
| Responsive and accessibility polish | 1 hour | Clipped composer corrected; inline landscape IME; compact short-window composer; grouped accessible rows/messages including direction and timestamp |
| Final regression and release check | 3 hours | Reproducible release checklist, review builds and notes; external release blockers explicitly recorded |

## Behavior and limits

A timed-out write is not claimed to have failed on the server. Conversation retry uses the same canonical pair ID; a late callback cannot redirect the UI after timeout, retry or logout. Profile timeout signs out and allows the user to sign in and finish setup again. Firestore may finish an already-queued write later. No durable offline outbox is promised.

In a short landscape window with the keyboard open, secondary headers and the character counter collapse to preserve the input and Send button; the input scrolls within one visible line while retaining multiline content. Closing the keyboard restores the normal screen. This is a native Android app, not a desktop website.

## Validation record

Validation ran on 2–3 October. Fifteen unit tests passed. The final full Auth/Firestore emulator suite recorded 25 tests: 24 passed, one expected missing-configuration skip, zero failures. This includes all nine authorization tests, both new offline timeout/recovery tests and the native-control two-user journey. The normal device suite passed three tests with 22 expected emulator-only skips.

Final normal assembleDebug, assembleRelease, testDebugUnitTest, lintDebug and connectedDebugAndroidTest passed. Lint reports zero errors and ten existing warnings (dependency/SDK updates, programmatic view constructors and full-list refresh). No dependency upgrades were bundled into the quality changes. Generated debug and release BuildConfig both have USE_EMULATORS=false; the merged release manifest contains no debug/cleartext/wake-lock override. No tracked JavaScript or TypeScript source remains. git diff --check passed.

Screen matrix on Android 17/API 37:

- 360 × 640 dp phone, 130% font: complete native-control journey and keyboard/rotation/logout checks passed.
- 640 × 360 dp landscape phone, 100% font: the same two checks passed, including full composer and Send-button geometry above the keyboard.
- 800 × 1280 dp tablet, 100% font: the same two checks passed.
- Screenshots reviewed for message wrapping, incoming/outgoing contrast, timestamp alignment and visible controls. Emulator size/density/font overrides restored afterward.

The end-to-end flow is automated through actual native controls, complemented by manual screenshot review. It is not a claim of an independently completed human two-device cloud test. Physical devices, TalkBack interaction and older Android versions remain unverified. The repeatable manual two-device checklist is in RELEASE_CHECKLIST.md.

## Release notes for 0.1.0 review build

Includes account registration/login/session restoration, read-only profiles, user search, canonical private conversations, latest-message chat list, realtime private text messages, server persistence, local timestamps and safe retries. Friday hardens slow/offline recovery and small-screen usability.

No production release/tag or merge to master was made. The unsigned release APK is a build-check artifact, not install-ready distribution. The normal debug APK has emulator mode disabled and still requires the teacher's Firebase file for live use.

## Review and external blockers

- Branch `feature/quality` stacks on `feature/messaging` (PR #4), which includes earlier pending work. Review/merge earlier PRs first; no protected branch is updated directly.
- Teacher `RustamAkbaraliyev` must accept the pending write invitation and approve the PR. Auto-merge must continue to respect protected-branch requirements.
- Teacher `google-services.json`, compatible Email/Password Auth, Firestore rules/indexes and a recorded live two-device test are outstanding.
- Owner-controlled release signing and final approval are required before a stable release.

Friday cards remain TEST for review; the final release card explicitly remains blocked. Deferred original full-scope requirements remain follow-up work: unread/read state, presence, profile editing, settings, advanced reconnect/outbox, older-history pagination, comprehensive CI/device coverage and detailed design matching.

The subsequent UI pass on 3 October adds consistent styling, quieter navigation, avatars and a centered tablet column. See UI_REVIEW.md for its separate screenshot and targeted regression results; release blockers above still apply.
