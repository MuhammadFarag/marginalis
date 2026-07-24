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

## Shipped (2026-07-23)

- ✅ QoL trio (operator feedback) — Clear All now closes open editor
  panels (deleted thread → inlay disposed, not just refreshed); per-agent
  author-name colors (identity-hashed six-color palette, anonymous
  "Agent" keeps purple, user blue excluded); skill 0.9.1: `unread`/`list`
  scoped to the cwd project so sweeps stop burning read receipts in
  other projects, plus ranked `project_scope` containment (exact >
  cwd-inside-project > project-inside-cwd — nested projects had matched
  in ping order).
- ✅ Segment comments, human-side (v0.1.8, designed and built same day) —
  select + ⌃⌥M anchors to the span; agents only READ segments (asymmetry
  is the design). Built to the agreed sketch: quote selector (exact +
  within-line prefix/suffix, W3C TextQuoteSelector prior art) captured
  from the live selection; AnchorPolicy degradation ladder segment →
  line → ±window → orphan, 11 new core tests (36/36); tinted EXACT_RANGE
  highlighter; combined per-line gutter icon + chooser popup, with
  marker attachment consolidated into MarginalisMarkers (was duplicated
  across three sites); additive `segment` on comment_list rows,
  comment_add untouched; skill 0.10.0 "Heed the span". Within-line
  selections only — multi-line falls back to a line thread.

## Shipped (2026-07-24)

- ✅ UX round (v0.1.9, from the operator-perspective review) — turn signal:
  tool-window stripe badge + "N awaiting you" title when open threads have
  the agent's last word; unread gutter icons wear a badge dot (the
  BalloonInformation swap was too subtle); bare ⌃⌥M on a line with live
  threads opens the conversation (or the chooser) instead of drafting a
  duplicate — a selection still always drafts; Esc closes the panel and
  refocuses the editor; resolving a walkthrough step auto-advances to the
  next (setting, default on); "Quote code" composer link inserts the
  selection (or the thread's anchor) as a language-tagged fence; panel
  width follows the editor viewport instead of freezing at open; `ping`
  gains the plugin's own `version` (installed truth via PluginManagerCore
  — capability detection stops being absence-based), skill 0.10.1.
  Chooser popup extracted to ThreadChooserPopup (shared by gutter and
  ⌃⌥M).
- ✅ Rendered heading sizes (v0.1.10) — the render pane's HTML kit gains a
  stylesheet: h1 = 1.2× body, h2 = 1.1×, h3+ = body size, tight margins;
  bold carries the hierarchy. Closes the 07-19 "shouty headings" finding.

- ✅ Panel polish round (v0.1.14, designed in conversation) — the accent
  rail: panel's left edge names its kind (blocker red / nit gray / brand
  purple); per-message author rails in the existing author colors +
  consecutive agent messages group under one meta line (user messages
  always keep theirs — Edit and the seen-check live there); header
  de-redundified again ("Marginalis" title dropped, Resolve/Close are
  links now, step arrows kept per operator call); composer full-width
  with a right-aligned action row beneath and a two-line minimum; "✓
  seen" receipt on read user messages (tooltip names the agents) — the
  edit-window promise made visible. Declined for now: prose measure cap
  (operator call: no width cap).
- ✅ Grouped Resolved/Orphaned sections (v0.1.13, operator ask) — every
  status section now shares the directory tree; the flat lists and their
  per-row path repetition are gone, and file-node turn dots count only
  live conversations. Chronology traded away knowingly: real usage
  consults Resolved by file and clears it per session (operator finding —
  the section is a session record, not an archive; timestamps stay in
  the threads). Blocker counts never count resolved threads, so Resolved
  never alarms about gates already passed.
- ✅ Severity: blocker / nit (v0.1.11, designed and built same day) —
  the agent's ad-hoc "HIGH:/MEDIUM:/LOW:" body prefixes lifted into the
  channel, the walkthrough-order pattern again. Built to the agreed
  sketch: two ends + silent middle (`severity: blocker|nit` on
  comment_add, unmarked = ordinary; blocker = gate NOT importance —
  importance stays in prose, see decision log); legacy high/low/medium
  accepted on the wire, garbage gets a teaching 400; additive codec +
  comment_list field, 2 new core tests (38/38). Visuals per "one loud
  mark, one quiet mark, silence": red/grayed tree chips (word + color),
  nit rows grayed whole, section blocker counts, gutter error-badge
  precedence checkmark → orphan → blocker → unread, panel status word,
  stripe badge red when blockers open. Resolve All / Clear All warn on
  open blockers. "Blockers Only" funnel toggle — filter + step-walking
  = walk the blockers; empty state "No blockers". Skill 0.11.0:
  severity vocabulary (never in the body), call-path walk ordering.
  DECIDED: no severity-colored span tints.
- ✅ Panel severity badges (v0.1.12, walkthrough feedback same day, 2026-07-24) —
  the thread panel header wears a pill badge matching the gutter: red
  "blocker", quiet-gray "nit"; replaces the too-easy-to-miss status-line
  word. The panel was the one surface that only got the quiet treatment.

## Open — features

(none — the feature backlog is clear; composer fence-interior highlighting
remains parked under "Deferred with analysis")

## Shipped (2026-07-20)

- ✅ Agent self-identification (v0.1.5) — optional `author_name`/`author_id`
  on comment_add/comment_reply, anonymous default display "Agent"
  (no more hardcoded "Claude"); resolver stamped by caller (`faff649`)
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

- **JetBrains Marketplace publishing, operator side** — plugin-side wiring
  shipped in v0.1.3 (`a9c8cfc`); remaining: account, first manual upload
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

- **Multi-agent, remaining halves** — message addressing (@agent) and
  per-agent resolution authority. The read map shipped in v0.1.7
  (per-agent `seen_by` receipts); build the rest when concurrent agent
  sessions are routine.

## Decision log

- **Importance dimension declined** (2026-07-24): severity stays
  one-dimensional (blocker/nit + silent middle). Importance fails the
  vocabulary test — no distinct reader behavior attaches to it — the
  agent can't measure it honestly, a second chip breaks "one loud mark,
  one quiet mark, silence", and the underlying need is served by
  persistence (important non-blocking threads simply stay open; prose
  argues the weight). Revisit only if real usage produces the want —
  the comment_edit standard.

- **Resolve is an agent verb** (2026-07-19, real-usage finding) — OVERTURNED
  later the same day: the operator resolves tour stops directly while
  walking a review. Human-side resolve affordances stay as they are;
  the de-emphasis item is dropped.
- **Session presence dropped** (2026-07-19): operator call — the indicator
  doesn't make sense for a turn-based channel.
- **Cross-project walkthrough fragmentation accepted** (2026-07-20):
  rare in practice and harmless while walkthroughs don't interleave;
  the agent-side convention (one walkthrough per project) suffices —
  no UI work.
- **comment_edit dropped** (2026-07-20): symmetry without a use case — a
  full session of real use never produced the want. Agents compose in one
  pass and the human reads almost immediately, so the edit window would be
  seconds; corrective replies serve better and keep the record honest.
  Revisit only if real usage produces the need.
- **Review outcomes 2026-07-19** (via resolved margin threads, all landed in
  `65db25b`): comment-hygiene sweep; AuthorKind.HUMAN → USER with tolerant
  loader; Author + ThreadStatus as sealed ADTs; vendor email → m@far.ag;
  description → "between you and your coding agent".
