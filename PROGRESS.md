# PROGRESS.md

Rough status note, not a task list. Update the area lines when an area's
state changes.

## Working

- Backend auth + user identity — Spring Boot scaffold, Firebase Auth
  verified server-side, `USER` entity + auto-provisioning, Flyway-managed
  schema, `GET /api/v1/ping` and `GET /api/v1/users/me`, tests passing.
  Verified end-to-end against `votrip-dev` with real tokens.

## Now

- Trips — `TRIP` + `TRIP_MEMBER` schema, then resource authz.

## Not touched

- Itinerary / expense domains.
- Frontend (web + mobile).

## Gotchas

- Postgres is **local-only** (docker-compose, `localhost:5432`) — no
  Aiven/Neon or other remote DB configured anywhere in the repo.
- `FIREBASE_WEB_API_KEY` in `backend/.env` is for manual token minting only —
  the Spring app never reads it.
- Profile fields (`displayName`, `avatarUrl`) are captured once at first
  provision and never re-synced from Firebase.
