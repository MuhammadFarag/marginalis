# Marginalis CLI — feature brief

A Rust CLI as the public face of the agent side, replacing the private
skill's wrapper script and most of its teaching duty. Draft for margin
review — decisions below are proposals until their threads resolve.

## Why

The severity-vocabulary change exposed the structural problem: the server's
contract lives in one repo, the text teaching agents that contract lives in
another, and nothing fails when they drift apart. A public release needs the
drift to be impossible by construction, and needs to serve agents that
aren't Claude Code.

The layered answer, agreed in chat:

1. **The server serves the contract** — a version-matched agent guide
   shipped inside the plugin zip. Source of truth; kills drift.
2. **The CLI is the hands** — discovery, scoping, validation, teaching
   errors. Agents handle CLIs better than raw HTTP or MCP config, and
   `--help` is progressive disclosure that can't be skipped.
3. **A one-line doorbell** — an AGENTS.md line or stub skill saying
   "margins are live here; use `marginalis`." Never changes when the
   contract does. For Claude Code, explicit slash commands are the same
   doorbell with a handle: `/marginalis:marginalis` today, and named
   verbs like `/marginalis:walkthrough` ("walk me through this change")
   as natural future additions.

## Proposed decisions

- **Language: Rust.** Agents invoke the CLI dozens of times per session;
  cold-start latency is the budget. Single static binary, no runtime.
- **Crates: `clap` + `ureq` + `serde_json`.** Synchronous HTTP — one
  loopback call per invocation; tokio buys nothing. Binary under ~5 MB.
- **Lives in this repo, under `cli/`.** Same tag, same walkthrough, same
  release as the plugin — the one-commit litmus test survives. The tap
  holds only a formula, never source.
- **The CLI stays thin.** No rule reimplementation: severity words,
  anchoring semantics, lifecycle guards all stay server-side; the CLI
  relays the server's teaching errors verbatim. A CLI that re-encodes the
  rules is the skill-drift problem in a compiled language.
- **Version-skew warning on every run.** Ping already returns `version`;
  compare against the compiled crate version, warn on mismatch. The drift
  alarm the skill never had.
- **Distribution: Homebrew tap** (`homebrew-marginalis`), formula
  bumped by CI on each `v*` tag; private to start, flips public whenever —
  visibility is a one-click call, nothing depends on it.
  Windows (winget/scoop or bare release binaries) is deferred.
- **Binary name: `marginalis`, full word.** Agents do the typing;
  discoverability beats brevity. A short alias can ride the formula later.
- **`doctor` ships in Phase 1.** Setup friction is the top public-release
  risk; ping each port, report IDE/plugin versions, check the navigation
  setting, name the fix for every failure.
- **The CLI is not a release blocker.** The plugin ships on its own
  timeline (internal testing included); the CLI catches up when the
  toolchain and phases allow.
- **Help vs guide.** `marginalis help` (clap's built-in) documents usage —
  verbs, flags, examples. `marginalis guide` prints the server-served
  contract: one markdown blob to start, growing `guide <topic>` sections
  (anchoring, etiquette, walkthroughs) only if context spend hurts.

## Subcommand tree

Parity with `marginalis.sh` first, then the new verbs:

    marginalis ping | discover [path] | doctor | whoami
    marginalis unread | list | open-on <file>
    marginalis add <file> <line> <anchor> <body> [--order N] [--walkthrough L] [--severity blocker|nit]
    marginalis reply <thread> <body> | resolve <thread> | reopen <thread>
    marginalis reanchor <thread> <line> <anchor>
    marginalis resolve-all [file] | clear-all [file]
    marginalis navigate <file> <line> <anchor>
    marginalis batch          # NEW: mixed add/reply/resolve ops as NDJSON on
                              # stdin — a nine-step walkthrough or a
                              # post-review consolidation in ONE invocation,
                              # per-item results out. CLI-side fan-out over
                              # the existing endpoints; a server batch
                              # endpoint only if round trips ever hurt.
    marginalis guide          # NEW: print the server-served agent contract
    marginalis mcp            # OPTIONAL, demand-driven: stdio MCP bridge for
                              # agent surfaces that cannot exec a binary
                              # (browser-hosted agents, some IDE assistants).
                              # Redundant for Claude Code — built only when a
                              # real user of that shape shows up.

Identity via `MARGINALIS_AUTHOR` / `MARGINALIS_AUTHOR_ID`; scoping via cwd
with `MARGINALIS_PROJECT` override — same env contract as the script.
Identity is per-process env, full stop — no config file. The real-world
shape rules a file out: worktrees w1/w2/w3, each hosting two sessions
(one designing, one implementing) — six identities, three shared
directories, one home directory. Nothing keyed by user or by cwd can
tell those apart; only the process environment can. So: flag > env >
unset. The launcher that starts a session hands it its identity (env at
launch), or the agent introduces itself with a role-qualified name;
`whoami` prints the effective identity and honestly reports when there
is none rather than inventing one.

## Phases

- **Phase 0 — toolchain unblock (operator).** rustup + cargo in the
  tenant: needs install mode and egress allowlist additions
  (`static.crates.io`, `index.crates.io`, `static.rust-lang.org`).
- **Phase 1 — scaffold + parity.** `cli/` crate, all script verbs, skew
  warning, `--help` text written for agent readers. Script retires only
  at full parity.
- **Phase 2 — the served guide (RELEASE UNBLOCKER, sequenced first).**
  Server: `GET /api/marginalis/agent_guide` returning the contract from
  plugin resources, version-matched; CI check that the guide mentions
  every endpoint. Pure Kotlin — no Rust dependency, ships in the next
  plugin release while Phase 0 waits on the toolchain. From that release
  on, the doorbell is "GET agent_guide and take it from there" — curl
  suffices, no CLI required. CLI's `guide` verb prints the same document.
- **Phase 3 — release wiring.** CI builds macOS binaries on `v*` tag
  alongside the plugin zip; workflow bumps the tap formula.
- **Phase 4 — MCP bridge mode (optional, demand-driven).** `marginalis
  mcp` exposing the verbs as tools; descriptions defer to the guide for
  philosophy. Built only when an exec-less agent surface actually asks.
- **Phase 5 — the skill shrinks to a doorbell.** Trigger text plus "run
  `marginalis guide`"; teaching content deleted.

## Open questions

(First round settled in margin review 2026-07-25: name, `doctor`, tap
timeline, help/guide split, Phase 2 sequencing — all promoted to the
decisions and phases above.)

- Batch shape: NDJSON-on-stdin with CLI-side fan-out is the proposal —
  confirm, or push for a server-side batch endpoint from the start?

(Identity fallback file: settled 2026-07-25 — dropped. Multiple sessions
per worktree make any file ambiguous; identity is per-process env only.)
