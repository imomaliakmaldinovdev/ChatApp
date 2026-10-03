# Wednesday delivery — 30 September 2026

Completed and verified locally; submitted for teacher review on 1 October. Five reduced week-one tasks, planned budget 8 hours (not an actual time log):

| Task | Estimate | Implemented |
| --- | --- | --- |
| Chats list UI | 2 hours | Native list, name/initials, truncated preview, activity date, empty/loading/error states and conversation selection |
| Chats list integration | 2 hours | Member-only query, live profile/latest-message listeners and activity ordering |
| Profile UI | 1 hour | Read-only current-user and participant profiles, initials and missing-bio fallback |
| User search | 1.5 hours | Debounced name-prefix lookup, self exclusion, 20-result limit, empty/error states and stale-response guards |
| Start private conversation | 1.5 hours | Canonical pair ID, safe concurrent create/reuse, correct destination and immutable membership |

Discovery routes survive Activity recreation. Logout clears private UI state and subscriptions. Auth callbacks from a destroyed ViewModel are invalidated. Firebase rules remain unchanged.

## Verification
- Full local Auth/Firestore emulator device suite: 18 tests, 17 passed, 1 expected missing-configuration skip, no failures. Includes all nine Kotlin security-rule tests.
- Final discovery suite after search-field layout adjustment: 3 passed, no failures.
- Final normal build: assembleDebug, testDebugUnitTest, lintDebug and connectedDebugAndroidTest successful. Nine unit tests passed. Normal device suite: 3 passed, 15 expected emulator-only skips. USE_EMULATORS=false in the delivered APK.
- Android 17 Medium_Phone emulator. Search/profile screenshots inspected; search label spacing corrected. Wider physical devices and cloud integration have not been verified.
- Discovery tests cover public profile search/self exclusion, no results, concurrent/repeated create, membership isolation, live preview/reordering, profile navigation, rotation and logout during pending search.

## Review and remaining scope
Branch: feature/discovery. No direct merge into develop or master. The branch includes the pending Kotlin security-test migration from PR #2, plus the approval-workflow documentation from PR #1; these earlier changes remain subject to teacher review too.

RustamAkbaraliyev's write invitation is still pending. He must accept it and approve the PR before it merges. Trello Wednesday cards stay in TEST pending review and cloud validation.

The teacher's google-services.json is still required for live Firebase verification. No cloud rules or indexes have been deployed. Follow docs/BACKEND.md; never overwrite a shared teacher project's rules without agreement.

Thursday: full message timeline, composer, sending/receiving and persistence verification. Follow-ups outside Wednesday's reduced scope: unread badges/counts, presence, profile editing, pagination/listener cost optimization, fuzzy search and final visual polish. The current conversation destination displays only the latest preview, not a full message history.
