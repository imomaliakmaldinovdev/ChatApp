# Architecture
One Android app module, Kotlin + XML ViewBinding and Material components. Keep the first week small; split modules only when boundaries justify it.

- MainActivity: foundation UI and lifecycle-scoped session subscription.
- navigation/SessionRouter: pure routing policy. WELCOME is public; CHATS, PROFILE and CONVERSATION require a session. Signed-in welcome resolves to chats. Actual login, registration and feature screen rendering come in subsequent tasks. Do not expose unguarded deep links.
- domain/Models: immutable profile/conversation/message data and message length policy.
- domain/Repositories: auth/profile/conversation/message contracts. Subscribe methods return closeable handles; close on screen exit/logout. Messaging contracts intentionally have no fake-success implementation.
- data/FirebaseBackend: named Firebase client, explicit config status, emulator isolation and memory-only Firestore cache.
- data/FirebaseAuthRepository: actual Firebase session observer and logout adapter.
- ChatApplication: application-owned backend composition root; no Activity references retained.

Later implementation: feature ViewModels own loading/content/error state, delegate to repository implementations, and expose UI state. No database calls in XML views. Firestore server rules, not the route guard, enforce privacy. Clear feature state and subscriptions on logout/account change. Memory-only cache avoids persistent on-disk message history; server history remains durable.

Configuration status means client initialization, NOT proven backend reachability. Emulator mode is debug-only. Release builds ignore useEmulators and never enable cleartext traffic.
