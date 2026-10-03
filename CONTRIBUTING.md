# GitFlow
- master: validated, stable baseline/releases only.
- develop: integration branch for ongoing work.
- feature/<name>: create from develop when starting a real task, not in advance.

```sh
git switch develop
git switch -c feature/auth
# implement, test, commit
git push -u origin feature/auth
gh pr create --base develop
# Enable once the PR is ready for teacher review:
gh pr merge --auto --merge
```
Run ./gradlew assembleDebug testDebugUnitTest lintDebug. For security/backend changes, run the Kotlin FirestoreRulesTest suite against local Auth and Firestore emulators using the commands in docs/BACKEND.md. No project JavaScript dependencies are required. Validate the app on an emulator/device before a release. Only merge develop into master when release criteria pass; tag actual releases. Never force-push, delete history, or commit local.properties, firebase.properties, tokens or service-account keys.

Repository: https://github.com/imomaliakmaldinovdev/ChatApp (public). develop is the default integration branch; unapproved changes remain on feature branches. If a task depends on an unmerged feature, stack it on that branch and identify the dependency in its PR. Review the earlier PR first. master remains the stable charter baseline until an actual release is reviewed.

Both develop and master require a pull request, one approving review, code-owner approval and resolved review conversations. Protection applies to administrators too; direct unreviewed pushes, force pushes and branch deletion are blocked. New changes dismiss stale approval, and the latest push must be approved by someone other than its pusher.

RustamAkbaraliyev is the code owner for every file. He must accept the repository write-access invitation before GitHub recognizes him as an eligible code owner. Until then, leave PRs pending; do not bypass protection or treat another person's review as the teacher's approval.

Enable auto-merge separately on each ready PR. GitHub merges it after the required approval and other protection requirements pass, provided it has no merge conflicts. Review requests for code owners are automatic once their access is active. Do not merge locally into protected branches. For releases, open a separate develop-to-master PR only after release criteria pass; enable auto-merge on that PR when ready for teacher approval.

Existing implementation was pushed before this policy was established; these rules govern subsequent changes. Do not create feature branches until needed.

Trello: move the active card TO DO -> DOING -> TEST -> DONE after its actual acceptance criteria pass. Record blocked cloud setup honestly. Preserve full-scope follow-ups when accepting the reduced week-one implementation.
