# Fox implementation brief: RALLE nameplate roles and selected style

Updated 2026-09-27. Implement this slice in `Fox-main`; the client renderer and
settings remain in RALLE's [in-game plan](supporter-cosmetics.md). This is an
implementation request, not merely a request to produce another plan.

Implementation chat: [Implement RALLE cosmetic roles and nameplate API](codex://threads/01a0e311-1d1b-7c80-9798-cd16267a1643)
in Fox-main, gpt-6-sol with high reasoning. Created 2026-09-27; completion is tracked
in that chat.

## Product requirements

Extend the **existing API Diagnostics → RALLE Users page**, including its user
detail view, so website engineers can add/remove cosmetic Supporter, Contributor
and Admin grants. Do not create a separate top-level page or modify the standalone
effect playground. Support identity entry by IGN, backend resolution to canonical
Minecraft UUID, and grant management for existing users and people never seen by
RALLE. A manual cosmetic registration must not fabricate diagnostic connections,
last-seen activity, guild membership or authenticated sessions.

The user confirmed: the in-mod color switcher selects the authenticated player's
own color from their assigned roles, and other enabled RALLE clients see it.
Persist and synchronize the selected style ID in Fox. Material resolution,
text/whole-plate treatment and white glyph-outline preferences stay client-local.
Do not accept arbitrary shader code, URLs, colors, materials or other players'
style preferences from the client.

Cosmetic Admin is a display entitlement only. It grants **no** website login,
engineer/staff permission, Discord permission, guild/alliance eligibility or LFG
authority. Supporter status is not proof of a donation or Minecraft ownership.
No payment integration, subscriptions or automatic expiry are requested.

## Concrete code entry points inspected

- `web/frontend/src/pages/ApiDiagnostics.jsx`: `RalleUsers`, `RalleUserCard`,
  `RalleUserDetail`; existing users tab, filters, 15-second polling and detail modal.
- `web/frontend/src/lib.js`: `adminRalleUsers` / `adminRalleUser` API helpers.
- `web/backend/app/api.py`: `/admin/diagnostics/ralle-users` and
  `/admin/diagnostics/ralle-users/{minecraft_uuid}`, currently protected by
  `require_metrics_access`, with private/no-store responses.
- `web/backend/app/auth.py`: Royal Engineer and site-admin access guards; inspect
  current revalidation, mutation and CSRF protections before adding writes.
- `web/backend/app/diagnostics.py`: existing UUID-keyed `ralle_user` directory and
  diagnostic facts. Cosmetic state must survive diagnostic retention cleanup.
- `web/frontend/src/theme.css`: current RALLE user-directory styles.

Inspect repository instructions, current branch/worktree state, actual SQLite
infrastructure, Minecraft identity verification and LFG protocol code/tests first.
Preserve unrelated in-progress changes. Fit existing architecture rather than
assuming route/table names in this brief already exist.

## Canonical styles — exact input data

Read `nameplate-styles/catalog.json` beside this brief, plus all six original JSON
exports in that directory. Copy a suitable canonical fixture/catalog into Fox so
its build/tests do not depend on another repository or the user's Downloads folder.
Stable proposed IDs and grants:

| ID | Cosmetic grant | Label | Shadow / midtone / highlight |
| --- | --- | --- | --- |
| supporter-gold | supporter | Ralle Gold | #8b5c20 / #e5b94c / #fff0b5 |
| contributor-green | contributor | Contributor Green | #176f9b / #62f500 / #ffffff |
| contributor-green-alt | contributor | Contributor Green Alt | #388a15 / #62f500 / #ffffff |
| contributor-blue | contributor | Contributor Blue | #176f9b / #00b8f5 / #ffffff |
| admin-white | admin | Admin White | #000000 / #000000 / #ffffff |
| admin-red | admin | Admin Red | #000000 / #000000 / #ff0000 |

All materials are liquid: seed 7349, speed 0.45, scale 4, warp 63, shine 71,
treatment plate, notificationBorderLogicalPixels 2. Gold's recipe resolution is 1;
the other five use 0.5. Preserve Green Alt as a distinct approved style.
The original field `gold` denotes the midtone for every palette. Preview IGN,
guild, font, supporter flag, paused state and timestamp are not enrollment data
or authoritative preferences. Never automatically grant the export's sample users.

## Storage and role semantics

Use existing SQLite migrations/init conventions, with a UUID-keyed cosmetic
identity, a highest-tier grant and selected style. Contributor includes Supporter
colors; Admin includes Contributor and Supporter colors. Preserve grants across
name changes. Store canonical/last-known IGN for display, revision and timestamps;
use existing mutation audit facilities for engineer changes where available.
Do not store payment details or add raid/donation analytics.

Make grant and selection mutations transactional. A duplicate add/remove should
have stable, documented behavior. A tier change preserves a still-allowed
selection. If removal invalidates the selection, atomically choose
a deterministic remaining allowed default; clear it when no grants remain. Proposed
default ordering is Gold, Green, Green Alt, Blue, White, Red (catalog order).
Document this reversible fallback choice and test it. Concurrent
revoke/select operations must never leave an unauthorized active style.

## Engineer experience and authorization

Add role badges, role filters and clearly named cosmetic controls to RALLE Users.
Keep connection/activity data and existing filters usable. In a selected user's
detail view, show UUID, canonical IGN, current grants, allowed styles and current
selected style. Engineers manage grants; players select their own style in the mod.
For unseen players, offer an Add cosmetic user flow within this same page:
IGN → resolved canonical profile/UUID → grant choices → save. Resolution failures
must not create guessed or partial records. Prevent case-variant duplicates.

Provide loading, empty, not-found, duplicate, revoked-access, stale/conflicting,
and failed-mutation feedback. Disable repeated submissions while pending and show
success only after server acceptance. Refresh directory/detail after mutations;
polling must not overwrite an unsaved draft. Label Admin as cosmetic where needed
to avoid confusing it with website administration.

Enforce writes server-side for the existing site-admin/Royal Engineer identities
authorized for this feature. Add a narrowly named cosmetic-management capability
if appropriate; **do not turn general read-only metrics access into universal write
authority**. Revalidate role/session revocation and use existing CSRF/request-origin
and rate-limit conventions. Ordinary users and mod sessions cannot assign grants.

## Game-facing API contract

Provide a small, versioned cosmetic catalog and bounded UUID lookup API containing
authoritative grants and the resolved selected style, plus revision/cache-expiry
information. Return only public cosmetic metadata, never engineer identities,
private notes, donation records, diagnostics or login tokens. Avoid a public full
supporter-directory endpoint. Deduplicate/validate UUIDs, cap batch size and response
size, rate-limit requests, and document errors, absent identities and revoked roles.
Unknown/no-grant players return an explicit neutral result so clients clear stale
decorations. A reconnect/fresh lookup must see revocations and new selections.

Add a self-style mutation authenticated through verified Minecraft session ownership.
Derive UUID from the authenticated session, never a request's claimed UUID/IGN.
Accept only a known style ID currently allowed by the server's grants. Reject
cross-player writes, unsupported versions, invalid/unknown styles and revoked grants.
The response returns the canonical selected style and new revision. Make same-value
requests idempotent and rate-limit repeated selection changes.

Cosmetic auth and lookup must work without accidentally granting LFG eligibility.
Inspect whether the existing Minecraft session mechanism is reusable without its
alliance gate; if necessary, introduce a narrowly scoped cosmetic session path
with the same ownership verification and short-lived credentials. Never bypass or
relax the existing LFG auth, create/join eligibility, guild or protocol checks.
Do not depend on Discord linking. Use HTTPS and existing version conventions.

Use existing live-event infrastructure for bounded change notifications where
appropriate, or document finite TTL/revision refresh behavior. Do not create an
authoritative client/bot copy or redesign the LFG protocol gratuitously. If changing
LFG packets is necessary, negotiate compatibility and update fixtures explicitly.
Selection is one deliberate user mutation, not a per-frame request. Endpoint
availability alone must not require a disabled RALLE feature to make requests.

Document exact routes, auth scopes, JSON request/response examples, error codes,
role/style IDs, fallback ordering, rate/batch limits, revisions and cache rules for
the client implementer. Include sample cases: gold-only supporter, contributor
choosing Blue, admin choosing Red, multi-role player, neutral player and revocation.

## Acceptance checks and handoff

- Add/remove each grant; duplicate requests; all role combinations; unseen user;
  renamed/case-variant IGN; failed upstream resolution; SQLite restart/migration.
- Access: unauthenticated/ordinary users rejected; engineers/admins authorized;
  revoked engineer session rejected; cosmetic Admin cannot access engineer routes.
- Selection: permitted styles accepted; cross-role/cross-player/unknown selections
  rejected; forged identity rejected; revoked role and concurrent selection handled
  atomically; removing the last grant clears the selection.
- Lookup: bounded batches, invalid/duplicate UUIDs, neutral results, finite stale
  state, no private fields, version errors, restart/reconnect and live/TTL updates.
- UI: existing diagnostics retained, four named roles states (none + three grants),
  pending/error behavior, filtering, unseen-user management and keyboard usability.
- Existing Minecraft auth and LFG invariants remain covered by regression tests.

Run the narrowest relevant tests first, then repository-prescribed backend/frontend
checks. No real production grants, donation records or deployment are requested.
Finish the implementation in Fox-main and report changed files, tests, remaining
limits and the exact client contract. The RALLE chat will consume that handoff;
do not edit the Minecraft renderer/settings in this task.
