# PROGRESS.md

Rough status note, not a task list. Update the area lines when an area's
state changes.

## Working

- Backend auth + user identity — Spring Boot scaffold, Firebase Auth
  verified server-side, `USER` entity + auto-provisioning, Flyway-managed
  schema, `GET /api/v1/ping` and `GET /api/v1/users/me`, tests passing.

## Now

- Nothing in flight.

## Not touched

- Trip / itinerary / expense domains.
- Frontend (web + mobile).

## Gotchas

- `feat/backend-firebase-auth-scaffold` is **not merged** into `main` —
  `main` is still just the initial commit.
- Postgres is **local-only** (docker-compose, `localhost:5432`) — no
  Aiven/Neon or other remote DB configured anywhere in the repo.
