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
