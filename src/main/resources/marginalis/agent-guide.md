# Marginalis — Agent Guide

You are reading the contract served by the installed plugin itself
(`GET /api/marginalis/agent_guide`), so it always matches the server
you are talking to. `ping` tells you the exact version.

Marginalis is margin conversation between you and the user: comment
threads anchored to lines of live code, rendered inside their JetBrains
IDE. The channel is **turn-based** — the user is always present; you
exist during a turn. You never type into their buffer; you leave notes,
replies, review findings, and guided walkthroughs in the margin, and
you read what they left you. Everything below follows from that
asymmetry.

## Discovery

`GET http://127.0.0.1:63342/api/marginalis/ping`

```json
{"status": "ok", "ide": "…", "version": "<plugin version>",
 "projects": [{"name": "…", "path": "…", "branch": "…"}]}
```

Each running IDE process serves its own port (first 63342, next 63343, …)
and only its own open projects. If ping fails, the IDE isn't running —
say so and move on; do not retry in a loop. If ping succeeds but your
project isn't in `projects`, probe the next port. `branch` disambiguates
same-layout git worktrees.

## Identity

Introduce yourself on every write and every read: `author_name` (display
name) and optionally a stable `author_id`. Read receipts are **per
agent**, keyed by `author_id` (falling back to name): listing marks
messages seen for YOUR identity only. Unidentified callers all share the
anonymous "Agent" identity — and consume each other's unread. When
several agent sessions share a margin, take distinct role-qualified
names ("Claude · design" / "Claude · impl") with distinct ids.

## The three habits

**1. Start every turn with the unread sweep.**
`GET comment_list?unread_only=true&project=…&author_id=…` — the user
leaves comments and replies while you're away, born unread. Always pass
`project`: a bare sweep spans every project open in the IDE and
consumes your read receipts on all of them. Reading marks messages
seen (`newly_seen` per message, `marked_seen` total): that receipt is
the promise the user relies on, so always read the bodies you consume,
and answer in-thread — a user reply is guaranteed a response. Every
thread carries `updated_at`; hand the newest one back as
`updated_after=` and a later sweep returns only what has moved —
including threads the user resolved while you were away.
Unread is what you haven't *seen*; your debt is what you haven't
*answered*. `comment_list?awaiting=agent&project=…` lists every open
thread where the user spoke last — reading doesn't shrink it, only a
reply (or a resolve) does. End the sweep with it empty.

**2. Never edit a file that has open threads.**
`GET comment_list?file=<path>&status=open` before editing. Open threads
are unfinished conversations; drive each to resolution first — its
conclusion becomes part of your edit, or reply why it needs no action
and resolve. Editing underneath an open thread orphans the discussion.

**3. The resolver is the completer.**
`RESOLVED` means "the outcome is in the code, or explicitly moot" — the
gutter marker disappears at that moment. A user reply of "do it" is
approval, not completion: make the edit first, then resolve. If the
user resolves a thread themselves while action seems pending, ask
rather than assuming. Resolve immediately only when no action is needed.

## Ending a turn: wait for the hand back

When the user has finished a round — read your replies, answered what
they wanted to — they **hand back**: a "Hand Back" button in the
Marginalis tool window, or "Submit & hand back" on the reply composer.
It is project-wide ("I've finished this round"), and it is how you learn
you are wanted.

End every turn the same way: leave your replies, then start the wait as
a **background command** and stop.

```
curl -s "http://127.0.0.1:63342/api/marginalis/comment_wait?project=…&since=<cursor>&author_name=…&author_id=…"
```

`since` is your cursor: the later of the newest `updated_at` and the
`handed_back_at` from the sweep or wake that started this turn — not
the timestamp of your own replies. A `comment_list?project=…` listing
carries the project's last `handed_back_at` in its envelope; a wake
carries the one that woke you. So a hand back the user made while you
were still working answers at once instead of being missed, and one you
have already answered never wakes you twice. The wait's **completion is
the signal**: `handed_back: true` means the user wants you now, and its
`awaiting` list (the `awaiting=agent` set, same thread shape as
`comment_list`) is your to-do list — answer each, then wait again.
`handed_back: false` is a timeout (default one hour) or a closing IDE:
the user stepped away. Do **not** re-arm; they will type when they are
back.

A hand back with nothing awaiting you ends the loop — stop waiting,
exactly as on a timeout. That is how the user closes a round: they
resolve the threads they are done with and hand back once more. The
user sees whether you are waiting — the hand-back gestures name you
while your wait is armed.

Any agent harness that re-invokes the agent when a background command
exits turns the click into your wake-up — in Claude Code, run the curl
with `run_in_background`. Every agent waiting on the project wakes on
the same hand back. Without an armed wait, nothing reaches you until the
user types; Claude Code users can close that gap with two hooks that put
the awaiting threads into every prompt — offer them the recipe at
https://github.com/MuhammadFarag/marginalis#if-you-use-claude-code.

## Anchoring

- The ladder, narrowest first: **selection → line → file → project**. You
  write the middle two and the top one — `line` for a place in the code,
  `file` alone for the file as a whole, neither for the project itself;
  selections are the user's gesture (see Spans). Always take the narrowest
  rung the subject actually occupies.
- `file` is project-relative; `line` is **1-based**, as you read files.
- Line numbers are hints; content is truth. **Always pass `anchor_text`**
  — the exact text you believe occupies the line. The server verifies,
  searches ±20 lines, and answers with `line_adjusted: true` when it
  corrected you.
- **409 means your picture of the file is stale**: re-read the file, find
  the target again, retry with fresh values. Never respond to a 409 by
  dropping `anchor_text` — that trades an honest failure for a comment
  silently pinned to the wrong line.

## Above the line: file and project threads

Omit `line` on `comment_add` and the thread is about the **file itself** —
its shape, its name, the README it lacks. Omit `file` as well and it is
about the **project**: the workspace as a whole, what you'd raise in a
standup rather than in a diff.

- Each omission drops what the narrower rung needed: `anchor_text` without
  `line` is a teaching 400, and so is `line` without `file`.
- A project-level `comment_add` has no path to resolve by, so pass
  `project` whenever more than one is open — otherwise it fails with
  `open_projects` for you to pick from.
- Responses and listings say only what the thread has: no `line`, and for
  project-level no `file` either. In `comment_list` project-level threads
  come first of all, then each file's file-level threads before its line
  threads.
- Neither drifts: a file-level thread orphans only when its file
  disappears (reopening on its own when the path comes back), a
  project-level one never orphans. `comment_reanchor` refuses both, and
  `navigate` still needs a `file`.
- `severity`, `order`, and `walkthrough` work unchanged; stepping to a
  file-level step opens the file at the top. Either may carry a `segment`
  as provenance — the user's selection that sparked it, not an anchor.

## Spans (read-only for you)

A thread may carry `segment {exact, prefix?, suffix?}`: the user
selected those exact words within the line. Their gesture was precise;
address the quoted span specifically, not the line in general. Above the
line the same field is provenance instead — the words that sparked a
comment about the whole file, or the whole project; read them as the
origin of the thought, not as its subject. Agents cannot create segments —
`comment_add` anchors to lines.

## Severity

`severity` on `comment_add` is a **gate, not a weight**: `blocker`
("act before this proceeds") or `nit` ("taste, dismiss guilt-free");
omit for everything in between, which is most comments. The vocabulary
is exactly those two words — anything else is rejected with a teaching
400; fix the word or drop the field, never retry with a synonym. Never
write the level into the body ("HIGH:", "Blocker:") — the UI carries it
everywhere it matters and the user can filter to blockers. Importance
is not severity; importance lives in your prose, argued with reasons.

## Intents

`intent` on `comment_add` says what kind of response the thread wants —
a **gate, not a weight**, exactly like severity and completely
independent of it. Three words, nothing else:

- `finding` — something here is wrong. It ends when the code is fixed.
- `guidance` — how the code around here should be written. It ends when
  the new code follows it, which is usually not the moment you read it.
- `question` — you genuinely want an answer. It ends when you get one.

Omit it for everything else, which is most threads: an ordinary comment
asks for nothing in particular, and marking everything makes the marks
meaningless. Anything outside the three words is a teaching 400 — fix
the word or drop the field, never retry with a synonym. Never write the
intent into the body ("Question:", "FINDING —"): the UI carries it, and
`comment_list?intent=` is how it is found.

Intent and severity compose freely, because they answer different
questions: a `guidance` `blocker` ("do not bring the rejected approach
back") is a normal and useful thing to say. Resolution works identically
for all of them — what differs is what resolving *means*, which is the
list above. Before editing a file, `comment_list?file=…&intent=guidance`
is the cheapest way to learn what its authors already decided.

## Message bodies

Bodies render as CommonMark — use it wherever structure helps:
emphasis, inline code, links (clickable, opened in the user's browser),
lists, and headings (rescaled to margin proportions). Fenced code
blocks display as read-only editor fragments with native syntax
highlighting — tag your fences with a language and prefer them to
prose-wrapped code. Deliberately outside the scope: tables, images,
and raw HTML degrade to plain text, so stay within the constructs
above.

## Walkthroughs

An ordered walk — "look here 1st, 2nd, …" — for reviewing your change,
explaining how code hangs together, or onboarding. Create steps with
`order` on `comment_add` (1, 2, …); an optional `walkthrough` label
("A", "B") keeps concurrent walkthroughs separate. Rules:

- A finished walk is one call: `comment_add_batch` takes the steps as
  items and answers per item, so a single stale anchor costs you that
  step and not the round.
- One topic per step, anchored on the line that best embodies it. The
  body never restates position, file path, or severity — the UI carries
  all three (steps render as "(2/5)" in a tree sorted in walking order).
- Order by the code's structure — entry point first, then callees —
  never by severity; severity has its own channel.
- A step can sit at any rung: a line, a file, or the project — a
  project-level step opens nothing, since there is no file to jump to.
- The user walks with next/previous controls and resolves steps as they
  go. A step resolved without a reply is seen-and-approved; a reply is a
  change request — land the change first, then resolve it.

## Orphans

`status: orphaned` means the anchored content disappeared. Orphans are
kept, not dropped — and you can rescue them: re-read the file, find
where the content lives now, `comment_reanchor {thread_id, line,
anchor_text}`. The thread reopens with a fresh verified anchor. Only
orphans may move (live anchors answer 409). When a sweep surfaces
orphans, rescue them before other work; if the content is truly gone,
reply saying so and resolve. When a whole file was rewritten and its
threads orphaned together, `comment_reanchor_all {file}` runs the same
search over all of them at once — widened to the whole file, since the
old line numbers mean nothing after a rewrite — and answers per thread:
re-anchored at line N, or still orphaned. No orphans there is an empty
list, not an error.

## Navigation

`navigate` opens the file in the user's editor with the caret on the
line — pointing without creating a thread. Omit `line` to open the file
at the top when the file, not a place in it, is what you mean. Use it
**only on explicit request** ("show me", "take me there"); never move
the user's caret uninvited. A 403 means they switched agent navigation
off in settings — tell them, don't retry.

## Multiple projects

A project-relative path resolves first-match across every open project —
and same-layout worktrees make that ambiguous by construction. Pass
`project` (name or root path) on anchored calls when more than one
project could match; resolution failures return `open_projects` (name,
path, branch) so you can pick and retry. Check each listed thread's
`project` field before trusting a same-named file.

## API reference

Base: `http://127.0.0.1:<port>/api/marginalis/` — errors are
`{"error": "…"}` with 4xx status, written to be acted on.

| Endpoint | Description → returns |
|---|---|
| `GET ping` | status, ide, plugin version, open projects with branches — full shape under Discovery |
| `GET agent_guide` | this document (markdown, not JSON) |
| `GET comment_list?file=&status=open\|resolved\|orphaned&intent=finding\|guidance\|question&awaiting=agent\|user&unread_only=&updated_after=&project=&author_name=&author_id=` | threads with messages; reading marks seen for the calling identity → `{threads: […], marked_seen, handed_back_at?}` — `handed_back_at` (the project's last hand back) only when the listing covered one project and it has one; example below |
| `POST comment_add {body, file?, line?, anchor_text?, order?, walkthrough?, severity?, intent?, project?, author_name?, author_id?}` | start a thread on a line → `{thread_id, file, line, line_adjusted, status}`; without `line`, on the file as a whole → `{thread_id, file, status}`; without `file` either, on the project (pass `project` when several are open) → `{thread_id, status}` |
| `POST comment_add_batch {items: [comment_add payloads], author_name?, author_id?, project?}` | many notes in one call; the envelope's identity and `project` are per-item defaults → `{results: [ …success shape… \| {error} ], created}` in request order, 200 unless the envelope itself is malformed |
| `POST comment_reply {thread_id, body, author_name?, author_id?}` | reply in-thread → `{message_id, thread_id, status}` |
| `POST comment_resolve {thread_id, author_name?, author_id?}` | outcome landed / moot → `{thread_id, status}` |
| `POST comment_reopen {thread_id}` | resurface a resolved thread → `{thread_id, status}` |
| `POST comment_reanchor {thread_id, line, anchor_text?}` | orphan rescue, line threads only (file-level → 400) → `{thread_id, line, status}` |
| `POST comment_reanchor_all {file, project?}` | rescue every orphan on one file, searching the whole file by content → `{file, results: [{thread_id, line?, status}], rescued}` |
| `POST comment_resolve_all {file?, author_name?, author_id?}` | bulk resolve — only when the outcomes genuinely all landed → `{resolved: <count>}` |
| `POST comment_clear_all {file?}` | DELETE threads and the resolved log — destructive; only on explicit user request, and sweep unread first → `{cleared: <count>}` |
| `GET comment_wait?project=&since=&timeout=&author_name=&author_id=` | hold until the user hands back — at once if they already did after `since` (ISO-8601, exclusive; omit to wait for the next one) — or until `timeout` seconds pass (default 3600, capped at 14400) → `{handed_back: true, handed_back_at, awaiting: [threads]}` or `{handed_back: false}`; `awaiting` marks seen like `comment_list`; `project` is required when several are open; your `since` is the later of the newest `updated_at` and `handed_back_at` you have seen |
| `POST navigate {file, line?, anchor_text?, project?}` | consent-gated pointing → `{navigated, file, line, line_adjusted}`; without `line`, opens the file at the top → `{navigated, file}` |

A `comment_list` thread, in full:

```json
{"threads": [{
  "thread_id": "…", "project": "…", "file": "src/…", "line": 12,
  "anchor_text": "    val x = compute()",
  "status": "open", "intent": "guidance",
  "created_at": "2026-07-27T18:03:11Z",
  "updated_at": "2026-07-27T18:41:02Z",
  "messages": [{
    "message_id": "…",
    "author": {"kind": "user", "name": "…"},
    "body": "…", "created_at": "…",
    "seen_by": ["claude-main"], "newly_seen": true
  }]
}], "marked_seen": 1}
```

Field notes: `anchor_text` is the anchor line **as it stands now** —
compare it with the text you anchored to and you know whether the code
moved under the thread, without re-reading the file. (Live from the
open document; for a file no editor has loaded it is the stored
fingerprint — the most the server honestly knows without forcing the
file into memory.) `updated_at` moves
when the conversation does (a message, a resolve, a reopen, a rescue) and
not when it is merely read or its line drifts, which is what makes it a
usable cursor for `updated_after`. `author` is always an object — `kind`
is `agent` or `user`, and agent authors carry `id`. Thread fields `segment`, `order`,
`walkthrough`, `severity`, `intent`, and `resolved_by` appear only when
set.
`awaiting` narrows to open threads whose last message is the other
party's: `agent` — the user spoke last, you owe the reply; `user` — an
agent spoke last, the user owes one. It composes with every other
filter, and listing still marks seen: awaiting is about *answered*, not
*read*. Any other value is a teaching 400.
`newly_seen` marks messages this very listing consumed for your
identity; `seen_by` lists the identities that have read the message.
Timestamps are ISO-8601 UTC; `line` in every response is 1-based and
current (already re-anchored), not necessarily where the thread began —
and absent entirely above the line, as `file` is on a project-level
thread. Threads arrive in reading order: the project's own first, then by
file, file-level before line threads, then down the lines.

## Persistence

Threads live in `.idea/marginalis.json` per project, survive IDE
restarts, and re-anchor by content on reopen. The margin is part of the
workspace: prefer a thread over chat whenever what you're saying is
about a specific line — that is this channel's reason to exist. Keep
bodies short, one topic per thread, and put lasting conclusions in code
and commits, not only in the margin.
