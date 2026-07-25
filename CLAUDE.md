# Marginalis — agent notes

JetBrains plugin: in-editor agent↔human comment threads. Design history
lives in `docs/marginalis-handover.md` (the original brief) and
`docs/navigate-handover.md`; the living roadmap is `docs/BACKLOG.md`.

## Building

- `./gradlew buildPlugin` → zip in `build/distributions/`;
  `./gradlew :core:test` runs the domain tests (`scripts/test-core.sh`
  runs them in a single JVM for environments where forked test workers
  can't connect back).
- CI (`.github/workflows/`) is the honest environment: compiles against
  the real `ideaIC-2025.2` floor and runs the Plugin Verifier
  (`recommended()` IDE set). Local builds may compile against a newer
  local IDE via the `marginalis.localIde` property, so floor violations
  surface in CI, not locally.
- Releases: tag `v*` → CI builds the zip and publishes the GitHub
  Release; Marketplace publishing activates when the publishing secrets
  exist.

## Dogfooding — the margin protocol

This project reviews itself in its own margins. When the reviewer's IDE
is running the plugin:

- Sweep unread margin comments at the start of a turn; replies land there,
  born unread.
- Never edit a file with open threads — drive each to resolution first
  (its conclusion becomes part of the edit, or reply why it needs none).
- The resolver is the completer: RESOLVED means the outcome is in the
  code (or explicitly moot). Approval is a reply; land the change, then
  resolve.
- Substantial batches get a review walkthrough (`add … <order>` steps,
  one walkthrough per project) before commit. A step resolved silently is
  approved; a reply is a change request.

## Project conventions

- API `line` parameters are 1-based (as agents read files); the core
  model's `CommentThread.line` is 0-based. Convert at the transport
  boundary.
- Architecture: `core/` is pure Kotlin (model, lifecycle, walks,
  AnchorPolicy, ThreadsCodec) — it imports nothing from the plugin
  module, ever. The plugin module is adapters: transport, Swing/editor
  UI, VFS + file I/O, markers (MarginalisStore pairs core threads with
  live RangeHighlighters).
- Roadmap discipline: open work lives in GitHub issues (`gh issue list`)
  — check there before starting, file new findings there, and close the
  issue when the work lands. `docs/BACKLOG.md` is the historical shipped
  record and decision log, append-only. Don't gold-plate ahead of the
  agreed item.
- Guided vocabulary is "walkthrough"/"steps" (wire param `walkthrough`,
  UI actions First/Previous/Next/Last Step).
