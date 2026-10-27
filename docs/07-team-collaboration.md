# Four-person collaboration / 四人协作

## Branches and responsibilities

`main` contains the runnable Kotlin/Ktor baseline. Every role branch contains the entire project and starts from the same baseline; branch names organise responsibility, not account permissions.

| Branch | Role | Stories | First increment |
|---|---|---|---|
| `feature/student` | Students attending activities | STU-01–03 | Verify registration, then filters and QR access; notifications later |
| `feature/club` | Student club organisers | CLUB-01–05, CLUB-07–08 | Verify creation/rosters/cancellation; add review explanation and remaining places |
| `feature/teacher` | Reviewing teachers | TCH-01–06 | Verify review and add submission dates; resubmission/history later |
| `feature/admin` | Account administrators | ADM-01–10 | Verify account operations; add categories/search and read-only oversight |

CLUB-06 is absent from the supplied stories. ADM-10 (university identity integration) is outside this release.

## Getting started

Install JDK 17 or newer. Clone this repository into a new directory:

```powershell
git clone https://github.com/Qianleqian/F4-Club-Website.git
cd F4-Club-Website
git switch --track origin/feature/student
.\gradlew.bat run
```

Replace `feature/student` with your assigned role branch. Open `http://127.0.0.1:8080`. Run `.\gradlew.bat test` before submitting changes. Linux/macOS can use `./gradlew run` and `./gradlew test`.

## Shared files

- Each owner primarily changes their own `src/main/kotlin/edu/campus/routes/*Routes.kt`.
- Business methods currently share `service/CampusService.kt`; coordinate changes to common helpers.
- Public activity search currently lives in `routes/AuthRoutes.kt`, so the Student owner may need to change that route too.
- All role interfaces currently share `src/main/resources/static/app.js`, `index.html` and `styles.css`. Work on role-specific functions and keep changes small.
- Agree changes to `model/Models.kt`, sessions, password handling, repository persistence and Gradle configuration before integrating them.
- Keep backend permission checks; hiding a button alone does not enforce access control.

## Submission and integration

1. Complete one small task on your role branch, test it, commit and push.
2. Open a Pull Request with base `main` and compare your role branch. Describe the changed behaviour and validation.
3. Ask a teammate to review, then merge after issues are resolved. Keep role branches for ongoing work.
4. With local edits committed, synchronise the role branch:

```powershell
git fetch origin
git merge origin/main
```

If conflicts appear, resolve the affected code with its owner and rerun relevant checks. Do not force-push over teammates' changes.

## Scope and status

The baseline already contains the main registration, review and account-management paths. Full acceptance still needs checking. Filters, QR access, editing, resubmission, review history, notifications and additional administrator views are planned or incomplete. Role branch task notes describe the next work; creating a branch does not implement those features.

For changes to approved activities, agree the review/version policy between Club and Teacher before implementation; notify students when changes take effect. Teachers retain approval/rejection responsibility; administrator oversight is read-only.

Runtime data (`data/`), build output, caches and generated distributions are ignored. Dependencies are restored through Gradle. Server deployment remains separate from a source-code push.
