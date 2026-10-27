# Administrator / 账号管理员

Branch: `feature/admin`

This branch contains the entire runnable baseline. The tasks below describe ownership and future work, not newly completed functionality.

## Stories and estimates

| Story | Scope | MoSCoW | Size | Weight | Baseline status |
|---|---|---|---|---|---|
| ADM-01 | View accounts | Must have | S | 2 | Core implemented |
| ADM-02 | Create accounts and assign roles | Must have | M | 3 | Core implemented |
| ADM-03 | Disable accounts | Must have | M | 3 | Core implemented |
| ADM-04 | Restore accounts | Must have | XS | 1 | Core implemented |
| ADM-05 | Categorise accounts | Should have | S | 2 | Planned |
| ADM-06 | View all-club activities | Should have | M | 3 | Planned |
| ADM-07 | View organisers and participants | Should have | M | 3 | Planned |
| ADM-08 | Help beside login | Could have | XS | 1 | Planned |
| ADM-09 | Search accounts | Could have | S | 2 | Planned |
| ADM-10 | University identity integration | Won't have this time | XL | 8 | Won't this release |

Core implemented means the main path exists and acceptance still needs checking. Size is whole-story relative effort, not remaining hours.

## Main files and methods

- `src/main/kotlin/edu/campus/routes/AdminRoutes.kt`
- `src/main/kotlin/edu/campus/service/CampusService.kt: users, createUser, setEnabled`
- `src/main/resources/static/app.js: accounts, new-user and planned oversight views`
- `src/main/resources/static/index.html (help entry if needed)`

## Suggested task order

- [ ] Verify list/create/disable/restore, duplicate validation and valid club association.
- [ ] Verify disabled users are blocked even with old sessions, self-disable is prevented and relationships survive restoration.
- [ ] Add Students/Teachers/Administrators display groups; identify Club Staff within Students without changing stored roles or permissions.
- [ ] Add account search within the selected category, clear-search and empty states.
- [ ] Add read-only all-club activities with status/club filters, then organiser and participant details with correct counts/times.
- [ ] Add the small anonymous help entry beside login if capacity permits.
- [ ] Keep university identity integration outside the current release.

## Coordination

Approval/rejection remain Teacher operations. Public lists must still hide unpublished content. Never return password hashes in account or people views.

## Validation and handoff

Run `.\gradlew.bat test` (Windows) or `./gradlew test` (Linux/macOS), manually check the affected role and failure paths, then open a PR into `main`. Include behaviour, validation and shared-file changes. See [team workflow](../07-team-collaboration.md).
