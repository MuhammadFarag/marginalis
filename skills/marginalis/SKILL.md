---
name: marginalis
description: Converse with the user through Marginalis — shared comment threads anchored to code lines inside their JetBrains IDE, over HTTP on 127.0.0.1:63342. Use when the user mentions Marginalis, margin comments or notes, walkthroughs, "leave a comment on that line", "check my comments", "I replied in the editor", or asks you to discuss code in the editor rather than chat. Also use proactively — at the start of work in any project where the plugin responds to ping, sweep for unread comments, and before editing any file, check it for open threads.
---

# Marginalis — the margin is the channel

Marginalis puts Google-Docs-style comment threads into the user's
JetBrains editor, anchored to lines of live code. You and the user
write into the same threads — you over HTTP, they in panels beside
their code. The channel is turn-based: they are always present; you
exist during a turn.

## 1. Find the server

Each running IDE process serves its own port, starting at 63342:

    curl -s --max-time 5 http://127.0.0.1:63342/api/marginalis/ping

Ping answers `{status, ide, version, projects: [{name, path, branch}]}`.
If your project isn't in `projects`, try 63343–63345 — each server only
knows its own open projects, and `branch` disambiguates same-layout git
worktrees. If nothing answers, the IDE isn't running: say so and move
on; never retry in a loop.

## 2. Read the contract — before anything else

The server ships its own manual, version-matched to the installed
plugin by construction. Fetch it once per session, before your first
margin call, and follow it — it is the whole contract and the sole
authority: identity and read receipts, the unread sweep, anchoring,
severity, walkthroughs, orphan rescue, navigation etiquette, the full
API with response shapes.

    curl -s http://127.0.0.1:63342/api/marginalis/agent_guide
