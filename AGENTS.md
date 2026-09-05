# AGENTS.md

This file provides guidance to coding agents — Claude Code, Codex, and others — when working with code in this repository.

## Keeping CLAUDE.md and AGENTS.md in Sync

CLAUDE.md and AGENTS.md must always contain the same instructions. Any edit
to one must be applied to the other in the same commit. If you find them out
of sync, stop and flag it before continuing.

## Project Overview

VoTrip is a trip-planning platform where a Trip Guide builds a reusable
itinerary template once, spins up an actual trip from it, and manages the
group's shared expenses in the same place — itinerary planning and a
Splitwise-style expense ledger, fused into one product instead of two
disconnected tools.

**Single repository, two applications:**

```
VoTrip/
├── backend/     → Spring Boot API (Maven) — the one source of truth
└── frontend/    → Turborepo: Next.js web + Expo mobile + shared packages
```

Different toolchains (Java vs. TypeScript) living in one repo is fine —
they build, lint, and deploy independently; only the repo and the
version-controlled history are shared. There is only ever **one backend**.
Web and mobile are two clients of the same API — never implement business
logic in a client that isn't also enforced server-side.

Full product context lives in `/docs` — read the relevant file before
making a non-trivial design call:

- `docs/01-PRD.md` — vision, scope, what's explicitly deferred
- `docs/02-SRS-ERD.md` — functional requirements, full ERD, and the
  constraints that don't fit in a diagram (cascade rules, uniqueness,
  business rules)
- `docs/03-TechStack.md` — architecture, why each service was chosen
- `docs/04-FutureScope.md` — deferred features and the schema watch-list;
  check this before over- or under-building something that touches a
  future feature

## Progress Tracking

Progress is tracked in `PROGRESS.md` at the repo root — GitHub Issues are
**not** the task source of truth. Work is picked ad-hoc, not from an issue
queue. When a piece of work is finished, move its line from "Not started" to
"Done" in `PROGRESS.md` in the same commit.

## Commands

Run from repo root unless noted. Each side builds independently — a
backend-only change never needs to touch `frontend/`, and vice versa.

### Backend (`backend/`, Maven)

```bash
cd backend
docker compose up -d postgres   # local Postgres
./mvnw spring-boot:run          # run API
./mvnw test                     # unit + integration tests
./mvnw verify                   # full build incl. checks
./mvnw flyway:migrate           # apply DB migrations
```

### Frontend (`frontend/`, turbo-orchestrated)

```bash
cd frontend
pnpm install
pnpm dev                  # web + mobile dev servers
pnpm build
pnpm lint
pnpm type-check
pnpm format
```

Single app:

```bash
cd frontend/apps/web && pnpm dev       # Next.js only
cd frontend/apps/mobile && pnpm start  # Expo only
```

Mobile build/deploy (EAS):

```bash
cd frontend/apps/mobile
eas build --platform all --profile preview
eas submit --platform ios
```

## Architecture

### Backend layout (`backend/src/main/java/com/votrip/`)

Package-per-domain, layered structure:

```
config/       → Spring Security, Firebase Admin SDK init
common/       → @ControllerAdvice, shared exceptions, base entities
user/
trip/
itinerary/    → Day + ItineraryItem live here
expense/
(organization/ → reserved, unused in MVP)

each domain: controller/ service/ repository/ dto/ entity/
```

Modular monolith. No microservices split for MVP — don't introduce one
unprompted.

### Frontend layout (`frontend/`)

```
apps/
  web/       → Next.js (public SEO zone + authenticated app zone)
  mobile/    → Expo (React Native, iOS + Android)
packages/
  shared-types/    → Trip, ItineraryItem, Expense, User, Role type defs
  api-client/      → typed functions calling the Spring Boot API
  validation/      → Zod schemas (expense splits, itinerary item date ranges)
  domain-logic/    → settle-up algorithm, drag-drop position math, date/tz formatting
```

UI components, navigation, and screens are **not** shared between web and
mobile — only types, API calls, validation, and pure logic are. Don't try
to force a shared component layer; that was a deliberate call (see
`docs/03-TechStack.md` §13).

### Data flow

React/Expo → `api-client` (typed) → Spring Boot controller → service →
repository (Postgres). Firebase Auth issues the JWT; Spring Security
verifies it on every request via the Firebase Admin SDK. **Firebase only
proves who the user is — Spring Boot always decides what they're allowed
to do.** Never trust a role or trip-membership claim coming from the
client.

### Next.js zone split

- **Public zone** (`apps/web/app/(public)/trips/[slug]/page.tsx`) —
  SSG/ISR, no auth, reads published `ITINERARY_TEMPLATE` data
  server-side. Optimize for SEO (Metadata API, sitemap, JSON-LD
  `schema.org/TouristTrip`).
- **App zone** (`apps/web/app/(dashboard)/...`) — client-rendered, behind
  an auth guard, calls the API via `api-client`. Treat it like a normal
  SPA; SEO doesn't matter here.

Don't leak app-zone patterns (client-only data fetching, auth guards) into
the public zone, and don't try to make the public zone dynamic/authenticated
— that defeats the reason it exists.

## Domain Model & Business Rules (the ones that are easy to get wrong)

These come straight out of `docs/02-SRS-ERD.md` — treat them as
load-bearing, not suggestions. If you're touching itinerary, trip, or
expense code on **either side of the repo**, re-read the relevant section
of that file first — these rules constrain both the backend schema and
whatever the frontend assumes about it.

- **`position` is a gapped sort key** (e.g. 1000/2000/3000), not a tight
  sequence. Reordering writes a new value between neighbors (moving
  between 1000 and 2000 → write 1500) — never renumber every sibling row
  on a drag-drop. `day_number` is a derived display value recomputed from
  position order, not the write target.
- **Position collisions fail the write and retry with a freshly
  recalculated position** — this is what actually satisfies concurrent-edit
  safety (FR-4.4), not "last write wins" as a hand-wave. Enforced by a
  composite uniqueness constraint on `(day_id, position)` /
  `(template_id, position)` / `(trip_id, position)`.
- **Position rebalancing**: when the gap between two adjacent positions
  drops below a threshold (e.g. < 2), renumber the whole `Day`/`Trip` back
  to clean, evenly-spaced values. This needs to actually exist, not just
  be theoretically possible.
- **All money fields are `DECIMAL(10,2)`, never `FLOAT`.** No exceptions —
  floating point rounding is unacceptable in a shared-expense ledger. This
  applies to backend entity types and any frontend arithmetic on money
  values equally.
- **`EXPENSE.itinerary_item_id` and `EXPENSE.travel_leg_id` are `ON DELETE
  SET NULL`, never `CASCADE`.** Deleting an itinerary item or travel leg
  must never delete the money record tied to it — only unlink it. This is
  the one cascade rule most likely to get "simplified" wrong during a
  refactor.
- **`DAY.template_id` / `DAY.trip_id` is XOR** — exactly one is non-null,
  enforced by a DB check constraint. `DAY` is intentionally shared between
  templates and trips so cloning a template is a straightforward row-copy
  with the FK swapped, not two parallel schemas.
- **Cloning a template into a trip creates an independent copy.** Editing
  a live trip's itinerary must never mutate the original template. There
  is no live link back.
- **`EXPENSE_PAYER` and `EXPENSE_SPLIT` are separate tables**, not columns
  on `EXPENSE` — this is what makes "multiple people paid, split unevenly
  among a different set of people" possible. Don't collapse them.
- **`SETTLEMENT` rows are generated, not source-of-truth.** They're the
  output of running debt-simplification over
  `EXPENSE_SPLIT`/`EXPENSE_PAYER` and should be recomputed on demand, not
  treated as the ledger itself. `status` only tracks manual "mark as paid"
  confirmations — there is no payment integration in MVP.
- **A trip must always retain at least one `TRIP_MEMBER` with
  `role='guide'`.** This is a service-layer rule, not a DB constraint —
  enforce it explicitly on member removal (reject, or require promoting
  another member first).
- **An itinerary item can legitimately cross midnight** (`end_time <
  start_time`, e.g. 11:00 PM – 2:00 AM). Treat this as "ends the following
  calendar day," not an error and not a negative-duration bug.
- **`USER` rows are never hard-deleted.** Deactivate via a flag — nearly
  every table holds a `user_id` FK that financial/historical records
  depend on.
- **`EXPENSE.amount` has no currency field on purpose** — it inherits
  `TRIP.currency` (single currency per trip in MVP). Don't add a
  per-expense currency field speculatively; see `docs/04-FutureScope.md`
  if multi-currency ever actually gets built.
- **Trip joining has two paths writing to the same `TRIP_MEMBER` table**:
  shareable `invite_code` (instant self-join, no approval) and explicit
  guide-sent invite (`status='invited'` → `'joined'` on acceptance).
  Regenerating the invite code invalidates old links but never affects
  already-joined members. Don't fork the schema for these — they're the
  same table, two code paths.

## Authorization

Role matrix (SuperAdmin / Trip Guide / Member) is defined in full in
`docs/02-SRS-ERD.md` §2 — the short version:

- Role checks are **always server-side**, on every endpoint. A client-sent
  role claim is never trusted, no matter how "obviously fine" it looks
  from a UI-only change.
- Most checks are **resource-level, not just role-level** — "is this user
  a member of this specific trip" (query `TRIP_MEMBER`, not
  `Trip.created_by`) matters more than "is this user a Trip Guide
  somewhere." Access control always resolves through `TRIP_MEMBER`.
- A single account can be a Trip Guide on one trip and a plain Member on
  another — never assume a global role for a user; role is scoped to the
  trip via `TRIP_MEMBER.role`.
- Members can mark itinerary item status but cannot edit the itinerary
  itself — this is a fixed permission for MVP (see PRD §4.3), not
  per-trip configurable. Don't build a permissions editor for this.

## Shared Utilities — Use, Don't Reimplement

- **Settle-up / debt-simplification** — lives in
  `frontend/packages/domain-logic`. Write and test it once; both web and
  mobile consume it. Keep the backend's `SETTLEMENT` recomputation logic
  deliberately in sync with it (same algorithm, same rounding rules) —
  since this repo holds both sides, there's no excuse for the two drifting
  apart silently.
- **Drag-and-drop position math** — also
  `frontend/packages/domain-logic`. The piece most likely to get quietly
  reimplemented differently on web vs. mobile if it isn't shared.
- **Validation** — `frontend/packages/validation` (Zod). Split sums, item
  date ranges, and other cross-field rules get validated identically on
  both clients, then re-validated server-side. Client-side validation is a
  UX nicety, never the actual guard.
- **Types** — `frontend/packages/shared-types`. If a Trip/Expense/User
  shape needs to change, change the backend DTO first, then update this
  package to match — treat the backend as the source of truth for the
  contract, not the other way around.

## Code Standards

- Strict TypeScript on the frontend; no `any` types.
- Zero lint warnings enforced.
- Prettier + consistent formatting; don't hand-format around the
  formatter.
- REST API versioned (`/api/v1/...`); consistent error response shape via
  `@ControllerAdvice` so both clients can parse errors the same way.
- Input validation is duplicated **on purpose** — `validation` package on
  the frontend for fast feedback, the same rules re-enforced in Spring
  Boot. Never trust client-side validation alone, especially for money.

## UI & Responsive Design

Drag-and-drop parity across web and native mobile was flagged in the PRD
as a genuine cost center, not something a shared library solves for free —
budget real time for it, and don't assume the same gesture library
behaves identically on both platforms without testing.

- All new UI must work at mobile width — this is a travel app used
  one-handed on the move as often as at a desk. Test narrow viewports
  first, not as an afterthought.
- No hover-only interactions on anything that needs to work on mobile
  (item status toggles, expense entry, reordering handles).
- Touch targets ≥ 44×44px, especially on drag handles and item status
  controls.
- Adding an expense mid-trip is the single most-repeated action in the
  product (PRD §6) — any friction added here (extra required fields, slow
  loads, multi-step flows for the common case) is a regression, not a
  neutral change.
- Itinerary and expense screens should load in well under ~1.5s on
  average mobile network conditions (NFR in `docs/02-SRS-ERD.md` §4) —
  watch for N+1 query patterns on these two screens specifically, since
  they're read constantly.

## Working Across Both Sides in One Session

Since backend and frontend live in the same repo, a single session can
(and often should) implement a feature vertically — endpoint, DTO,
`shared-types` entry, `api-client` call, and UI in one pass. When
doing this:

- Design the backend DTO shape first, treat it as final for the task, then
  match `shared-types`/`api-client` to it — not the reverse.
- Don't let a frontend convenience shape a backend response. If the UI
  wants data reshaped, do it in the frontend, not by bending the API
  contract.
- Keep commits (or at least logical diff chunks) separable by side even
  within one session/PR, so a reviewer — or a future `git blame` — can
  still tell backend and frontend changes apart.

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

Both `apps/web` and `apps/mobile` point at the local Spring Boot API. No
auth bypass in local dev — use a real Firebase project (dev/staging) with
test accounts rather than mocking the identity layer, since so much of the
authorization logic lives downstream of a verified token.

## Definition of Done (applies to every issue)

1. Implement only what's in the issue's scope. Flag ambiguity, don't
   guess or expand scope.
2. Write unit + integration tests covering the happy path and any real
   edge cases implied by 02-SRS-ERD.md / 01-PRD.md (constraints,
   cascades, enums, auth boundaries, concurrency where relevant).
3. Run the full test suite (./mvnw verify) — must be green.
4. Manually verify against a real dev account/token (curl) before
   committing.
5. Report back in two separate short messages:
   (A) What changed — plain language, no code, as if to a product owner.
   (B) Tests + verification — what was tested, the actual pass/fail
       output pasted (not just "tests passed"), and anything you're
       unsure of or couldn't verify.
6. Do not commit until steps 3 and 4 are both green.
7. Do not start the next issue until I've reviewed (A) and (B) from
   this one.

## Data Layer Convention

- Schema changes are Flyway migrations (`src/main/resources/db/migration`),
  never relied on via Hibernate `ddl-auto=update`/`create`. Set
  `ddl-auto=validate` so the app fails fast if entities and migrations
  drift apart.
- Each issue that touches the schema includes its own migration file
  (e.g. `V2__create_users_table.sql`) written to match 02-SRS-ERD.md
  exactly — field types, nullability, unique constraints, check
  constraints, and cascade rules (ON DELETE SET NULL vs CASCADE) as
  specified there, not inferred from JPA defaults.
- Before starting Issue 1 (User entity), confirm Flyway is already
  configured from the walking skeleton (#1). If it's on ddl-auto
  instead, fix that first as a small prep step and tell me before
  proceeding.
