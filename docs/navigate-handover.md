# Handover: `navigate` + the first settings surface

**Audience:** the implementing agent, fresh context window.
**Status:** designed in conversation 2026-07-19, not yet implemented.
**Read first:** `CLAUDE.md` (sandbox build recipe, margin protocol, verification
workflow — everything environmental lives there, not here). Architecture:
`core/` is the pure domain module, the plugin module is adapters; keep that
dependency rule.

## What we're building

The last unbuilt tool from the original design surface:

```
POST /api/marginalis/navigate   {file, line, anchor_text?}
```

Opens the file in the user's editor and places the caret on the line — the
ephemeral counterpart to a thread: pointing without creating an artifact.
Threads are for things worth saying; navigate is for things worth seeing
("show me where that is" → the editor answers).

## The two decisions already made — do not relitigate

1. **Navigate fires only on explicit user request.** The agent never moves
   the user's caret uninvited — not to be helpful, not mid-explanation, only
   when the user asked to be taken somewhere ("show me", "where is that",
   "take me there"). This is skill-level etiquette in the tradition of
   resolver-is-the-completer: a convention the agent keeps, stated where
   agents read. Update the marketplace skill (see CLAUDE.md for its location
   and the version-bump rule) with the command AND this rule.

2. **This feature ships with the plugin's first settings surface.** Moving
   the user's caret is the first capability a user might reasonably want to
   switch off entirely — etiquette governs the agent, the setting governs
   trust. Settings page ("Marginalis" under the IDE settings tree):
   - `Allow agent navigation` (default ON — every call is user-solicited by
     etiquette; the switch exists for users who want the hard guarantee).
     When off, the endpoint answers `403 {"error": "navigation is disabled
     in Marginalis settings"}` — honest, so the agent can tell the user why
     nothing happened.
   - Stretch, same page (backlog item, natural fit): `Display name` override
     for the user (currently derived from the OS username in
     `plugin/store/Authors.kt`).

## Behavior spec

- Resolve `file` project-relative across open projects (same rule as
  `comment_add` — see `resolveFile` in `MarginalisRestService`).
- `line` is 1-based on the wire, 0-based internally (boundary conversion in
  the transport, as everywhere).
- `anchor_text` optional but encouraged: verify/correct via
  `core.AnchorPolicy` exactly like `comment_add` — stale line + no match in
  the window → `409` "re-read the file", never a silent jump to the wrong
  place. Response: `{"navigated": true, "file", "line", "line_adjusted"}`.
- Navigation itself: `OpenFileDescriptor(project, vFile, line0, 0)
  .navigate(true)` on the EDT — the same call the tool window's `navigateTo`
  uses (`MarginalisToolWindowFactory`). Full navigation with focus is
  correct here BECAUSE every call is user-solicited; do not build a "polite
  scroll-only" mode speculatively.

## Settings implementation sketch

- `PersistentStateComponent` app-level service (navigation applies to the
  user, not one project) storing `navigationEnabled: Boolean = true`,
  `displayName: String? = null` if the stretch lands.
- `Configurable` registered via `com.intellij.applicationConfigurable` EP,
  simple form (a checkbox, maybe a text field).
- Transport consults the service; `Authors.user` consults `displayName`
  when present.

## Definition of done

- Endpoint + setting + skill update (command, etiquette rule, version bump).
- Core stays pure — nothing in this feature belongs in `core/` except the
  already-existing AnchorPolicy reuse.
- Verified live with the operator: agent navigates on request, setting
  toggles it off, 403 surfaces, anchor correction works on a stale line.
- Operator preferences: batch commits (no push until a reviewed milestone);
  review substantial diffs via a guided tour in the margin before commit.

## Why this feature is worth its size

It closes the original tool surface — after this, every capability sketched
on day one exists. And it establishes the consent pattern (etiquette rule +
hard off-switch) that every future capability with reach into the user's
attention should copy.
