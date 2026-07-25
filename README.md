# Marginalis

Code conversations with your AI coding agent, in the margins of your
JetBrains IDE.

Comment threads anchor to lines — or to the exact words you select. They
survive edits and restarts, and they resolve when their outcome lands in
code. You write in the editor, where the code is. Your agent reads and
replies between turns over a local HTTP API. Google Docs comments over
live code, built for you and your agent.

![A margin thread on a line of code: two agent roles debate a design
question in distinct colors, the user decides, and the agent locks the
decision in](docs/images/margin-conversation.png)

## How it works

Press <kbd>⌥⇧M</kbd> on a line, or on a selection, and write in the panel
that unfolds. Threads appear as gutter icons. A tool window lists every
thread as a directory tree with step-by-step walking. Resolving a thread
means its outcome is in the code: the marker drops and the thread moves
to the Resolved log.

Your agent talks to the IDE's built-in server on `127.0.0.1:63342`. It
reads unread messages at the start of each turn, replies in-thread, and
resolves what it completes. Messages carry per-agent read receipts, so
"the agent will see this" is a visible promise. Your message stays
editable until an agent has read it.

## In the margin

- **Anchors that survive.** Line numbers are hints; content is truth.
  Threads re-find their text after edits and restarts. A reworded span
  becomes a line comment instead of a loss, and agents can rescue
  orphaned threads.
- **Spans.** Select the words, not just the line. The span stays tinted
  in the editor, and the agent is told to address it specifically.
- **Severity.** Agents mark review findings `blocker` ("act before this
  proceeds") or `nit` ("dismiss guilt-free"). The tool window filters to
  Blockers Only or Awaiting You. A gate, not a weight: importance lives
  in prose.
- **Walkthroughs.** Ordered steps across files for reviewing a change.
  Resolving a step advances to the next.
- **Turn signals, not presence.** A stripe badge and "N awaiting you"
  when the agent spoke last. A clickable balloon when a reply lands in a
  file that is not on screen. Nothing pulses.
- **A real composer.** Markdown with structure highlighting, code fences
  rendered through the IDE's own color scheme, quote-the-selection in one
  click, and drafts that survive closing the panel.
- **Multiple agents.** Agents introduce themselves, get stable
  per-identity colors, and keep separate read receipts.

## Install

Download the latest `marginalis-*.zip` from
[Releases](https://github.com/MuhammadFarag/marginalis/releases), then
Settings → Plugins → ⚙ → *Install Plugin from Disk*. Settings live under
*Tools → Marginalis*.

## Agent integration

The plugin serves its own agent manual, so the documentation always
matches the installed version. Teaching an agent Marginalis takes one
instruction:

```
Ping http://127.0.0.1:63342/api/marginalis/ping — if Marginalis answers,
GET /api/marginalis/agent_guide and follow it.
```

The guide covers turn etiquette, identity and read receipts, anchoring
rules, severity and walkthrough vocabulary, orphan rescue, and the full
API reference. CI checks that it mentions every endpoint. The API itself
is plain JSON over the built-in server:

```
GET  ping · agent_guide · comment_list?file=&status=&unread_only=&project=
POST comment_add {file, line, body, anchor_text?, order?, walkthrough?, severity?, project?}
POST comment_reply {thread_id, body} · comment_resolve · comment_reopen
POST comment_reanchor {thread_id, line, anchor_text?}   (orphan rescue)
POST comment_resolve_all {file?} · comment_clear_all {file?}
POST navigate {file, line, anchor_text?, project?}      (consent-gated)
```

`line` is 1-based and treated as a hint. Pass `anchor_text` (the line's
content) and the server verifies it or searches nearby, answering 409
when your picture of the file is stale. Errors are written to be acted
on.

### Give your agent the skill

```sh
npx skills add MuhammadFarag/marginalis -g -y
```

This installs the Marginalis skill (`skills/marginalis/`) for Claude
Code, Cursor, and [70+ other agents](https://skills.sh). `-g` installs
globally, so the skill is available in every project rather than only
the current one. `-y` accepts the prompts, so the command runs
unattended. The skill teaches an agent to find the server, fetch the
guide, and follow it. Plain HTTP; no wrapper scripts.

## Building from source

JDK 21+, no other setup:

```sh
./gradlew buildPlugin    # zip in build/distributions/
./gradlew runIde         # sandbox IDE with the plugin
```

`core/` is pure Kotlin: the model, thread lifecycle, anchoring policy,
and persistence codec, tested via `scripts/test-core.sh`. The plugin
module is adapters around it: `transport/`, `store/`, `ui/`, `settings/`.
CI verifies against the declared 2025.2 floor with the Plugin Verifier.
Releases are tags (`v*`).
