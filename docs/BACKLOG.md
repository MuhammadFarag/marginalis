# Marginalis — idea backlog

Captured from operator feedback during use; not yet scheduled. Move items to
GitHub issues once the token has Issues scope.

## Tool window

- **Guided ordering / numbering** (2026-07-19, during first code review via
  Marginalis): when the agent proposes a reading or review order, the tool
  window should be able to show that numbering so it can guide the human
  through "where to look next". Implies threads (or files) can carry an
  agent-assigned sequence, rendered as `1.`, `2.`, … in the tree — margin
  threads as a *tour*, not just a pile. Probably an optional field on
  comment_add (e.g. `order`) plus sort + prefix in the tree renderer.

- **Default anchor: left** (2026-07-19): the tool window opens on the right;
  most file-navigation surfaces in IntelliJ live on the left, and this is a
  navigation surface. One-word change (`anchor="left"` in plugin.xml) — but
  decide consciously: left competes with the Project view stripe.

- **Native IDE file icons** (2026-07-19): the directory tree uses a generic
  file icon (`AllIcons.FileTypes.Any_type`) instead of per-type IDE icons
  (Python, Kotlin, XML…). Definitely possible: resolve the VirtualFile and
  use its file-type icon (e.g. `FileTypeManager` / `IconUtil.getIcon`) —
  directories likewise (`AllIcons.Nodes.Folder` is fine). Small change,
  meaningful familiarity win.

## Thread panel / messages

- **Edit own unseen messages** (2026-07-19): no way to edit a comment after
  sending, before the agent reads it. The read receipt is the natural edit
  window: editable while `seenByAgent == false`, immutable record after.
  Panel pencil affordance + store mutation; agent-side `comment_edit` later
  for symmetry.

- **Markdown rendering, markdown-lite scope** (2026-07-19): bold/italic/
  inline code/fences/links/lists. Parse with org.jetbrains:markdown; render
  via JBHtmlPane (NOT raw JBLabel html — see the 7a62f64 truncation lessons;
  reuse measure-at-width). ~half day; risk = panel height measurement +
  dark-theme styles. Tool-window previews should strip formatting.

- **Syntax-highlighted code fences via native editor fragments** (2026-07-19):
  render fenced blocks as read-only EditorTextField with
  EditorHighlighterFactory + language from the fence tag — real IDE lexer +
  user color scheme, no Markdown-plugin/JCEF dependency. ~+half day on top
  of markdown-lite.

## Identity & protocol

- **Agent self-identification** (2026-07-19): "Claude" is hardcoded as the
  agent displayName (Model.kt), but any agent can drive the protocol. Default
  the agent author to "Agent"; let posts carry identity — optional
  `author_name` (maybe `author_id`) on comment_add/comment_reply, echoed in
  comment_list and rendered in panels/attribution. Pairs with making the
  human name configurable (currently derived from the OS username).

## Thread panel / messages (cont.)

- **"Send" → "Submit"** (2026-07-19, operator wording note): nothing is
  "sent" anywhere — the message lands in a local store awaiting the agent's
  next read. "Submit" is honest. Trivial; batch with other small UI polish
  (anchor-left, native file icons) in one polish pass.

## Open product questions

- **What is "resolved" actually for?** (2026-07-19): a full review/idea
  session happened without a single resolve. The concept was designed for
  the discussing→editing transition (§3.1: resolve = outcome consolidated
  into code, gates edits). In conversational/idea-capture use, threads are
  answered and simply left. Either (a) that's fine — resolve only matters
  when edits are pending, unresolved threads on untouched files are cheap;
  or (b) the lifecycle needs a lighter terminal state ("done reading",
  auto-archive on inactivity?). Watch real usage before mechanizing; §1.4.
  - Real-usage data (2026-07-19, operator's separate project): resolve is
    used exclusively by the AGENT, at the discussion→editing transition —
    the resolver-is-the-completer etiquette makes that the natural shape,
    since the agent performs the edits. Human-side resolve (panel button,
    Resolve All) has gone unused. Conclusion: reading (a) confirmed; resolve
    is an agent-side consolidation op in practice. Consider de-emphasizing
    human-side resolve affordances rather than adding lifecycle states.

- **Icon for "Add Marginalis Comment" context-menu action** (2026-07-19):
  action shows text-only in the editor right-click menu. Give the AnAction
  an icon (balloon, matching the gutter family) via the action registration.

- **Multi-agent design note** (2026-07-19, review thread on Model.kt): with
  N agents, seenByAgent (single bit) must become a per-participant read map;
  then message addressing (@agent) and per-agent resolution authority.
  Nothing to build yet — but the Author/status ADT refactor should keep the
  door open (Agent variant carries identity).

## Decided (review session 2026-07-19 — outcomes of resolved margin threads)

- **Comment hygiene sweep** (approved): remove all planning-doc/§ references
  from code comments — each becomes self-contained rationale or is deleted;
  drop comments restating the obvious; move marginalis-handover.md to docs/
  as historical record.
- **AuthorKind.HUMAN → USER** (approved): rename with tolerant persistence
  loader (accept legacy "HUMAN" in .idea/marginalis.json) + wire-format and
  skill-doc updates.
- **Author and ThreadStatus become ADTs** (approved, scheduled with core/
  extraction): sealed Author { User(name); Agent(name, id?) }; sealed
  ThreadStatus { Open; Resolved(by); Orphaned } — moves resolvedBy into the
  state, enables agent self-identification, makes illegal states
  unrepresentable. Done once, inside the hexagonal core/ module extraction.
- **plugin.xml fixes applied directly** (this session): vendor email →
  m@far.ag; description → "between you and your coding agent".
- **Multi-agent questions**: recorded above; explicitly deferred.

- **Fence-interior highlighting while typing** (2026-07-19, nice-to-have):
  the markdown composer is lexer-only, so fence delimiters color but code
  inside fences doesn't until rendered. Real fix = custom layered
  highlighter (markdown lexer + per-fence delegation to the language's
  SyntaxHighlighter), ~half day of custom lexer code; daemon/injection on a
  text field is worse. Deferred — rendered messages already show fences
  fully highlighted, so typing-time interior color is cosmetic.
