# VoTrip — Future Scope & Extensibility Notes

**Document version:** 1.0
**Companion to:** 01-PRD.md, 02-SRS-ERD.md, 03-TechStack.md
**Purpose:** Park ideas and structural considerations that are **explicitly out of MVP** but should inform how MVP is built, so later features don't require destructive schema rewrites. This is a living reference — add to it as new future ideas come up, rather than letting them silently influence MVP scope.

**How to use this doc:** Nothing here is being built now. When designing any MVP feature, skim this file to check "does my current design choice make any of these harder than it needs to be?" — then proceed with the MVP-appropriate, simpler choice unless a change is genuinely free.

---

## 1. Guiding principle

Per the PRD's own philosophy: **don't over-generalize the schema before a concrete feature needs it.** Premature abstraction (e.g., full polymorphic tables, full RBAC) risks guessing the wrong shape and refactoring anyway. This doc exists to *track* future direction, not to justify building it early.

The one exception: **cheap, low-risk changes that prevent painful migrations later** are worth making during MVP even if the feature isn't built yet (see §4).

---

## 2. Confirmed future feature: City/Area-based Community Matching

**Idea (as discussed):** Users opt in to being matched with other users in the same city/area. These users form a group; many people can join. The app suggests trips (nearby getaways, bike rides, treks, etc.) to the group based on shared availability, common holidays, and group interests — distinct from the current model where a Trip Guide manually creates a trip.

### Why this is mostly additive, not disruptive

The itinerary + trip + expense system being built now is **reused as-is**. Once a community group decides on a suggested trip, it becomes a normal `TRIP` record (from a template or blank), with matched members added as standard `TRIP_MEMBER` rows. This feature is a **discovery/matching layer that feeds into trip creation** — not a parallel trip system.

### Anticipated new entities (NOT built now)

| Future entity | Purpose |
| --- | --- |
| `USER_LOCATION` (or city/area fields on `USER`) | Opt-in location for matching — likely city-level granularity, not precise GPS, for privacy |
| `USER_INTERESTS` | Trip-type preferences (bike rides, trekking, road trips) — can reuse existing `trip_type` values |
| `USER_AVAILABILITY` | Free dates/weekends/holidays — genuinely the hardest new data model; a calendar-overlap problem, not a flat field |
| `COMMUNITY_GROUP` | City/area-based group entity — members, region, possibly a trip-type focus |
| `COMMUNITY_GROUP_MEMBER` | Join table, same shape/pattern as `TRIP_MEMBER` |
| `SUGGESTED_TRIP` | System-generated suggestion output; becomes a real `TRIP` once a group accepts it |

### Known hard parts (algorithmic, not schema)

- **Availability matching across many users** — naive overlap-finding scales badly across a large group; needs real algorithm design, not just a query
- **Location/proximity suggestions** — feasible with Google Maps Platform (already in stack), but is new service logic, not a schema concern
- **Privacy** — opt-in location + availability is sensitive. Should be modeled as clearly separate from core `USER` fields with explicit consent flags, and likely excluded from casual SuperAdmin views. Document this constraint explicitly whenever this module is actually designed (same rigor as the money/cascade-delete rules in the current ERD).

### What (if anything) MVP should keep in mind

**Nothing needs to change now.** `TRIP.created_by` and `TRIP.template_id` are already flexible enough — nothing in the current schema assumes a trip must be manually created by a human guide clicking a button. This is a clean example of the current lean schema already being compatible with this future direction without modification.

---

## 3. Other deferred features (already noted in PRD, restated here for one-stop reference)

- **AI itinerary generation** — architecture already keeps this open via a single service boundary (`ItineraryService.generateFromTemplate()` → future `generateFromPrompt()`); itinerary data is structured/JSON-shaped so an AI can write into the same schema a human edits
- **Org/marketplace pages for tour companies** — `ORGANIZATION` entity already reserved in schema (nullable owner on Template/Trip); no UI/billing/storefront yet
- **Real payment processing** — `SETTLEMENT` is designed as a generated/derived table, not source-of-truth, so a payment gateway can be wired in later (e.g., add `external_ref`, `gateway` fields, extend `status` enum) without touching the ledger tables (`EXPENSE_PAYER`/`EXPENSE_SPLIT`) that actually matter
- **Multi-currency support** — `EXPENSE.amount` deliberately has no `currency` field (inherits from `TRIP.currency`); re-add only if multi-currency is actually built
- **Offline-first mobile sync** — no local-first conflict resolution planned for v1
- **In-app chat/messaging** — notifications only in MVP
- **Public itinerary marketplace/discovery** — `ITINERARY_TEMPLATE.is_public` field already exists as a foothold, but no discovery UI/search in MVP
- **Push notifications (FCM)** — fast-follow after MVP, not day-one; `NOTIFICATION` table already modeled to support this later

---

## 4. Schema extensibility watch-list (structural considerations, not commitments)

These are **not action items for MVP** — they're documented so that if/when related features come up, the AI/developer building them checks this list first instead of guessing or duplicating patterns.

| Area | Current MVP state | What to watch for |
| --- | --- | --- |
| **Attachments/comments** | No polymorphic pattern exists | If photos, comments, or reviews are ever needed on more than one entity (trips, itinerary items, expenses), avoid building 2-3 near-identical tables. Consider a generic `entity_type` + `entity_id` pattern (`ATTACHMENT`, `COMMENT`) the first time a second use case appears — not before. |
| **Enums vs. strings** | `trip_type`, `transport_mode` are native Postgres enums; `category` on `EXPENSE` is already a free string (correct precedent) | If these lists are expected to grow with future features, prefer converting `trip_type`/`transport_mode` to app-validated strings (matching `category`'s pattern) — cheap to do early, more disruptive as a later migration once data exists. |
| **Role model** | Single `role` enum on `TRIP_MEMBER` (guide/member), deliberately simple per PRD | If finer-grained permissions become necessary later (e.g., "can edit itinerary but not remove members"), this will need a real `PERMISSION`/`ROLE_CAPABILITY` table. Known future migration, not a surprise one — flag before assuming today's model can just be extended in place. |
| **Location modeling** | `location_name`/`lat`/`lng` stored inline on `ITINERARY_ITEM` | Fine for MVP. If a "explore places" / cross-trip place search / popular-spots feature ever appears, extract into a normalized `PLACE` table referenced by FK — do this *before* place-related features multiply, not after. |
| **Activity/audit log** | Only `NOTIFICATION` (user-facing, read/unread) plus `created_by`/`created_at`/`updated_at` on mutable tables — deliberately no full audit log in MVP | If a "trip activity timeline" feature is ever needed, this requires a distinct append-only `ACTIVITY_LOG` table — `NOTIFICATION` should not be stretched to cover this; they serve different purposes. |
| **Subscription/plan/feature-flags** | No scaffolding exists | Orthogonal to core schema; safe to add later without disturbing trip/itinerary/expense tables whenever monetization or org-level feature gating becomes relevant. |

---

## 5. How to use this file going forward

- When a new future-scope idea comes up in conversation, **add it to §2/§3** rather than letting it quietly shape MVP decisions.
- When building any MVP feature, do a quick pass over **§4** to check for cheap, non-disruptive alignment — but don't pre-build anything on this list "just in case."
- Treat this as a **living document** — update it as scope conversations continue, the same way 01-PRD.md, 02-SRS-ERD.md, and 03-TechStack.md are treated as living specs.
