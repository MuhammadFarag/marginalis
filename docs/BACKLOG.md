# Marginalis — roadmap & backlog

Captured from operator feedback during real use. Move items to GitHub issues
once the token gains Issues scope.

## Shipped (2026-07-19)

- ✅ Guided tours — `comment_add order`/`tour`, compact `(A1/4)` prefixes,
  directory hierarchy in Guided sections, walking-order sort
- ✅ Tool window: anchor left, native per-filetype icons
- ✅ "Send" → "Submit"; context-menu action icon
- ✅ Edit-before-seen — composer-based editing; read receipt = edit boundary
- ✅ Markdown-lite rendering + fenced code as native highlighted editor
  fragments; markdown-aware composer (structure highlighting while typing)
- ✅ `core/` extraction — pure-Kotlin domain module, Author/ThreadStatus
  ADTs, USER rename (legacy-tolerant codec), AnchorPolicy deduplicated,
  22-test suite, `scripts/test-core.sh` local loop, comment-hygiene sweep,
  handover doc → docs/
- ✅ `navigate` endpoint + first settings surface — consent pattern
  (skill etiquette + hard off-switch, 403 when off), display-name override,
  anchor verification shared with comment_add (`940b096`)
- ✅ Tour walking — stable `(n/total)` denominators, first/prev/next/last in
  tool window title (⌘⌥↑/⌘⌥↓ free via occurrence actions) and thread-panel
  header, TourNavigator shared walk, panel title de-redundified (`db11943`)

## Open — features

- **Agent self-identification** — model ready (`Author.Agent(name, id)`);
  transport still stamps "Claude": accept optional `author_name`/`author_id`
  on comment_add/comment_reply, default the display to "Agent".
- **Cross-project walkthroughs fragment** (observed 2026-07-20): steps in
  different projects render in separate tool windows, each fragment
  numbered against its own project's total — a 4-step walkthrough spanning
  two projects shows as (1/2)(2/2) + (3/4)(4/4), both looking
  self-contained. Options: per-window "steps continue in project X" note,
  or an agent-side convention (one walkthrough per project). Needs design.
- **Rendered heading sizes** — h1/h2 render at document scale inside margin
  panels; likely want scaling down. (Operator thread open in sample-project.)

## Shipped (2026-07-20)

- ✅ Marketplace publishing wiring + walkthrough vocabulary (v0.1.3) —
  signing/publishPlugin off CI secrets (workflow skips gracefully until
  they exist), refreshed listing description; tour → walkthrough rename
  across model (legacy-tolerant codec), wire, UI (Step actions), skill
  0.5.0 incl. new Walkthroughs practice section (`a9c8cfc`)
- ✅ Worktree-safe anchoring (v0.1.4) — first externally-reported bug:
  same-layout worktrees made first-match resolution ambiguous. Optional
  `project` on anchored endpoints, git branch in ping + teaching 404s
  (`.git/HEAD` read, platform-only), per-thread `project` in listings,
  script auto-scopes by cwd; skill 0.6.0. CONFIRMED fixed in the
  reporting PyCharm worktree setup (`ae98560`)
- ✅ Hot reload — clean dynamic unload (`MarginalisUnloadListener` strips
  markup highlighters + editor inlays/user-data; all EPs verified
  `dynamic="true"`). Verified live: uninstall → install-from-disk cycles
  without IDE restart, threads survive via persistence + rehydration.
  Platform finding: one-step install-over-existing ALWAYS restarts
  (`PluginInstaller.installFromDisk` hard-codes it); the two-step flow is
  the reload loop — documented in CLAUDE.md.

## Open — infrastructure

- **JetBrains Marketplace publishing** — plugin-side wiring ~1hr (signing +
  publishPlugin + beta channel); operator side: account, first manual upload
  (creates the listing, ~2-business-day review), API token, four CI secrets.
- **Issues migration** — grant the PAT Issues scope, move this file there.
- **Skill source into repo** (deferred 2026-07-20, operator call) — move
  SKILL.md + marginalis.sh into an inert `skill/` dir here, one-way
  sync script → mfarag-plugins (plugin.json stays operator-owned);
  optional later: `.claude-plugin/marketplace.json` makes the repo itself
  an installable Claude Code marketplace. Design agreed, build skipped
  for now.
- **Skill trigger evals** — only if the `marginalis` skill under-triggers in
  other sessions (skill-creator's optimization loop is ready when needed).

## Deferred with analysis

- **Fence-interior highlighting while typing** — the composer is lexer-only;
  the real fix is a custom layered highlighter (markdown lexer delegating
  fence regions to each language's SyntaxHighlighter), ~half a day of
  bespoke lexer code; daemon/injection on a text field is worse. Rendered
  messages already show fences fully highlighted, so typing-time interior
  color is cosmetic.
- **Project-view tree badges** — ranked least useful of the visibility trio
  (tool window and tab glyphs cover the need).

## Future era

- **Multi-agent** — `seenByAgent` (single bit) must become a per-participant
  read map; then message addressing (@agent) and per-agent resolution
  authority. The Author ADT keeps the door open; nothing to build until a
  second agent is real.

## Decision log

- **Resolve is an agent verb** (2026-07-19, real-usage finding) — OVERTURNED
  later the same day: the operator resolves tour stops directly while
  walking a review. Human-side resolve affordances stay as they are;
  the de-emphasis item is dropped.
- **Session presence dropped** (2026-07-19): operator call — the indicator
  doesn't make sense for a turn-based channel.
- **comment_edit dropped** (2026-07-20): symmetry without a use case — a
  full session of real use never produced the want. Agents compose in one
  pass and the human reads almost immediately, so the edit window would be
  seconds; corrective replies serve better and keep the record honest.
  Revisit only if real usage produces the need.
- **Review outcomes 2026-07-19** (via resolved margin threads, all landed in
  `65db25b`): comment-hygiene sweep; AuthorKind.HUMAN → USER with tolerant
  loader; Author + ThreadStatus as sealed ADTs; vendor email → m@far.ag;
  description → "between you and your coding agent".
