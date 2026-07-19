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

## Open — features

- **Agent self-identification** — model ready (`Author.Agent(name, id)`);
  transport still stamps "Claude": accept optional `author_name`/`author_id`
  on comment_add/comment_reply, default the display to "Agent".
- **`navigate(file, line)` endpoint** — the last piece of the original tool
  surface: agent moves the user's caret without leaving a thread.
- **Session-presence indicator** — "is an agent attached right now": small,
  persistent, honest. Requires deciding what presence means over a stateless
  HTTP API (recent-activity window? explicit session start/end calls?).
- **`comment_edit` (agent-side)** — symmetry with edit-before-seen.
- **User display-name setting** — currently derived from the OS username.
- **De-emphasize human-side resolve affordances** — real usage shows resolve
  is an agent verb (the resolver is the completer, and the agent performs
  the edits); the panel button and Resolve All earn their place only for
  moot threads and bulk cleanup.
- **Rendered heading sizes** — h1/h2 render at document scale inside margin
  panels; likely want scaling down. (Operator thread open in sample-project.)

## Open — infrastructure

- **JetBrains Marketplace publishing** — plugin-side wiring ~1hr (signing +
  publishPlugin + beta channel); operator side: account, first manual upload
  (creates the listing, ~2-business-day review), API token, four CI secrets.
- **Hot reload** — dynamic-EP audit + clean unload pass so plugin updates
  stop requiring an IDE restart (today: restart required, empirically).
- **Issues migration** — grant the PAT Issues scope, move this file there.
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

- **Resolve is an agent verb** (2026-07-19, real-usage finding): the human
  never resolves; the agent resolves at the discussion→editing transition,
  as the resolver-is-the-completer etiquette implies. No new lifecycle
  states needed.
- **Review outcomes 2026-07-19** (via resolved margin threads, all landed in
  `65db25b`): comment-hygiene sweep; AuthorKind.HUMAN → USER with tolerant
  loader; Author + ThreadStatus as sealed ADTs; vendor email → m@far.ag;
  description → "between you and your coding agent".
