# GitFlow
- master: validated, stable baseline/releases only.
- develop: integration branch for ongoing work.
- feature/<name>: create from develop when starting a real task, not in advance.

```sh
git switch develop
git switch -c feature/auth
# implement, test, commit; review before merging
git switch develop
git merge --no-ff feature/auth
```
Run ./gradlew assembleDebug testDebugUnitTest lintDebug. For security/backend changes, run the Kotlin FirestoreRulesTest suite against local Auth and Firestore emulators using the commands in docs/BACKEND.md. No project JavaScript dependencies are required. Validate the app on an emulator/device before a release. Only merge develop into master when release criteria pass; tag actual releases. Never force-push, delete history, or commit local.properties, firebase.properties, tokens or service-account keys.

Local master starts with the project charter only: no unverified app code is designated release-ready. Monday implementation is committed on develop. No remote is configured until the owner provides the repository URL. Do not create feature branches until needed.

Trello: move the active card TO DO -> DOING -> TEST -> DONE after its actual acceptance criteria pass. Record blocked cloud setup honestly. Preserve full-scope follow-ups when accepting the reduced week-one implementation.
