# VoTrip — Product Requirements Document (PRD)

**Document version:** 1.0
**Owner:** Shovan (SuperAdmin / Developer)
**Status:** Draft for build planning
**Note:** "VoTrip" is a placeholder working name (a similar-sounding app "Vohtrip" already exists). Revisit naming before public launch — not a blocker for MVP build.

---

## 1. Vision

VoTrip is a trip-planning platform where a **Trip Guide** (a friend, a local travel club organizer, or eventually a tour company) builds a reusable itinerary once, spins up an actual trip from it whenever a batch of travelers wants to go, and manages the entire group's shared expenses in the same place — without needing a separate spreadsheet for the plan and a separate app for splitting the bill.

**One-line pitch:** "Plan the trip, invite the people, split the money — all in one place, reusable every time you run the trip again."

## 2. Problem it solves

- Trip planning content (itinerary, what-to-carry, what-to-do) is currently redone from scratch or copy-pasted across docs every time a similar trip runs (e.g., a Goa trip guide re-plans "5N/6D Goa" every batch).
- Group expense tracking (Splitwise) lives in a completely separate app, disconnected from *when* and *where* in the trip the expense happened.
- Each traveler's own transport (bus/train/flight into the destination) isn't captured anywhere structured, even though it affects both planning and shared costs (shared cabs/tickets).

## 3. Target users (personas)

| Persona | Who they are | What they need |
| --- | --- | --- |
| **SuperAdmin** | You, the platform owner | Full visibility/control across all trips and guides, for support and platform health |
| **Trip Guide** | Friend organizing a group trip, or a small club/organizer (Hyderabad Adventure Club-style), eventually a tour company | Build reusable itineraries, spin up trips from them, manage members, oversee trip finances |
| **Member (Traveler)** | Anyone invited to a trip | View itinerary, add their own travel-to-destination info, add/view expenses, see their balance |

VoTrip is **role-flexible, not org-specific** — the same account can be a Trip Guide on one trip and a plain Member on another. This is important enough to be a real requirement, not just a nice description.

## 4. MVP Scope (what we are building now)

### 4.1 In scope

1. **Auth & onboarding** — simple sign-up/login, minimal friction
2. **Itinerary Templates** — reusable, generic itinerary (e.g., "Goa 5N/6D") a Trip Guide authors once
3. **Trips** — created by attaching a template, then independently editable per trip instance (editing a live trip never mutates the original template)
4. **Itinerary detail model** — day-wise, time-ranged, ordered items with location, description, notes, transport mode, and per-item status
5. **Drag-and-drop reordering** — of days, and of items within and across days — on web and mobile
6. **Member management** — invite, join, remove; per-member optional travel-to-destination entry with multi-person tagging (shared ticket/cab groups)
7. **Splitwise-style expense tracking** — scoped per trip, expenses optionally linked to a specific itinerary item, running balances, simplified settle-up suggestions
8. **Trip lifecycle status** — Planning → Fixed → Ongoing → Ended / Cancelled
9. **Basic in-app notifications** — trip/itinerary changes, new expenses

### 4.2 Explicitly out of MVP (deferred, but architecture stays open to them)

- **AI itinerary generation** — future; itinerary data model is agent-friendly (structured JSON-shaped items) so an AI can later write into the same schema a human edits today
- **Org/marketplace pages for tour companies** (MMT-style package storefronts) — future; data model reserves an `organization` entity now so trips *can* later belong to an org without a schema rewrite
- **Real payment processing** — settle-up is a "mark as paid" ledger action, not a money-movement integration
- **Multi-currency support** — single currency (INR default) for MVP
- **Offline-first mobile sync** — app requires connectivity; no local-first conflict resolution in v1
- **In-app chat/messaging** — use notifications only; no group chat in MVP
- **Public itinerary marketplace / discovery** — trips are invite-only, not publicly browsable in MVP

### 4.3 Deliberately simplified for v1 (called out so it's a choice, not an accident)

- **Settle-up algorithm**: simple greedy debt-simplification (minimize number of transactions), not a configurable custom algorithm
- **Notifications**: in-app only for MVP; push notifications (FCM) are a fast-follow, not day-one
- **Roles**: exactly three (SuperAdmin, Trip Guide, Member) — no custom/granular permission editor in MVP

## 5. Core user flows

1. **Guide builds a template** → adds days → adds itinerary items per day → publishes template
2. **Guide creates a trip** → picks a template (or starts blank) → sets trip dates, name, cover → itinerary is cloned into the trip, editable independently from here on
3. **Guide invites members** → members join via link/code → each member optionally logs their own arrival travel (mode, date/time, tag co-travelers sharing that ticket)
4. **Trip runs** → guide/members mark itinerary item status (done/skipped/failed) → anyone adds an expense at any point, optionally attached to the current itinerary item → balances update live
5. **Trip ends** → guide marks trip "Ended" → final settle-up view shown, ledger stays viewable/archived

## 6. Success metrics (for you to judge MVP health, not vanity numbers)

- A guide can go from "new trip" to "shareable itinerary + invited members" in under 10 minutes
- Adding an expense mid-trip takes under 15 seconds (this is the single most-repeated action — it must never feel like friction)
- Reordering a day's plan (drag-drop) works reliably on both platforms without data loss

## 7. Risks / honest complexity notes

- **Two full sub-systems in one MVP** (itinerary planning + full Splitwise clone) is a genuinely sizeable build, even solo with an AI coding agent — this is not a weekend app. Treat it as two build phases within the same MVP, not two separate features bolted together.
- **Drag-and-drop parity across web + native mobile** will cost real time — budget for it explicitly rather than assuming a shared library solves it for free.
- **Per-person travel-mode tagging feeding into Splitwise** couples two modules (Members and Expenses) — get the data model right early since retrofitting this linkage later is painful.
