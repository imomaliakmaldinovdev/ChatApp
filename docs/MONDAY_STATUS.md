# Monday status — 2026-09-28

Implemented in /Users/imomaliakmaldinov/AndroidStudioProjects/ChatApp.

- Scope/reference: complete; native Android adaptation and screen states documented.
- Initialization: complete; debug APK builds and device instrumentation verifies launch and recreation on Medium_Phone (Android 17 / API 37). Move to DONE.
- Architecture: complete for Monday; domain/service contracts, session routing policy and lifecycle-aware Firebase session observer. Feature screens are later work.
- Data model/access rules: complete locally; schema/index and 9 emulator security tests pass. Live deployment belongs to backend setup.
- GitFlow: local master/develop established; current implementation is on develop. master holds charter only. No remote supplied and no feature branches created.
- Backend: SDK/configuration and emulator setup implemented; teacher will supply google-services.json; automatic file integration and package validation are ready. Local Android-to-Firebase auth and Firestore server round-trip pass. Live configuration, service compatibility and rules verification remain pending the teacher file. Keep in DOING.

Verification:
- ./gradlew assembleDebug testDebugUnitTest lintDebug — PASS
- 4 Kotlin unit tests — PASS
- Firestore emulator: 9 rule tests — PASS
- Android lint: 0 errors, 5 advisory SDK/dependency version warnings. Versions intentionally match the available local toolchain.
- Android instrumentation, missing-config mode: 2 passed, 1 backend-specific test skipped.
- Android instrumentation, local-emulator mode: 2 passed, 1 missing-config-specific test skipped.
- Verified launch/recreation, guarded unconfigured screen, local anonymous test auth, Firestore server write/read and sign-out. Anonymous auth is used only as a local connectivity test, not the planned product login method.
- Firebase JSON integration: matching synthetic file generates resources; mismatched package fails with a clear plugin error. These synthetic files exist only in a scratch copy, never in the real project.
- Broader visual/device coverage remains Friday scope; no real-cloud test has been claimed.
- No real Firebase project, cloud deployment, production data, or Git remote was created/changed.

Next: receive teacher’s google-services.json, check its Android package against com.imomali.chatapp and verify the teacher’s intended Firebase service/schema. Place a matching file in app/ and sync; confirm auth, database and access rules before live testing. Do not replace shared teacher-project rules without agreement. Tuesday adds actual login/registration UI and authentication operations.
