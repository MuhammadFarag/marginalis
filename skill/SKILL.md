---
name: marginalis
description: Converse with the human through Marginalis — shared margin comment threads anchored to code lines inside their JetBrains IDE, driven over HTTP on 127.0.0.1:63342. Use whenever the user mentions Marginalis, margin comments/notes, "leave a comment on that line", "check my comments", "I replied in the editor", or asks you to discuss code in the editor rather than chat. Also use proactively: at the start of work in any project where the plugin responds to ping, sweep for unread human comments — the human may have left you notes in the margin — and before editing any file, check it for open threads.
---

# Marginalis — margin conversations with the human

The human's JetBrains IDE runs the Marginalis plugin: Google-Docs-style comment
threads anchored to lines of live code. You and the human both write into the
same threads — you over this HTTP API, the human in panels inside their editor.
Comments show who wrote them, anchors track edits automatically, threads
resolve when their outcome lands in code.

This is a **turn-based** channel, not live chat. The human is always present;
you exist only during a turn. Everything below follows from that asymmetry.

## Is the channel up? Which IDE owns it?

```sh
scripts/marginalis.sh ping        # → {"status":"ok", "ide": "...", "projects": [{name, path}, …]}
```

One try, 5s timeout. If it fails, the IDE isn't running — say so and move on;
do not retry in a loop. `ping` succeeding is what makes every habit below
mandatory.

Each running IDE process has its own server (first gets port 63342, the next
63343, …) and each only serves *its own* open projects. Check that ping's
`projects` includes the project you're working on; if it doesn't (or to skip
the guesswork entirely):

```sh
export MARGINALIS_URL=$(scripts/marginalis.sh discover)   # match cwd; or: discover <path>
```

`discover` probes 63342–63345 and prints the base URL of the server whose
project list contains the path. All other commands honor `MARGINALIS_URL`.

## The three habits

**1. Start every turn with the unread sweep.** The human leaves comments and
replies while you're away; they arrive marked unread:

```sh
scripts/marginalis.sh unread      # comment_list?unread_only=true
```

Reading marks messages seen (each carries `"newly_seen": true` once, and the
response totals them in `marked_seen`) — that's the read receipt the human
relies on. Address what you find: reply in-thread, not only in chat, so the
conversation stays anchored to the code it's about.

**2. Never edit a file that has open threads.** Before editing any file:

```sh
scripts/marginalis.sh open-on <project-relative-path>
```

If threads come back, consolidate first — drive each to resolution (its
conclusion becomes part of your edit, or reply why it needs no action and
resolve). This invariant is what gives RESOLVED its meaning: an open thread is
an unfinished conversation, and editing underneath one orphans the discussion.

**3. The resolver is the completer.** RESOLVED means "the outcome is in the
code (or explicitly moot)" — and the thread's gutter marker disappears at that
moment. So: when the human replies "do it" to your proposal, that is approval,
not completion — make the edit *first*, then `resolve`. If the human resolves
a thread themselves while action seems pending, ask rather than assuming it
was a work request. Resolve immediately only when no action is needed.

## Commands

`scripts/marginalis.sh` wraps the API (handles JSON escaping; raw endpoints
below if you need them):

| Command | Purpose |
|---|---|
| `ping` | is the channel up |
| `unread` | turn-start sweep (marks seen) |
| `list` | all threads, all projects |
| `open-on <file>` | open threads on one file (the edit guard) |
| `add <file> <line> <anchor_text> <body>` | start a thread on a line |
| `reply <thread_id> <body>` | reply in-thread |
| `resolve <thread_id>` | outcome is in the code / moot |
| `reopen <thread_id>` | resurface a resolved thread |
| `resolve-all [file]` | bulk resolve (all projects, or one file) — only after the outcomes genuinely all landed |
| `clear-all [file]` | delete threads AND the resolved log — destructive; only when the human asks |

Raw API: `http://127.0.0.1:63342/api/marginalis/<endpoint>` — GET `ping`,
`comment_list?file=&status=open|resolved|orphaned&unread_only=`; POST
`comment_add {file, line, body, anchor_text?}`, `comment_reply {thread_id,
body}`, `comment_resolve|comment_reopen {thread_id}`. Errors come back as
`{"error": "..."}` with 4xx status.

## Anchoring rules

- `file` is **project-relative** (against any project open in the IDE);
  `line` is **1-based**, exactly as you see lines when reading files.
- **Always pass `anchor_text`** — the exact text you believe occupies that
  line. Your line numbers go stale the moment the human types; the plugin
  verifies and searches ±20 lines, and the response tells you what happened
  (`"line_adjusted": true` means it corrected you).
- **409 CONFLICT means your picture of the file is stale.** The error says so:
  re-read the file, find your target line again, retry with fresh numbers.
  Never respond to a 409 by dropping `anchor_text` — that trades an honest
  failure for a comment silently pinned to the wrong line.

## What to use it for

Anchor observations to code where the human will see them in context: a
question about a design decision, a concern about a line you're not changing,
a proposal before you implement it. Prefer a margin thread over a chat message
whenever the thing you're saying is *about a specific line* — that's the
channel's whole reason to exist. Keep bodies plain text (no markdown
rendering yet), reasonably short, one topic per thread.

## Gotchas

- Threads persist across IDE restarts (`.idea/marginalis.json`, per project).
  On reopen, anchors are re-found by content near the last known line; if the
  code changed too much, the thread shows up as `orphaned` — deal with those,
  don't ignore them. Important *conclusions* still belong in code or commits.
- The human's replies arrive only when you look (habit 1). A human reply is
  guaranteed a response from you; make that promise true.
- `status=orphaned` threads lost their anchor line (code deleted). Read them,
  address them in-thread, resolve them — don't ignore them.
- If ping works but `comment_add` 404s on a file that exists, the server you
  reached doesn't have that project open — run `discover` and use the URL it
  prints. Within one IDE process, a project-relative path is resolved against
  every open project, first match wins; be wary in same-layout monorepos.
