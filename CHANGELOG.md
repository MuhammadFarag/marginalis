# Changelog

All notable changes to Marginalis. The format follows
[Keep a Changelog](https://keepachangelog.com); the build injects the
current version's section into the plugin's Marketplace change notes.
History before 0.1.19 lives in git tags.

## [Unreleased]

### Changed

- Marketplace listing description: onboarding an agent is now part of the
  pitch — the one-command skill install, and the served guide URL for
  agents without the skill.

## [0.1.25] - 2026-07-27

First stable-channel release (#7).

### Added

- Thread intents (#14): an optional `intent` on `comment_add` —
  `finding`, `guidance` or `question` — saying what kind of response a
  thread wants, with anything else answered by a teaching 400 naming the
  vocabulary. Omitted stays the common case. It is fully independent of
  severity (a `guidance` `blocker` is a legitimate thing to say) and
  purely semantic in this version: resolution works identically for all
  of them, and the served guide teaches what resolving each one means —
  a finding by fixing, guidance by being followed in the new code, a
  question by an answer. `comment_list` gains an `intent=` filter and
  returns `intent` on threads that have one, which makes the motivating
  query cheap: all the open guidance for the file you are about to edit.
  In the IDE each intent has its own gutter glyph — an eye, a bulb, a
  question mark, shapes rather than colors — plus a chip in the tool
  window rows and the thread panel, and its own lens in the tool
  window's filter.
- `comment_add_batch` (#10): a review round's notes in one call. Each
  item is a whole `comment_add` payload — the full anchor ladder, mixed
  freely — judged on its own, so one stale anchor fails its own item and
  the rest still land. The envelope's `author_name`, `author_id` and
  `project` are defaults for every item; results come back in request
  order, each the shape the single call would have sent or `{error}`.
  Only a malformed envelope is an HTTP error.
- `comment_reanchor_all {file}` (#11): rescue every orphan on a file at
  once, the way a whole-file rewrite creates them. The content search
  runs server-side and widens to the whole file — after a rewrite the
  old line numbers mean nothing and the anchor text means everything —
  and answers per thread: re-anchored at line N, or still orphaned
  because the content is genuinely gone. A file with no orphans returns
  an empty list, not an error.
- `comment_list` gains `updated_after` and returns `updated_at` per
  thread (#13): a cursor for "what moved since my last sweep", including
  threads the user resolved while the agent was away. It moves on
  messages, resolve, reopen and rescue — deliberately not on reading a
  thread or on an anchor drifting with an edit, which would make it
  useless as a cursor. Persisted; files written before this carry no
  timestamp and derive one from their newest message.
- Listed threads carry `anchor_text` — the anchor line as it stands now,
  so a caller can tell "adjusted to new content" from "still exactly
  what I wrote" without re-reading the file (#13).
- Project-level threads (#16): omit `file` as well as `line` on
  `comment_add` and the thread is about the workspace itself — the
  convention nobody wrote down, the decision still owed — with no path,
  no line, and nothing that can ever orphan. They lead the reading order
  everywhere: first in `comment_list`, and above the file nodes in a
  "Project" section of the tool window that appears only when such
  threads exist. Creating one never depends on that section: a toolbar
  action is always there, and the composer's split button now offers
  both widenings ("Comment on file instead" / "Comment on project
  instead"), carrying a draft's selection along as provenance. Since
  there is no file to resolve by, `comment_add` takes `project` whenever
  several are open, and answers the usual `open_projects` error when it
  can't tell; `line` without `file` is a teaching 400. The served guide
  states the anchor ladder once: selection → line → file → project.
- File-level threads (#12): omit `line` on `comment_add` and the thread
  is about the file itself — its shape, its name, the README it lacks —
  with no anchor to drift and nothing to re-find. A page glyph in the
  gutter beside line 1 marks a file that has them; clicking it unfolds
  the conversation above the first line, above all the code it is about.
  The glyph is display only: it never anchors anything, and these threads
  orphan only when the file itself disappears, reopening by themselves
  when the path comes back. They carry `severity`, `order`, and
  `walkthrough` like any thread (a file-level step opens the file at the
  top), and the tool window lists them under the file node above its line
  threads. On the wire the absence is the shape: responses and listings
  for a file-level thread carry no `line` or `line_adjusted` at all.
  `navigate` without `line` now opens a file at the top; `anchor_text`
  without `line`, and `comment_reanchor` on a file-level thread, are
  teaching 400s.
- "Comment on File" in the editor context menu (#15): the entry point
  that needs nothing to exist first, so a file with no threads at all can
  still be commented on as a whole. The tool window's file node offers
  the same action.
- The composer for a thread being started is now a split button: submit
  as begun, or take the dropdown's "Comment on file instead" to land the
  same words as a file-level thread. A draft that began from a selection
  keeps the selected words as provenance — what sparked the comment,
  recorded without pretending to anchor it.
- In-repo agent skill (`skills/marginalis/`), installable globally for
  70+ agents via `npx skills add MuhammadFarag/marginalis -g`: it
  teaches an agent to find the server, fetch the served agent guide,
  and follow it. No wrapper script — plain HTTP.

### Changed

- Malformed `comment_add` payloads now get teaching 400s throughout
  (#13): every rejection names the field, says what is wrong with it,
  and says what to do instead — the manner the severity vocabulary set,
  applied to types, empty bodies and misplaced anchors alike.
- `comment_list` returns threads in the tool window's reading order — by
  file (directory-tree), file-level threads first within each file, then
  down the lines — instead of creation order.
- A `segment` may now ride a file-level thread: provenance, not anchor.
  The guide's Spans and File-level sections both teach it — the quoted
  words are what sparked the thread; address them specifically even when
  the thread is about the whole file.
- Agent guide: message-body formatting is now its own "Message bodies"
  section (was a footnote under the API reference) — names everything
  that renders (emphasis, inline code, links, lists, headings, tagged
  fences) and what deliberately doesn't (tables, images, raw HTML), and
  tells agents to use markdown where structure helps.
- Agent guide: the API reference now documents responses, not just
  inputs — every endpoint's return shape in the table, a full
  `comment_list` example (`thread_id`, structured `author {kind, name,
  id}`, `seen_by`/`newly_seen`, optional thread fields), so agents
  script against real shapes instead of guessing (#9).
- Agent guide: the unread-sweep habit now passes `project=` — a bare
  sweep spans every project open in the IDE and consumes read receipts
  across all of them (#9).
- Skill: slimmed to a pure bootstrap — find the server, fetch the
  served guide before the first margin call, follow it as the sole
  authority. The duplicated habit summaries are gone, so the skill no
  longer needs to change when the contract does.

## [0.1.24] - 2026-07-25

### Added

- `GET /api/marginalis/agent_guide`: the plugin serves its own agent
  manual — the full contract (turn etiquette, identity and read
  receipts, anchoring rules, severity and walkthrough vocabulary, orphan
  rescue, API reference) as markdown, version-matched by construction
  because it ships inside the plugin. Teaching any agent Marginalis is
  now one instruction: ping, then fetch the guide. CI verifies the guide
  mentions every endpoint.

### Changed

- `ping`'s `version` now comes from a build-stamped resource instead of
  platform plugin-manager lookups (which are internal API).
- README's agent-integration section leads with the served guide.

## [0.1.23] - 2026-07-25

### Changed

- Markdown rendering now uses the IDE's bundled Markdown plugin instead of
  shipping a private copy of the parser library. New plugin dependency:
  `org.intellij.plugins.markdown` (bundled and enabled by default in all
  JetBrains IDEs).
- Marketplace listing description rewritten around what makes Marginalis
  itself: a turn-based margin conversation protocol.

### Fixed

- All Marketplace verifier findings: internal API usage
  (`PluginManagerCore.getPlugin`), scheduled-for-removal API
  (`SimpleListCellRenderer.create`), and deprecated API usages replaced
  with their public, current equivalents.

## [0.1.22] - 2026-07-25

### Added

- Collapsing reply composer: idle panels fold the reply box to a single
  prompt row; it expands on click or restored draft and folds after
  submitting. Panels open in reading mode — Esc and the walk shortcuts
  work without a click.
- Walk shortcuts inside the thread panel: the platform's next/previous
  occurrence shortcuts drive Previous/Next Step with focus anywhere in
  the panel.
- Resolve auto-advances through every walk — ordinary threads too, not
  just guided walkthroughs.
- Multi-line selections keep their quote: the span clamps to the
  selection's first line instead of degrading to a whole-line thread.

### Fixed

- Reopening a thread panel after an external file reload: stale inlay
  bookkeeping made navigation move the caret without opening the panel
  and swallowed the first gutter click.

## [0.1.21] - 2026-07-24

### Changed

- Severity vocabulary is strict: `blocker` and `nit` only — legacy
  `high`/`medium`/`low` now get a teaching rejection instead of silent
  aliasing.
- Domain rules moved into the pure core module: walk ordering and stable
  totals, directory-tree ordering, severity parsing, orphan rescue as a
  guarded transition, aggregate-state precedence, and the anchoring
  decision ladder — one tested home each, shared by every surface.

## [0.1.20] - 2026-07-24

### Fixed

- Submit shortcut is a registered shortcut (⌘⏎/Ctrl-Enter no longer
  collides with editor actions on some keymaps); a keyboard shortcut
  moved off the refactoring row.

## [0.1.19] - 2026-07-24

### Added

- Resolve folds the thread panel: only open threads hold editor real
  estate.

### Changed

- README and Marketplace description caught up with the product.
