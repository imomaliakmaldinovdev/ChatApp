# Architecture
One Android app module, Kotlin + XML ViewBinding and Material components. Keep the first week small; split modules only when boundaries justify it.

- MainActivity: accessible login/registration forms, local validation, and lifecycle-scoped observation of AuthViewModel. Password fields opt out of saved view state and are cleared on submission, mode changes and logout.
- AuthViewModel: survives rotation, owns one in-flight Firebase request, observes auth state, verifies restored sessions against the server, creates the required public profile before exposing chats, and supports recovery from interrupted registration. Request generations reject obsolete callbacks after logout. No password or Activity is retained. Server failures keep protected screens closed. Firebase persists its own session credentials; the app stores no passwords.
- navigation/SessionRouter: pure routing policy. WELCOME is public; CHATS, PROFILE and CONVERSATION require a session. The activity currently renders authentication or a chats placeholder only after the ViewModel verifies the session and profile. Do not expose unguarded deep links.
- domain/Models: immutable profile/conversation/message data and message length policy.
- domain/Repositories: auth/profile/conversation/message contracts. Subscribe methods return closeable handles; close on screen exit/logout. Messaging contracts intentionally have no fake-success implementation.
- data/FirebaseBackend: named Firebase client, explicit config status, emulator isolation and memory-only Firestore cache.
- data/FirebaseAuthRepository: actual Firebase session observer and logout adapter.
- ChatApplication: application-owned backend composition root; no Activity references retained.

Later implementation: feature ViewModels own loading/content/error state, delegate to repository implementations, and expose UI state. No database calls in XML views. Firestore server rules, not the route guard, enforce privacy. Clear feature state and subscriptions on logout/account change. Memory-only cache avoids persistent on-disk message history; server history remains durable.

Configuration status means client initialization, NOT proven backend reachability. Emulator mode is debug-only. Release builds ignore useEmulators and never enable cleartext traffic.
