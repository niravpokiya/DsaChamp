# Contest Service

DsaChamp contests use a fixed UTC window and fixed points per problem. An admin creates a scheduled contest from published problems. Players register, submit through the existing judge, and see standings derived from saved verdicts.

## Run locally

Start the regular DsaChamp stack, including the registry, gateway, Question Service, Execution Submission Service, and execution worker. Set these environment variables **before** starting the services so their processes inherit the values.

| Variable | Services that need the same value |
| --- | --- |
| `DSACHAMP_GATEWAY_KEY` | Gateway, Contest Service, Execution Submission Service |
| `DSACHAMP_CONTEST_KEY` | Contest Service, Execution Submission Service |
| `DSACHAMP_WORKER_KEY` | Execution worker, Execution Submission Service |

Use three distinct secrets. Run the Contest Service from this directory with `./mvnw spring-boot:run` or `.\mvnw.cmd spring-boot:run` on Windows. It registers with Eureka as `contest-service` and defaults to port `8096` and MongoDB at `mongodb://localhost:27017/contests`. Override these through `CONTEST_PORT`, `CONTEST_MONGODB_URI`, and `EUREKA_SERVER_URL`.

Send client requests to the gateway with an access token. Keep the service ports private in deployed environments. Enable MongoDB automatic index creation in local development. In deployments that manage indexes separately, create the submission index `{contestId: 1, status: 1, verdict: 1}`.

## API

| Method and path | Purpose |
| --- | --- |
| `POST /api/contests` | Admin creates and schedules a contest. |
| `GET /api/contests?page=0&size=20` | List contests. |
| `GET /api/contests/{id}` | Read a contest. The problem list appears at the opening time. |
| `PUT /api/contests/{id}/registration` | Join or confirm an existing registration. |
| `GET /api/contests/{id}/registration` | Read your registration status. |
| `POST /api/contests/{id}/submissions` | Submit code and receive a judge job ID. |
| `GET /api/code/submit/{jobId}` | Poll your submission through the existing status endpoint. |
| `GET /api/contests/{id}/standings?page=0&size=20` | Read ranked entries and current provisional status. |

Create a contest with a title, a future start and end, and problem slugs that match published questions.

```json
{
  "title": "Saturday Practice Round",
  "startsAt": "2030-01-05T10:00:00Z",
  "endsAt": "2030-01-05T11:00:00Z",
  "problems": [{"slug": "two-sum", "points": 100}]
}
```

For a submission, send `questionId`, `language`, and `sourceCode`. Supported language identifiers are `cpp`, `java`, and `python`. The server sets the user ID, problem metadata, points, and submission time. The start is inclusive and the end is exclusive. Avoid editing or unpublishing a contest problem while its contest is scheduled or running.

Standings award points once per solved problem and include players with at least one solve. Ties use the time of the last first-accepted submission that contributed to the score, then the user ID for stable ordering. Verdicts can arrive after the deadline. The board remains provisional while the contest is open or there are saved submissions awaiting judgment.

## Verification

Run `mvn test` in the Contest Service for the registration, deadline, and standings API tests. To exercise the ranking query against MongoDB, set `CONTEST_TEST_MONGODB_URI` to a test server and run `mvn -Dtest=ContestScoreRepositoryTest test` in the Execution Submission Service. The test uses an isolated, randomly named database.
