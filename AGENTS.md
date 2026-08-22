# AGENTS.md

This file provides guidance to Codex and other coding agents when working with code in this repository.

## Repo Layout

Single repository, two applications, independent toolchains:

```
VoTrip/
├── docs/        → 01-PRD.md, 02-SRS-ERD.md, 03-TechStack.md, 04-FutureScope.md
├── backend/     → Spring Boot API (Maven) — the one source of truth
└── frontend/    → Turborepo: Next.js web + Expo mobile + shared packages
```

Only one backend — both clients call the same Spring Boot REST API. Never
put business logic in a client that isn't also enforced server-side. Each
side builds/lints/deploys independently; only the repo and history are
shared.

## Commands

```bash
# backend/
docker compose up -d postgres
./mvnw spring-boot:run
./mvnw test
./mvnw verify
```

```bash
# frontend/
pnpm install
pnpm dev                  # web + mobile dev servers
pnpm build
pnpm lint
pnpm type-check

cd apps/web && pnpm dev       # Next.js only
cd apps/mobile && pnpm start  # Expo only
```

## Architecture

**Stack:** Next.js (web) + Expo/React Native (mobile) · Spring Boot (Maven)
API · PostgreSQL · Firebase Auth · Cloud Run / Vercel / EAS.

**`backend/src/main/java/com/votrip/`:** package-per-domain (`trip`,
`itinerary`, `expense`, `user`), layered
`controller/service/repository/dto/entity`. Modular monolith — no
microservices split for MVP.

**`frontend/`:**
```
apps/web/       → Next.js: public SSG/ISR zone (/trips/[slug]) + client-rendered app zone
apps/mobile/     → Expo, iOS + Android
packages/shared-types/   → Trip, ItineraryItem, Expense, User type defs
packages/api-client/     → typed functions calling the Spring Boot API
packages/validation/     → Zod schemas (split sums, item date ranges)
packages/domain-logic/   → settle-up algorithm, drag-drop position math
```
UI/screens are NOT shared between web and mobile — only types, API calls,
validation, and pure logic are.

**Auth flow:** Firebase Auth issues JWTs → both clients attach the token →
Spring Security verifies it via Firebase Admin SDK → Spring Boot (never the
client) decides authorization. Firebase proves identity only.

**Data flow:** React/Expo → `api-client` → Spring Boot controller → service
→ repository → Postgres.

## Domain Rules That Are Easy to Get Wrong

Full detail in `docs/02-SRS-ERD.md` — condensed must-not-break list. These
apply on both sides of the repo — the frontend's assumptions about data
shape and ordering follow directly from these backend rules.

- `position` is a **gapped sort key** (1000/2000/3000). Reordering writes
  one new value between neighbors — never renumber siblings on every
  drag-drop. A position collision on write must fail and retry with a
  recalculated value, not silently overwrite.
- Rebalance positions when the gap between neighbors drops too low (e.g.
  < 2) — this routine needs to actually exist.
- All money fields are `DECIMAL(10,2)` — never `FLOAT`, no exceptions, on
  either side of the repo.
- `EXPENSE.itinerary_item_id` / `EXPENSE.travel_leg_id` are `ON DELETE SET
  NULL`, never `CASCADE`. Deleting an itinerary item must never delete the
  money record — only unlink it.
- `DAY.template_id` / `DAY.trip_id` is XOR (exactly one non-null, DB check
  constraint). Template → Trip cloning is a row-copy with the FK swapped,
  and creates an **independent copy** — editing a live trip must never
  mutate the source template.
- `EXPENSE_PAYER` and `EXPENSE_SPLIT` are separate tables, not columns on
  `EXPENSE` — don't collapse them; this is what supports uneven multi-payer
  splits.
- `SETTLEMENT` rows are generated/derived, recomputed from
  `EXPENSE_SPLIT`/`EXPENSE_PAYER` — not source-of-truth. `status` only
  tracks manual "mark as paid."
- A trip must always keep ≥1 `TRIP_MEMBER` with `role='guide'` — enforce at
  the service layer on member removal (this isn't expressible as a DB
  constraint).
- Itinerary items can legitimately cross midnight (`end_time < start_time`)
  — treat as "ends next calendar day," not an error.
- `USER` rows are never hard-deleted — deactivate via a flag only.
- `EXPENSE.amount` has no currency column on purpose (inherits
  `TRIP.currency`, single-currency MVP) — don't add one speculatively.
- Trip joining has two paths (invite-code self-join vs. explicit
  invite-then-accept) that both write to the same `TRIP_MEMBER` table — no
  schema fork.

## Authorization

- Every role check is server-side, on every endpoint — never trust a
  client-sent role, even from a same-repo frontend change that "obviously"
  keeps them in sync.
- Checks are resource-level, not just role-level: "is this user a member of
  *this* trip" resolves through `TRIP_MEMBER`, not `Trip.created_by` or a
  global user role.
- A single account can be Trip Guide on one trip and plain Member on
  another — role is scoped per-trip via `TRIP_MEMBER.role`, never global.
- Members can mark item status but not edit the itinerary — fixed
  permission for MVP, not per-trip configurable. Don't build a permissions
  editor.

## Shared Utilities — Use, Don't Reimplement

- Settle-up / debt-simplification algorithm → `frontend/packages/domain-logic`
  — and keep the backend's `SETTLEMENT` recomputation logic in sync with it
  deliberately (same repo, no excuse for two implementations drifting
  apart).
- Drag-and-drop position math → `frontend/packages/domain-logic` — the
  piece most likely to silently diverge between web and mobile if not
  shared.
- Cross-field validation (split sums, date ranges) →
  `frontend/packages/validation` (Zod), re-enforced server-side. Client
  validation is UX only, never the actual guard — especially for money.
- Shape changes to Trip/Expense/User → design the backend DTO first, then
  update `frontend/packages/shared-types` to match — the backend is the
  source of truth for the contract.

## Code Standards

- Strict TypeScript on the frontend, no `any`.
- Zero lint warnings enforced.
- REST API versioned (`/api/v1/...`), consistent error shape via
  `@ControllerAdvice`.
- Validation duplicated on purpose: client (`validation` package) for fast
  feedback, server (Spring Boot) as the actual guard.

## UI & Responsive Design

- Mobile-first testing — this is a used-on-the-move app. No hover-only
  interactions on anything touch needs (reordering handles, status
  toggles).
- Touch targets ≥ 44×44px, especially drag handles.
- Adding an expense mid-trip is the most-repeated action in the product —
  don't add friction to that flow (extra required fields, slow loads,
  unnecessary steps).
- Itinerary/expense screens should load well under ~1.5s on average mobile
  network — watch for N+1 queries on these two screens specifically.
- Drag-and-drop parity between web and native mobile costs real
  engineering time — don't assume a shared gesture library solves it for
  free; test both platforms explicitly.

## Working Across Both Sides in One Session

A single session can implement a feature vertically — backend endpoint
through frontend UI — in one pass, since both live in this repo. Design
the backend DTO first and treat it as final for the task; don't let a
frontend convenience reshape the API contract. Keep backend and frontend
changes separable in the diff even within one session, so they're still
easy to tell apart later.

## Local Development Setup

```bash
# backend
cd backend
docker compose up -d postgres
./mvnw spring-boot:run

# frontend
cd frontend
pnpm install
pnpm dev
```

No auth bypass in local dev — use a real Firebase dev/staging project with
test accounts rather than mocking identity, since authorization logic lives
downstream of a verified token.
