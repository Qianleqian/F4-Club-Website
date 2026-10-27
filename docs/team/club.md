# Club organiser / 学生社团组织者

Branch: `feature/club`

This branch contains the entire runnable baseline. The tasks below describe ownership and future work, not newly completed functionality.

## Stories and estimates

| Story | Scope | MoSCoW | Size | Weight | Baseline status |
|---|---|---|---|---|---|
| CLUB-01 | Publish an activity | Must have | M | 3 | Core implemented |
| CLUB-02 | Understand review | Should have | XS | 1 | Partly implemented |
| CLUB-03 | See registrations and remaining places | Should have | S | 2 | Partly implemented |
| CLUB-04 | View participant list | Must have | S | 2 | Core implemented |
| CLUB-05 | Edit activity details | Should have | L | 5 | Planned |
| CLUB-07 | Confirm cancellation | Must have | S | 2 | Core implemented |
| CLUB-08 | Keep changed information consistent | Should have | M | 3 | Partly implemented |

Core implemented means the main path exists and acceptance still needs checking. Size is whole-story relative effort, not remaining hours.

## Main files and methods

- `src/main/kotlin/edu/campus/routes/ClubRoutes.kt`
- `src/main/kotlin/edu/campus/service/CampusService.kt: myActivities, create, participants, cancel`
- `src/main/resources/static/app.js: club dashboard, creation, participants and cancellation`

## Suggested task order

- [ ] Verify creation fields, invalid capacity/deadlines and Pending review visibility.
- [ ] Add an explicit teacher-approval explanation to pending activity details.
- [ ] Show remaining places and verify counts, no duplicate participants and empty states.
- [ ] Verify cancel confirmation and unchanged activity when dismissed.
- [ ] Add editing with capacity/deadline validation; coordinate returned-activity resubmission and review policy for changes to published activities.
- [ ] Keep detail, management and student views consistent after effective changes.

## Coordination

CLUB-06 is absent in the supplied file. Agree important-edit review rules with Teacher before implementing CLUB-05/08; keep cancellation immediately effective.

## Validation and handoff

Run `.\gradlew.bat test` (Windows) or `./gradlew test` (Linux/macOS), manually check the affected role and failure paths, then open a PR into `main`. Include behaviour, validation and shared-file changes. See [team workflow](../07-team-collaboration.md).
