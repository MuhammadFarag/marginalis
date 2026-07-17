# Marginalis — Tenant Environment: What Works and What Doesn't

**Audience:** the agent working inside the `marginalis` tenant.
**Companion to:** `marginalis-handover.md` (the design brief). This note is about the *sandbox you
are running in*, not the plugin.

You are running as the macOS user `marginalis`, a sandboxed "tenant" account. The host operator
(Muhammad) controls the sandbox via a profile file you cannot see or edit
(`~/.config/tenant/profiles/marginalis.toml` on the host). If something below blocks you, ask the
operator — do not burn time retrying.

## Filesystem

- `~/src` is a symlink to a co-working directory shared read-write with the host. This project
  lives at `~/src/marginalis/`. Anything you write there, the host can read, and vice versa —
  it is the hand-off channel.
- `~/.local/share/chezmoi` is the shared dotfiles source; run `chezmoi apply` to materialise
  dotfiles into `$HOME`.
- `~/plugins/mfarag-plugins/*` are read-only Claude Code plugin shares.
- `~/jdks/25.0.3-ms` is a read-only JDK share (Microsoft OpenJDK 25.0.3 LTS). Set
  `JAVA_HOME="$HOME/jdks/25.0.3-ms"` (and prepend `$JAVA_HOME/bin` to PATH) in `~/.zshrc`.
  Heads-up: *running* Gradle itself on Java 25 requires Gradle 9.1+; if the project pins an older
  Gradle or the IntelliJ Platform build insists on JDK 21, ask the operator for a 21 share —
  don't try to download one yourself (blocked in runtime mode).
- The rest of `$HOME` is yours (Gradle caches, IDE sandboxes, etc. all fine).

## Network: egress is TCP/443 ONLY, allowlisted hosts only

Everything else is dropped by the packet filter. Symptom of a non-allowlisted host (or any
non-443 port): connection hangs or is refused — it is not a server-side problem, stop retrying.

**Works in normal (runtime) mode:**

- GitHub — git over HTTPS, api, web. (SSH to github.com:22 will NOT work; use HTTPS remotes.)
- Anthropic — `api.anthropic.com`, `claude.ai`, `downloads.claude.ai`.
- Maven Central — `repo.maven.apache.org`, `repo1.maven.org`.
- Gradle Plugin Portal — `plugins.gradle.org`, `plugins-artifacts.gradle.org`.
- JetBrains non-CDN — `data.services.jetbrains.com`, `packages.jetbrains.team`.
- MCPs — `mcp.context7.com`, `mcp.devin.ai`.

**JetBrains CDN hosts DO NOT work** (`cache-redirector.jetbrains.com`, `download.jetbrains.com`,
`plugins.jetbrains.com`, `downloads.marketplace.jetbrains.com`): they are CloudFront-backed with
rotating IPs, which defeats the firewall's load-time hostname pinning. Connections to them hang.
Do not configure the build to download IDE distributions, JetBrains Runtime, or Marketplace
plugins — it will time out. Use the local platform instead:

## IntelliJ Platform: use the LOCAL IDE, never download one

A full IntelliJ IDEA Ultimate install (2026.1.4, build 261.26222.65) is shared read-only at
`~/ides/IntelliJ IDEA.app`. Wire the build to it:

```kotlin
// build.gradle.kts
repositories { mavenCentral() }
dependencies {
  intellijPlatform {
    local(providers.gradleProperty("platformPath"))  // machine-agnostic
  }
}
```

```properties
# gradle.properties (or ~/.gradle/gradle.properties)
platformPath=/Users/marginalis/ides/IntelliJ IDEA.app
```

- `runIde` uses this install's bundled JetBrains Runtime — no JBR download.
- Plugin verifier: point `pluginVerification { ides { local(...) } }` at the same app, or skip
  verification here.
- Depend only on bundled platform modules/plugins; a Marketplace (third-party) plugin dependency
  would need the blocked Marketplace hosts — flag it to the operator instead.
- The handover brief targets PyCharm; compiling against IDEA Ultimate (which bundles Python
  support) is fine for now — raise it with the operator if you need PyCharm proper for testing.

So ordinary `./gradlew build` dependency resolution works in runtime mode: Gradle plugins and
Maven deps from the network, the platform from the local app.

**Works only in install mode** (the operator must enter it on the host with
`tenant mode marginalis install`; you cannot widen the network yourself):

- Gradle wrapper distribution downloads — `services.gradle.org` / `downloads.gradle.org`.
  First `./gradlew` run and any wrapper version bump need install mode.
- JDK provisioning — foojay resolver (`api.foojay.io`) + Adoptium (`api.adoptium.net`), and
  Homebrew hosts. Caveat: Adoptium redirects binary downloads to GitHub release assets, which may
  still fail; the reliable fallback is asking the operator to `brew install --cask temurin@21`.
- Claude Code installer — `storage.googleapis.com`.

**Never works:** any other host, any port other than 443 (no plain HTTP, no SSH out, no custom
registries).

## Loopback ports: the Gradle daemon trap

The sandbox blocks inbound loopback (127.0.0.1) TCP except declared ports — and this
indiscriminately blocks *your own* connections to your own undeclared ports.

- **The Gradle daemon will hang or fail**: it listens on a random loopback port that can't be
  declared ahead of time. Run all Gradle with the daemon off. Put this in
  `~/.gradle/gradle.properties`:

  ```properties
  org.gradle.daemon=false
  ```

  (or pass `--no-daemon` every time; the properties file is less error-prone). Expect builds to
  be slower than daemon-backed ones — that is the sandbox, not a regression.
- **Declared ports (usable):** `5005` (JVM remote debug, e.g. `runIde --debug-jvm` attach target)
  and `63342` (IntelliJ built-in server in the runIde sandbox IDE).
- Any *other* local server you start (test HTTP server, etc.) will be unreachable even from your
  own processes. If you genuinely need another port, ask the operator to declare it or to run
  `tenant inbound marginalis permissive` temporarily.
- UDP loopback is unfiltered; only TCP is gated.

## Practical checklist for first build

1. Confirm a JDK is present (`java -version`); if not, this is an operator/install-mode task.
2. `org.gradle.daemon=false` in `~/.gradle/gradle.properties`.
3. Wrapper zip not yet cached? → operator enters install mode for the first `./gradlew` run.
4. Point the build at the local platform (`platformPath` above) — never at a downloadable
   IDE version. `runIde` launches a GUI IDE — it works on this machine but the window appears
   in the tenant's login session, so coordinate with the operator if you need it observed.
