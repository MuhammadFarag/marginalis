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
