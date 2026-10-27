# Teacher / 审核教师

Branch: `feature/teacher`

This branch contains the entire runnable baseline. The tasks below describe ownership and future work, not newly completed functionality.

## Stories and estimates

| Story | Scope | MoSCoW | Size | Weight | Baseline status |
|---|---|---|---|---|---|
| TCH-01 | View pending submissions | Must have | S | 2 | Partly implemented |
| TCH-02 | Inspect activity details | Must have | S | 2 | Core implemented |
| TCH-03 | Approve publication | Must have | M | 3 | Core implemented |
| TCH-04 | Return with reason | Must have | M | 3 | Core implemented |
| TCH-05 | Review revised submission | Should have | L | 5 | Planned |
| TCH-06 | View my decisions | Could have | M | 3 | Planned |

Core implemented means the main path exists and acceptance still needs checking. Size is whole-story relative effort, not remaining hours.

## Main files and methods

- `src/main/kotlin/edu/campus/routes/TeacherRoutes.kt`
- `src/main/kotlin/edu/campus/service/CampusService.kt: pending, review`
- `src/main/resources/static/app.js: review view`
- `src/main/kotlin/edu/campus/model/Models.kt (shared submission/review timestamps or records)`

## Suggested task order

- [ ] Verify pending-only list, complete details and no state change from viewing.
- [ ] Add submission dates; confirm all-club versus assigned-club review scope with the team.
- [ ] Verify authorised approval and rejection, required return reason and public visibility.
- [ ] Coordinate edits and resubmission with Club: resubmission returns to Pending and retains the previous reason.
- [ ] Record review decisions and timestamps, then add personal review history if capacity permits.

## Coordination

Current returned state is named REJECTED. Agree its mapping to Returned, historical records and important-edit review rules with Club. Do not publish a resubmission automatically.

## Validation and handoff

Run `.\gradlew.bat test` (Windows) or `./gradlew test` (Linux/macOS), manually check the affected role and failure paths, then open a PR into `main`. Include behaviour, validation and shared-file changes. See [team workflow](../07-team-collaboration.md).
