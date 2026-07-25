# Marginalis — Agent Guide

You are reading the contract served by the installed plugin itself
(`GET /api/marginalis/agent_guide`), so it always matches the server
you are talking to. `ping` tells you the exact version.

Marginalis is margin conversation between you and the human: comment
threads anchored to lines of live code, rendered inside their JetBrains
IDE. The channel is **turn-based** — the human is always present; you
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
`GET comment_list?unread_only=true&author_id=…` — the human leaves
comments and replies while you're away, born unread. Reading marks them
seen (`newly_seen` per message, `marked_seen` total): that receipt is
the promise the human relies on, so always read the bodies you consume,
and answer in-thread — a human reply is guaranteed a response.

**2. Never edit a file that has open threads.**
`GET comment_list?file=<path>&status=open` before editing. Open threads
are unfinished conversations; drive each to resolution first — its
conclusion becomes part of your edit, or reply why it needs no action
and resolve. Editing underneath an open thread orphans the discussion.

**3. The resolver is the completer.**
`RESOLVED` means "the outcome is in the code, or explicitly moot" — the
gutter marker disappears at that moment. A human reply of "do it" is
approval, not completion: make the edit first, then resolve. If the
human resolves a thread themselves while action seems pending, ask
rather than assuming. Resolve immediately only when no action is needed.

## Anchoring

- `file` is project-relative; `line` is **1-based**, as you read files.
- Line numbers are hints; content is truth. **Always pass `anchor_text`**
  — the exact text you believe occupies the line. The server verifies,
  searches ±20 lines, and answers with `line_adjusted: true` when it
  corrected you.
- **409 means your picture of the file is stale**: re-read the file, find
  the target again, retry with fresh values. Never respond to a 409 by
  dropping `anchor_text` — that trades an honest failure for a comment
  silently pinned to the wrong line.

## Spans (read-only for you)

A thread may carry `segment {exact, prefix?, suffix?}`: the human
selected those exact words within the line. Their gesture was precise;
address the quoted span specifically, not the line in general. Agents
cannot create segments — `comment_add` anchors to lines.

## Severity

`severity` on `comment_add` is a **gate, not a weight**: `blocker`
("act before this proceeds") or `nit` ("taste, dismiss guilt-free");
omit for everything in between, which is most comments. The vocabulary
is exactly those two words — anything else is rejected with a teaching
400; fix the word or drop the field, never retry with a synonym. Never
write the level into the body ("HIGH:", "Blocker:") — the UI carries it
everywhere it matters and the human can filter to blockers. Importance
is not severity; importance lives in your prose, argued with reasons.

## Walkthroughs

An ordered walk — "look here 1st, 2nd, …" — for reviewing your change,
explaining how code hangs together, or onboarding. Create steps with
`order` on `comment_add` (1, 2, …); an optional `walkthrough` label
("A", "B") keeps concurrent walkthroughs separate. Rules:

- One topic per step, anchored on the line that best embodies it. The
  body never restates position, file path, or severity — the UI carries
  all three (steps render as "(2/5)" in a tree sorted in walking order).
- Order by the code's structure — entry point first, then callees —
  never by severity; severity has its own channel.
- The human walks with next/previous controls and resolves steps as they
  go. A step resolved without a reply is seen-and-approved; a reply is a
  change request — land the change first, then resolve it.

## Orphans

`status: orphaned` means the anchored content disappeared. Orphans are
kept, not dropped — and you can rescue them: re-read the file, find
where the content lives now, `comment_reanchor {thread_id, line,
anchor_text}`. The thread reopens with a fresh verified anchor. Only
orphans may move (live anchors answer 409). When a sweep surfaces
orphans, rescue them before other work; if the content is truly gone,
reply saying so and resolve.

## Navigation

`navigate` opens the file in the human's editor with the caret on the
line — pointing without creating a thread. Use it **only on explicit
request** ("show me", "take me there"); never move the human's caret
uninvited. A 403 means they switched agent navigation off in settings —
tell them, don't retry.

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

| Endpoint | Description |
|---|---|
| `GET ping` | status, ide, plugin version, open projects with branches |
| `GET agent_guide` | this document |
| `GET comment_list?file=&status=open\|resolved\|orphaned&unread_only=&project=&author_name=&author_id=` | threads with messages; reading marks seen for the calling identity |
| `POST comment_add {file, line, body, anchor_text?, order?, walkthrough?, severity?, project?, author_name?, author_id?}` | start a thread (line-anchored) |
| `POST comment_reply {thread_id, body, author_name?, author_id?}` | reply in-thread |
| `POST comment_resolve {thread_id, author_name?, author_id?}` | outcome landed / moot |
| `POST comment_reopen {thread_id}` | resurface a resolved thread |
| `POST comment_reanchor {thread_id, line, anchor_text?}` | orphan rescue |
| `POST comment_resolve_all {file?, author_name?, author_id?}` | bulk resolve — only when the outcomes genuinely all landed |
| `POST comment_clear_all {file?}` | DELETE threads and the resolved log — destructive; only on explicit human request, and sweep unread first |
| `POST navigate {file, line, anchor_text?, project?}` | consent-gated pointing |

Message bodies render as CommonMark: emphasis, lists, and fenced code
blocks displayed as natively syntax-highlighted editor fragments — tag
your fences with a language and prefer them to prose-wrapped code.

## Persistence

Threads live in `.idea/marginalis.json` per project, survive IDE
restarts, and re-anchor by content on reopen. The margin is part of the
workspace: prefer a thread over chat whenever what you're saying is
about a specific line — that is this channel's reason to exist. Keep
bodies short, one topic per thread, and put lasting conclusions in code
and commits, not only in the margin.
