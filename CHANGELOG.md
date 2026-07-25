# Changelog

All notable changes to Marginalis. The format follows
[Keep a Changelog](https://keepachangelog.com); the build injects the
current version's section into the plugin's Marketplace change notes.
History before 0.1.19 lives in git tags.

## [Unreleased]

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
