# Marginalis

Code conversations with your AI coding agent, in the margins of your
JetBrains IDE.

Comment threads anchor to lines — or to the exact words you select — survive
edits and restarts, and resolve when their outcome lands in code. Your agent
reads and replies between turns over a local HTTP API; you read and reply in
the editor, where the code is. Google Docs comments over live code, built
for the human–agent pair.

## How it works

**You** press <kbd>⌥⇧M</kbd> on a line (or on a selection, to anchor to that
exact span) and write in the panel that unfolds. Threads show as gutter
icons; a tool window lists every thread as a directory tree with
first/prev/next/last walking. Resolving a thread means *its outcome is in
the code* — the marker drops and the thread moves to the session's Resolved
log.

**Your agent** talks to the IDE's built-in server on `127.0.0.1:63342` —
reading unread messages at the start of each turn, replying in-thread,
resolving what it completes. Messages carry per-agent read receipts, so
"the agent will see this" is a visible promise (your messages earn a green
✓ seen), and your message stays editable until it's actually been read.

## What's in the margin

- **Anchors that survive** — line numbers are hints, content is truth:
  threads re-find their text after edits and restarts, degrade gracefully
  (a reworded span becomes a line comment, not a loss), and orphans can be
  rescued by the agent (`comment_reanchor`).
- **Spans** — select the words, not just the line; the span stays tinted
  in the editor and the agent is told to heed it.
- **Severity** — agents mark review findings `blocker` ("act before this
  proceeds") or `nit` ("dismiss guilt-free"); the tool window filters to
  Blockers Only or Awaiting You, and bulk actions warn before resolving
  open blockers. A gate, not a weight: importance lives in prose.
- **Walkthroughs** — ordered steps across files ("look here 1st, 2nd, …")
  for reviewing a change; resolving a step auto-advances to the next.
- **Turn signals, not presence** — stripe badge and "N awaiting you" when
  the agent spoke last, a clickable balloon when a reply lands in a file
  you don't have on screen, and nothing that pulses.
- **A real composer** — markdown with structure highlighting, fenced code
  rendered through the IDE's own lexer and color scheme, quote-the-selection
  in one click, and drafts that survive closing the panel.
- **Multi-agent ready** — agents introduce themselves (`author_name`/
  `author_id`), get stable per-identity colors, and keep separate read
  receipts.

## Install

Grab the latest `marginalis-*.zip` from
[Releases](https://github.com/MuhammadFarag/marginalis/releases) →
Settings → Plugins → ⚙ → *Install Plugin from Disk*. Settings live under
*Tools → Marginalis* (display name, navigation consent, notifications,
walkthrough auto-advance, time format).

## Agent integration

Everything is plain JSON over the built-in server (port 63342; `ping`
reports the plugin version and open projects):

```
GET  ping · comment_list?file=&status=&unread_only=&project=
POST comment_add {file, line, body, anchor_text?, order?, walkthrough?, severity?, project?}
POST comment_reply {thread_id, body} · comment_resolve · comment_reopen
POST comment_reanchor {thread_id, line, anchor_text?}   (orphan rescue)
POST comment_resolve_all {file?} · comment_clear_all {file?}
POST navigate {file, line, anchor_text?, project?}      (consent-gated)
```

`line` is 1-based and treated as a hint — pass `anchor_text` (the line's
content) and the server verifies or searches nearby, answering 409 when
your picture of the file is stale. Errors are written to be acted on.
A Claude Code skill wrapping this API (sweep etiquette, walkthrough and
severity vocabulary, identity) ships alongside the plugin.

## Building from source

JDK 21+, no other setup:

```sh
./gradlew buildPlugin    # zip in build/distributions/
./gradlew runIde         # sandbox IDE with the plugin
```

Layout: `core/` is pure Kotlin — model, thread lifecycle, anchoring policy,
persistence codec, tested via `scripts/test-core.sh` — and the plugin module
is adapters around it: `transport/` (REST endpoints), `store/` (project
service pairing threads with live markers), `ui/` (panels, gutter,
tool window), `settings/`. CI verifies against the declared 2025.2 floor
with the Plugin Verifier; releases are tags (`v*`).
