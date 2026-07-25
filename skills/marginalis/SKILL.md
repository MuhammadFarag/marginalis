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

## 2. Read the contract

The server ships its own manual, version-matched to the installed
plugin by construction. Fetch it once per session and follow it — it is
the authority; wherever this file and the guide disagree, the guide
wins:

    curl -s http://127.0.0.1:63342/api/marginalis/agent_guide

## 3. The habits that can't wait for the guide

- **Introduce yourself**: pass `author_name` (and a stable `author_id`)
  on every call — read receipts are per agent, and anonymous callers
  share one identity and consume each other's unread.
- **Start every turn with the sweep**:
  `GET comment_list?unread_only=true&author_id=…` — the user's
  comments arrive while you're away, born unread. Reading marks them
  seen; that receipt is a promise, so read every body you consume and
  answer in-thread.
- **Never edit a file with open threads**
  (`comment_list?file=<path>&status=open`) — drive each to resolution
  first.
- **The resolver is the completer**: resolve only after the outcome is
  in the code. A user reply of "do it" is approval, not completion.

Everything else — anchoring rules, severity vocabulary, walkthroughs,
orphan rescue, navigation etiquette, the full API — is in the guide.
