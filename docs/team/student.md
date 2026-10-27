# Student / 学生活动参与者

Branch: `feature/student`

This branch contains the entire runnable baseline. The tasks below describe ownership and future work, not newly completed functionality.

## Stories and estimates

| Story | Scope | MoSCoW | Size | Weight | Baseline status |
|---|---|---|---|---|---|
| STU-01 | Search and filter activities | Should have | L | 5 | Partly implemented |
| STU-02 | Access and register for an activity | Must have | M | 3 | Partly implemented |
| STU-03 | Receive activity updates | Should have | L | 5 | Partly implemented |

Core implemented means the main path exists and acceptance still needs checking. Size is whole-story relative effort, not remaining hours.

## Main files and methods

- `src/main/kotlin/edu/campus/routes/StudentRoutes.kt`
- `src/main/kotlin/edu/campus/routes/AuthRoutes.kt (public activity search)`
- `src/main/kotlin/edu/campus/service/CampusService.kt: published, register, registrations`
- `src/main/resources/static/app.js: discover and registered views`

## Suggested task order

- [ ] Verify success confirmation, registered list, duplicate/full/cancelled/expired rejection.
- [ ] Implement interest/category, time and location filters, including a clear empty result.
- [ ] Provide directly accessible activity details before adding a QR code; handle login and returning to the activity.
- [ ] Implement in-platform change/cancellation notifications after Club/Teacher agree effective change rules.

## Coordination

Agree category fields with Club. Coordinate change events and effective versions with Club/Teacher. Do not expose pending/rejected activities publicly.

## Validation and handoff

Run `.\gradlew.bat test` (Windows) or `./gradlew test` (Linux/macOS), manually check the affected role and failure paths, then open a PR into `main`. Include behaviour, validation and shared-file changes. See [team workflow](../07-team-collaboration.md).
