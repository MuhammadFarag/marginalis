# Marginalis — agent notes

JetBrains plugin: in-editor agent↔human comment threads. Read
`docs/marginalis-handover.md` (design brief, historical) and `tenant-environment.md` (sandbox
rules) before substantial work.

## Building in the tenant sandbox

The sandbox blocks inbound loopback TCP except ports 5005 and 63342. Gradle's
daemon — and its *single-use daemon* fallback — talk over random loopback
ports, so every Gradle process must run fully in-process:

- JDK: read-only share at `~/jdks/25.0.3-ms` (Java 25 → requires Gradle 9.1+,
  Kotlin 2.3+; we compile to target 21, no toolchain — auto-provisioning is
  network-blocked).
- Platform dependency is property-switched: default (and CI) compiles against
  `ideaIC-2025.2` from the CDN; the sandbox sets `marginalis.localIde` in
  `~/.gradle/gradle.properties` to use the local IDE share instead. If local
  builds fail resolving `idea:ideaIC`, that property went missing.
- CI (GitHub Actions, `.github/workflows/`) is the honest environment: real
  2025.2 floor + Plugin Verifier (`recommended()` IDE set). Tag `v*` →
  release zip. Local sandbox builds compile against a newer local IDE, so
  floor violations surface in CI, not locally.
- `org.gradle.daemon=false` alone is NOT enough. The client JVM must pass
  Gradle's `DaemonCompatibilitySpec` or a single-use daemon is forked anyway:
  - `gradle.properties` sets `org.gradle.jvmargs=-Xms256m -Xmx2g
    -Dfile.encoding=UTF-8` (explicit `-Xms` because gradlew injects a default
    `-Xms64m`).
  - `GRADLE_OPTS` (exported in `~/.zshrc.local`) must carry exactly those args
    plus `-Duser.country=CA -Duser.language=en -Duser.variant
    -Dorg.gradle.internal.instrumentation.agent=false`. The spec demands exact
    set equality of immutable args — do not add extras (e.g. `--add-opens`).
  - `kotlin.compiler.execution.strategy=in-process` (the Kotlin daemon uses
    loopback too).
- Symptom of a mismatch: "a single-use Daemon process will be forked" then
  "Could not connect to the Gradle daemon". Fix the arg mismatch; don't retry.
- Test workers fork over loopback too — CONFIRMED: `gradle :core:test` dies
  locally with worker ConnectException. Local loop: `scripts/test-core.sh`
  (compiles via Gradle, executes via JUnit ConsoleLauncher in one JVM). CI
  runs the normal Gradle test task.

Non-interactive shells don't source `~/.zshrc.local`, so scripts/CI-style
invocations must export `JAVA_HOME`, `PATH`, and `GRADLE_OPTS` themselves
(copy the exports from `~/.zshrc.local`).

## Network

Egress is TCP/443 to allowlisted hosts only (see `tenant-environment.md`).
Verified working in runtime mode (2026-07-16): Maven Central, Gradle Plugin
Portal, GitHub incl. `raw.githubusercontent.com`, `services.gradle.org`
(wrapper distributions), `plugins.jetbrains.com`,
`data.services.jetbrains.com`. Verified BLOCKED despite being on the
documented allowlist (reported to operator 2026-07-16, likely CloudFront IP
rotation vs. an IP-pinned filter): `cache-redirector.jetbrains.com`,
`download.jetbrains.com`, `www.jetbrains.com` — these carry the IntelliJ
Platform artifacts, so builds fail at `Could not resolve idea:ideaIC` until
fixed. A hanging connection means "not allowlisted" — stop retrying and ask
the operator.

## Verification workflow (the one that works)

The tenant session is headless — `runIde` launches but its windows are
invisible and the unified IDEA build blocks on a license/first-run dialog
nobody can click. Verified working instead (2026-07-17, M0):

1. `./gradlew buildPlugin` → zip in `build/distributions/`.
2. Operator installs the zip in their own IDE (host session, Install Plugin
   from Disk) and opens `sample-project/` from the shared path.
3. Agent drives the built-in server on `127.0.0.1:63342` — cross-user
   loopback to the host IDE works through the declared port.
4. Kill any tenant-side IDE first: if it holds 63342, the host IDE binds
   63343+, which the sandbox blocks.

**Reinstalling after a rebuild — hot reload, no IDE restart (verified
2026-07-20):** the one-step "Install Plugin from Disk over an existing
version" ALWAYS demands a restart — `PluginInstaller.installFromDisk`
hard-codes `isRestartRequired = oldFile != null || …`, so the dynamic
machinery is never consulted (and idea.log stays silent about why). The
two-step path is fully dynamic:

1. Settings → Plugins → Marginalis → **Uninstall** — restart-free because
   `MarginalisUnloadListener` strips our classes from structures that
   outlive the classloader (markup-model highlighters, editor inlays and
   user-data keys). If a restart prompt appears here, the unload leaked;
   idea.log then names the culprit.
2. **Install Plugin from Disk** with the new zip — no old version on disk →
   dynamic-load branch; startup activity re-runs and rehydrates threads
   from `.idea/marginalis.json`.

Threads and settings live outside the plugin dir (`.idea/marginalis.json`,
IDE `options/marginalis.xml`), so the uninstall loses nothing.

> **Skill source of truth:** the agent-facing skill ships as the Claude Code
> plugin `marginalis` in the operator's marketplace —
> `/Users/Shared/plugins/mfarag-plugins/marginalis` (tenant path
> `~/plugins/mfarag-plugins/marginalis`). Update it THERE and bump the
> version in `.claude-plugin/plugin.json` whenever the protocol or API
> changes. Do not recreate copies in `~/.claude/skills/` or this repo.
> The marketplace IS a git repo, but owned by the operator: the tenant can
> edit files yet cannot commit (`.git` is operator-only — and git shows
> "dubious ownership" until `safe.directory` is added). After skill edits,
> hand the operator a ready commit message; they commit from their side.

## Margin protocol (LIVE — follow this when the operator's IDE is up)

The plugin this project builds is also our working channel. Use the skill's
script (`~/plugins/mfarag-plugins/marginalis/scripts/marginalis.sh`) rather
than raw curl — it scopes anchored calls to the cwd's project and attaches
identity. When the host IDE is running (`marginalis.sh ping`):

- **Identify yourself FIRST**: `export MARGINALIS_AUTHOR="Claude"
  MARGINALIS_AUTHOR_ID="claude-tenant-marginalis"` before any margin call.
  Read receipts are per agent (since 0.1.7) — an anonymous sweep reads as
  the shared "Agent" identity.
- **Start every turn** with `marginalis.sh unread` — the operator leaves
  margin comments (⌃⌥M in their editor) and replies there, born unread.
  This is the entire inbound channel; reading marks seen for YOUR identity.
- **Before editing any file**, check
  `comment_list?file=<path>&status=open` — the §3.1 invariant: never edit a
  file with open threads; resolve them first (conclusion → code change, or
  explicitly moot).
- Reply with `comment_reply {thread_id, body}`; resolve with
  `comment_resolve {thread_id}`.
- **Heed the span** (0.1.8+): a thread row may carry `segment {exact,
  prefix?, suffix?}` — the operator selected that exact text (⌃⌥M on a
  selection), so address the quoted span specifically, not the line in
  general. Agents cannot create segments; `comment_add` is line-only.
- **Resolution etiquette — the resolver is the completer.** RESOLVED means
  "the outcome is in the code (or decided moot)", and the gutter marker drops
  at that moment. So: approval is a *reply* ("do it"); the party who then
  consolidates the conclusion into code performs the resolve *after* editing.
  Never treat an early human Resolve as a work request — if it happens, ask.
  No-action threads: either party resolves immediately.
- Threads persist across restarts (M2): `.idea/marginalis.json` per project,
  re-anchored by content on reopen; unmatchable anchors become ORPHANED.
- Bulk ops exist on both sides (`comment_resolve_all` / `comment_clear_all`,
  optional file scope; tool-window title actions for the human). Clearing is
  destructive — agent-side, only on explicit human request.

## Project conventions

- API `line` parameters are 1-based (as agents read files); the core model's
  `CommentThread.line` is 0-based. Convert at the transport boundary.
- Architecture: `core/` is pure Kotlin (model, lifecycle, AnchorPolicy,
  ThreadsCodec) — it imports nothing from the plugin module, ever. The
  plugin module is adapters: transport, Swing/editor UI, VFS+file I/O,
  markers (MarginalisStore pairs core threads with live RangeHighlighters).
- The built-in server port (63342) is the declared transport; `runIde`
  sandbox IDEs also use it.
- **Roadmap = `docs/BACKLOG.md`** (shipped / open / deferred / decision
  log). Keep scope discipline; don't gold-plate ahead of the agreed item.
- **Review workflow**: substantial batches get a review walkthrough in the
  margin before commit (`add … <order>` steps; one walkthrough per project
  — never span projects). Operator resolves silently = approved; replies =
  change requests. Batch commit after review; release = tag `v*` (CI
  builds the zip and publishes the GitHub Release; Marketplace publish
  activates once the operator's four secrets exist).
- Guided vocabulary is "walkthrough"/"steps" (renamed from tour/stops,
  v0.1.3) — wire param `walkthrough`, UI actions First/…/Last Step.
