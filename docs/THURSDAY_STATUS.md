# Thursday delivery — 1 October 2026

Six reduced week-one tasks implemented locally. Planned budget: 8 development hours, not an actual time log.

| Task | Estimate | Implementation |
| --- | --- | --- |
| Private chat screen UI | 1.5 hours | Active participant, incoming/outgoing wrapping bubbles, profile/back navigation, keyboard-aware composer |
| Message composer | 1 hour | Multiline text, blank rejection, 4,000-character limit, Send/Retry, IME Send and hardware Enter/Shift+Enter |
| Send text messages | 1.5 hours | Stable client IDs, server timestamps, retained draft after failure, waiting/failed state and idempotent retry |
| Receive realtime messages | 2 hours | Member-only live snapshots, duplicate-ID reconciliation, listener cleanup on chat switch/background/logout |
| Message history and persistence | 1.5 hours | Latest 50 server messages in chronological order; verified across a new sign-in session |
| Message timestamps | 0.5 hours | Standard local date/time formatting, full-date accessibility label and pending/unknown fallback |

The Firestore access rules are unchanged. A repository checks the existing server message before retrying; a matching ID/sender/text is acknowledged rather than duplicated. A conflicting payload cannot overwrite the old message. The UI waits for server acknowledgement and preserves the draft on failure. A 15-second timeout means confirmation is pending, not that the message definitely failed.

Draft and outgoing state survive rotation in the ViewModel. Switching conversations or logging out clears the current draft. No durable offline outbox is implemented; an already-enqueued Firebase write may complete later. Listeners pause in the background and reopen only for the active, verified conversation.

## Validation
Normal assembleDebug, testDebugUnitTest, lintDebug and connectedDebugAndroidTest passed. Fourteen unit tests passed. Full Auth/Firestore emulator device suite: 22 tests, 21 passed and 1 expected missing-configuration skip; all nine security-rule tests and four messaging tests passed. Normal device suite: 3 passed and 19 expected emulator-only skips. A focused keyboard-geometry/rotation/logout UI rerun also passed. Delivered APK has USE_EMULATORS=false.

Tests ran on Android 17 Medium_Phone. The conversation screenshot was inspected with the keyboard visible, wrapping incoming/outgoing text and accessible composer; timestamp alignment and the IME Send action were checked. Wider physical devices and live cloud integration remain unverified.

Coverage includes two-user live delivery, duplicate retries, new-session history, bounded history, identifier conflicts, offline failure/retry, draft rotation, keyboard visibility, logout cleanup, timestamp timezone/midnight cases and stale callbacks.

## Review and external dependencies
Branch: feature/messaging, based on pending Wednesday branch feature/discovery (PR #3). It includes earlier pending changes; review and merge the earlier PRs first to narrow this diff. No direct update to develop or master. Teacher approval is required before merge.

The teacher's write-access invitation is still pending. Live Firebase configuration and verification remain blocked on google-services.json and the teacher project's compatible Auth/Firestore rules and indexes. No cloud deployment or release-readiness claim is made.

Friday remains: broader error/reconnect review, accessibility and wider-device polish, final two-user end-to-end regression and release checks. Deferred scope includes older-history pagination, refined scroll restoration, elaborate date separators, read receipts/unread counts, presence, profile editing and durable offline/reconnect support. Thursday Trello cards remain TEST pending teacher review and live cloud checks.
