# Tuesday — Authentication (29 September 2026)

Planned effort: 8 hours for one developer (login 1.5h, registration 1.5h, authentication integration 5h).

## Implemented

- Login form with email/password validation, password visibility, loading state, accessible labelled inputs and scrollable keyboard-aware layout.
- Registration form with display name, email, password and confirmation; inline errors and duplicate-submit prevention.
- Firebase email/password registration and sign-in; required public profile creation before protected screen access.
- Profile completion recovery when registration was interrupted between account creation and profile persistence.
- Lifecycle-aware authentication ViewModel; requests survive rotation. Passwords are not saved in view state and are cleared after submission and logout.
- Session restoration and server revalidation on entry; invalid sessions return to authentication. Logout clears visible user inputs and protected state.
- Clear error feedback and an honest setup-pending message when the teacher's Firebase configuration is absent.

The chats screen is a placeholder; Wednesday's conversation work is not included.

## Verification

- Gradle debug build, unit tests and Android lint.
- Final results: 6 unit tests passed; emulator-mode device suite 5 passed/1 mode-specific skip; normal build device suite 3 passed/3 emulator-only skips. No failures. Lint has no errors; remaining warnings concern newer dependency/SDK versions.
- Firebase Auth + Firestore emulators using demo-chat-app, never a live Firebase project.
- Two accounts register, persist profiles, log out, reject incorrect passwords, sign in and revalidate sessions.
- An account without a profile recovers through profile completion; account deletion removes access.
- Full registration form enters chats, survives activity recreation, restores the Firebase session in a fresh activity and signs out.
- Invalid form input is rejected; password and confirmation fields are blank after rotation.
- Normal build launches with Firebase absent and retains the authentication gate.

## Pending external actions

- Teacher's google-services.json, matching com.imomali.chatapp. Confirm Email/Password authentication and the intended database/rules before live verification. No cloud deployment performed.
- Trello updates are blocked by the locked Mac / unavailable Chrome connection. Planned updates: Login UI and Registration UI to DONE; Authentication integration remains pending live verification, with emulator-tested implementation recorded.
- No GitHub remote exists, so this work has not been pushed.

UI reference remains Chatvia Light: https://themesbrand.com/chatvia/layouts/index.html. Native Material screens use the clean messaging style without copying template assets.
