# APK UI review — 3 October 2026

The reduced messaging MVP received a visual and usability pass before generating a new review APK. Production release still requires the teacher's approval, Firebase configuration/live validation and release signing.

## Changes

- Consistent purple accents, stronger primary-button contrast, sentence-case button labels and rounded form controls.
- Secondary navigation, profile and sign-out actions no longer compete with the main action as full purple bars.
- Chat/profile initials appear in circular avatars; message bubbles and the composer use the same palette.
- Large screens center a maximum 600 dp content column. Small-screen and landscape keyboard adaptations are retained.
- Registration, search and profile screenshots are now captured during the repeatable native-control journey.

## Review coverage

Visually inspected login, registration, empty/populated chats, search results, profile and private messages. Reviewed wrapping, timestamp alignment, primary/secondary hierarchy, input readability and keyboard/composer geometry. Primary white text on #5143BC has a calculated contrast ratio of 7.29:1; main text on the page background is 14.10:1. These checks do not constitute a complete accessibility audit.

The first cold-emulator run was interrupted by an Android System UI ANR dialog. Those failed journey/keyboard checks were not accepted. Restarting the emulator cleared the system dialog and the clean rerun passed. Four authentication tests also passed.

The native-control journey and full composer/Send geometry checks passed on all four configurations (two checks each): standard 1080×2400 phone at 420 dpi, 360×640 dp phone at 130% font, 640×360 dp landscape phone and 800×1280 dp tablet. Screenshots were inspected, including the keyboard at larger text size and in landscape. Emulator size/density/font overrides were restored.

Final normal assembleDebug, assembleRelease, testDebugUnitTest, lintDebug and connectedDebugAndroidTest passed. Fifteen unit tests passed; normal device tests: three passed, 22 expected emulator-only skips. Lint: zero errors, ten existing warnings. Both generated build configurations have USE_EMULATORS=false. The prior full backend suite (24 passed, one expected skip) remains documented in FRIDAY_STATUS.md; backend logic/rules were unchanged in this visual follow-up.

## Release decision

Ready for teacher UI review and local APK testing within the reduced MVP scope. No observed blocking layout issue in the verified screens; this does not certify all Android devices. Physical-device/TalkBack interaction, older OS versions and a live two-device Firebase run remain outstanding. Detailed design matching and deferred features in SCOPE.md are outside this review.

The normal debug APK is installable for review, with Firebase emulator mode off. Without the teacher's configuration it cannot provide live authentication/messaging. The release APK is unsigned and is not a public distribution artifact. No production APK was published or release tag created.
