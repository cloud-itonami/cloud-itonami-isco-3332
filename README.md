# cloud-itonami-isco-3332

Open Occupation Blueprint for **ISCO-08 3332**: Conference and Event Planners.

This repository designs a forkable OSS business for an independent event planning practice: a venue-setup and material-handling robot manages on-site logistics under a governor-gated actor, so the practice keeps its own planning records instead of renting a closed event-management SaaS.

**Maturity: `:implemented`.** `src/eventplanning/` implements the
`EventPlanningActor` as a `langgraph.graph/state-graph`
(`eventplanning.actor`) wired to an `Event Advisor`
(`eventplanning.advisor`) and an independent `EventPlanningGovernor`
(`eventplanning.governor`), following the itonami actor pattern
(ADR-2607011000): `:intake -> :advise -> :govern -> :decide -+-> :commit
(:ok?) +-> :request-approval (:escalate?, human-in-the-loop interrupt)
+-> :hold (:hard?)`. 14 tests / 29 assertions green (`kbb -M:test`).
HARD invariants (always hold, never overridable): client provenance,
no-actuation (`:effect` must be `:propose`), a registered event basis
for any booking proposal, the proposed contract amount not exceeding
the event's registered budget ceiling (contracting beyond it is
unauthorized spending, not proactive planning), and verified venue
capacity before any booking can be finalized (finalizing without it
is an overcapacity risk, not efficient service). Always-escalate ops
(human sign-off regardless of confidence, mapping this repo's Trust
Controls in [`docs/business-model.md`](docs/business-model.md)):
`:approve-over-budget-vendor-contract` and
`:approve-guest-list-disclosure`.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot performs
the physical domain work**. Here a venue-setup and material-handling robot performs signage placement, seating-layout staging and equipment transport under an actor that proposes
actions and an independent **Event Planning Governor** that gates them. The governor never
dispatches hardware itself; `:high`/`:safety-critical` actions (such as
vendor contract above the client's registered budget ceiling) require human sign-off.

A live sample of the operator console (robotics safety console, shared template) is rendered in [docs/samples/operator-console.html](docs/samples/operator-console.html) — pure-data HTML output of `kotoba.robotics.ui`.

## Core Contract

```text
event brief + budget + venue availability
        |
        v
Event Advisor -> Event Planning Governor -> book vendor/finalize plan, or human sign-off
        |
        v
robot actions (gated) + operating records + audit ledger
```

No automated advice can dispatch a robot action the governor refuses, suppress
an operating record, or disclose sensitive data without governor approval and
audit evidence.

## Capability layer

Resolves via [`kotoba-lang/occupation`](https://github.com/kotoba-lang/occupation)
(ISCO-08 `3332`). Required capabilities:

- :robotics
- :forms
- :audit-ledger

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## License

AGPL-3.0-or-later.
