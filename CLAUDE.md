# Marginalis — agent notes

JetBrains plugin: in-editor agent↔human comment threads. Read
`marginalis-handover.md` (design brief) and `tenant-environment.md` (sandbox
rules) before substantial work.

## Building in the tenant sandbox

The sandbox blocks inbound loopback TCP except ports 5005 and 63342. Gradle's
daemon — and its *single-use daemon* fallback — talk over random loopback
ports, so every Gradle process must run fully in-process:

- JDK: read-only share at `~/jdks/25.0.3-ms` (Java 25 → requires Gradle 9.1+,
  Kotlin 2.3+; we compile to target 21, no toolchain — auto-provisioning is
  network-blocked).
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
- Test workers also fork over loopback — expect `test` to need the same care
  once tests exist; if truly stuck, ask the operator for a declared port or
  temporary permissive inbound mode.

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

Plugin reinstall (not auto-reload) is needed after each rebuild.

## Project conventions

- API `line` parameters are 1-based (as agents read files); internal
  `MarginNote.line` is 0-based. Convert at the transport boundary.
- The built-in server port (63342) is the declared transport; `runIde`
  sandbox IDEs also use it.
- Milestones live in the handover doc §9 — keep M-scope discipline; don't
  gold-plate ahead of the current milestone (§12).
