# Iconography handover — implement GitHub issue #25

> **Post-implementation note (0.1.29):** the tool window title count
> (`N ✉`, §2) was removed after live review — it counted every open
> thread owed, which didn't match the badges on screen. What to count is
> deferred to #26. The rest of this spec shipped as written.

You are implementing a design that was settled with the user in an earlier
session you have no access to. This document is the complete brief. Where it
and your instincts disagree, it wins; where it is silent or a platform fact
makes a decision impossible, stop and ask the user instead of improvising.

- **Issue:** #25 "Marginalis iconography: one family from the plugin icon"
  (`gh issue view 25 --comments` — rounds 1–3 are recorded there).
- **Repo state when written:** `main` at v0.1.28 (2026-09-28), clean tree.
- **Visual references (on disk, gitignored):** open these in a browser to see
  the exact drawings in context —
  `.features/iconography-playground.html` (round 1: gutter family, statuses,
  tool window icon), `.features/turn-signals-playground.html` (round 2: turn
  signals, file/project cues), `.features/turn-icons-playground.html`
  (round 3: the two turn icons at real pixel sizes). The SVG geometry below is
  copied from them; if a number here and a playground disagree, the
  playground's default rendering of the chosen option is the source of truth.

## Why this exists

Every IDE surface used generic grey stock icons; nothing of the plugin's own
identity (violet margin bar, violet agent bubble, blue human bubble — see
`src/main/resources/META-INF/pluginIcon.svg`) reached the gutter, tabs or tool
window. Three user complaints drove it: gutter marks are easy to miss; the
line-thread icon (`AllIcons.General.Balloon`) is *identical* to the IDE's
pull-request review comment icon; the tool window icon
(`AllIcons.Toolwindows.ToolWindowMessages`) is generic. The user also said the
old ● / ○ turn glyphs "never stuck".

## Global rules for every new icon

- **Dark-first, theme-neutral.** One mid-tone palette that reads on both
  themes, so each new icon is a **single SVG with no `_dark` variant**
  (IntelliJ only looks for `_dark` when it exists). The user works in the dark
  theme — judge everything there first, then check light still reads.
- **Palette (exact):**

  | Role | Hex |
  |---|---|
  | Brand violet — ordinary threads; "your move" | `#A35BD6` |
  | Blue — "the agent's move" | `#3D8BD9` |
  | Finding (red) | `#E0474C` |
  | Guidance (green) | `#3FA35A` |
  | Question (amber) | `#D98E1B` |
  | Orphan frame (amber) | `#E0A21B` |

- **16-unit viewBox**, `width="16" height="16"`, strokes with
  `stroke-linejoin="round"` (and `stroke-linecap="round"` where noted).
- **No `<text>` in icon SVGs** — fonts aren't guaranteed; draw glyphs as paths.
- Every mark must stay distinguishable **by shape as well as color**.
- Deliver files in `src/main/resources/icons/`, loaded through the existing
  `dev.marginalis.plugin.ui.MarginalisIcons` object (it already holds
  `HandBack`).

## 1. Gutter marks — the "brand bubble" family

A speech bubble drawn in **outline**, about **14px** in the gutter, whose tail
points **right, at the code** (the PR-review balloon's tail points down-left —
that difference is the whole point).

**Bubble body** (hue `H` per the intent table below):

```svg
<path d="M2.5 2h8.5a3 3 0 0 1 3 3v1.6l1.6 1.9-1.6 1.2V11a3 3 0 0 1-3 3H2.5z"
      fill="none" stroke="H" stroke-width="1.4" stroke-linejoin="round"/>
```

**Inside the bubble — one glyph, same hue `H`, centred around (8.1, 8):**

| Intent | Hue | Glyph |
|---|---|---|
| none (ordinary) | violet `#A35BD6` | three dots: `<g fill="H"><circle cx="5.9" cy="8" r=".85"/><circle cx="8.1" cy="8" r=".85"/><circle cx="10.3" cy="8" r=".85"/></g>` |
| finding | red `#E0474C` | eye: `<path d="M4.8 8Q8.1 5.03 11.4 8Q8.1 10.97 4.8 8Z" fill="none" stroke="H" stroke-width="1.15"/><circle cx="8.1" cy="8" r=".9" fill="H"/>` |
| guidance | green `#3FA35A` | bulb: `<circle cx="8.1" cy="7.3" r="2" fill="H"/><rect x="7.1" y="9.1" width="2" height="1.6" rx=".6" fill="H"/>` |
| question | amber `#D98E1B` | question mark: `<path d="M6.6 6.5a1.6 1.6 0 1 1 2.3 1.45c-.5.28-.8.65-.8 1.2v.25" fill="none" stroke="H" stroke-width="1.3" stroke-linecap="round"/><circle cx="8.1" cy="10.9" r=".75" fill="H"/>` (the playground drew "?" as bold text; match that look with a path) |

Threads on one line whose intents **disagree** fall back to the ordinary
(violet, three dots) mark — same rule as today.

**Subject cues** (same hue `H`, drawn over the bubble):

| Subject | Cue |
|---|---|
| line thread | none |
| file-level thread (gutter beside line 1) | **folded corner**: `<path d="M2.5 2h3.6l-3.6 3.6z" fill="H"/>` |
| project-level thread (tool window only — no gutter) | **arc on top** (a second bubble peeking behind): `<path d="M4.5 .4h7.2a2.6 2.6 0 0 1 2.6 2.6" fill="none" stroke="H" stroke-width="1.2" stroke-linecap="round"/>` |

That is 3 subjects × 4 intents = **12 mark variants**. Recommended: 12 static
SVGs (e.g. `mark_<line|file|project>_<none|finding|guidance|question>.svg`)
plus composition for status — your call on file layout, but keep the decision
of *which* mark (subject + agreed intent) in **core** with tests.

**Status on top of the mark** — precedence is unchanged and already lives in
core as `dev.marginalis.core.AggregateState` (RESOLVED, ORPHANED,
OPEN_BLOCKER, UNREAD, OPEN):

| State | Treatment (new) | Today |
|---|---|---|
| OPEN | the plain mark | stock balloon/page/intent icon |
| UNREAD (someone wrote something no agent has read) | small **blue dot badge** | same (keep `BadgeIconSupplier.infoIcon`) |
| OPEN_BLOCKER | small **red dot badge** | same (keep `BadgeIconSupplier.errorIcon`) |
| RESOLVED | the mark **dimmed to about a third** (e.g. `IconLoader.getTransparentIcon(mark, ~0.33f)`) | green checkmark *replaced* the mark |
| ORPHANED | the mark **faded (~55%) inside a dashed amber frame**: `<rect x=".6" y=".6" width="14.8" height="14.8" rx="3" fill="none" stroke="#E0A21B" stroke-width="1.1" stroke-dasharray="2 1.6"/>` | warning triangle *replaced* the mark |

Watch-out to mention in your walkthrough (don't redesign it): a *finding* is
red and a *blocker* badge is red too — the user chose both; flag it if it reads
badly in the real gutter.

**Where marks appear:**
- Gutter: `src/main/kotlin/dev/marginalis/plugin/ui/ThreadGutterIconRenderer.kt`
  (`base` selection ~line 40–47, `getIcon()` status mapping ~line 55–61).
  The file-level glyph beside line 1 is built in
  `MarginalisMarkers.refreshFileGlyph` and uses the same renderer.
- Tool window thread rows: `MarginalisToolWindowFactory.kt`, the
  `NodeData.ThreadNode` branch of the tree renderer (~line 733–742) — today it
  shows GreenCheckmark / Warning / Nodes.Module / Any_type / Balloon. Replace
  with **the same mark** the gutter would show for that thread (subject +
  intent hue + status), so the tree and gutter speak one vocabulary. Keep the
  existing row words (`L12`/`file`/`project`, intent word, `blocker`/`nit`).
- Actions whose icon stands for "a Marginalis conversation" switch to the
  ordinary violet marks: `marginalis.addComment` (plugin.xml ~line 63,
  `AllIcons.General.Balloon`) → line mark; `marginalis.commentOnFile`
  (plugin.xml ~line 76, `AllIcons.FileTypes.Any_type`) → file mark;
  `CommentOnProjectAction` (`MarginalisToolWindowFactory.kt` ~line 133,
  `AllIcons.Nodes.Module`) → project mark; the thread panel's **Reopen**
  action (`ThreadPanel.kt` ~line 391, `AllIcons.General.Balloon`) → line mark.
  The **Resolve** action keeps `GreenCheckmark` (it's a verb, not a thread).

## 2. Turn signals — replace ● / ○ everywhere

Today `FileTurn.glyph()` returns `●` (you owe the reply — the agent spoke
last, or addressed it to you) and `○` (the agent owes it). Core's
`dev.marginalis.core.Turn` (USER / AGENT) and `CommentThread.turn()` decide
*whose* turn it is — **don't change that logic**, only its presentation.

**The two icons (round 3: keep the round-2 sketches, exactly):**

- **✉ your move** — outline envelope, violet `#A35BD6`:
  ```svg
  <g fill="none" stroke="#A35BD6" stroke-width="1.5" stroke-linejoin="round" stroke-linecap="round">
    <rect x="1.8" y="3.4" width="12.4" height="9.2" rx="1.6"/>
    <path d="M2.4 4.4 8 8.7l5.6-4.3"/>
  </g>
  ```
- **✈ the agent's move** — the **Hand Back outline plane**, blue `#3D8BD9`:
  ```svg
  <path d="M1.5 7.2 14.5 1.5 8.8 14.5 7.3 8.7Z M7.3 8.7 14.5 1.5"
        fill="none" stroke="#3D8BD9" stroke-width="1.5" stroke-linejoin="round" stroke-linecap="round"/>
  ```
  This is the same drawing as the Hand Back toolbar button
  (`src/main/resources/icons/handBack.svg`), deliberately: you click the
  plane, the file then wears the plane. The **button itself stays as it is**
  (monochrome grey toolbar icon with its `_dark` variant); only the
  turn-signal copy is blue.

**Text forms** (only for plain-string surfaces): `✉` for your move, `✈` for
the agent's. `FileTurn.glyph()` should return these.

**Where each surface gets which form:**

| Surface | Today | New |
|---|---|---|
| Editor tabs | `name ●` via `MarginalisTabTitleProvider` (a plain-string API) | **overlay badge on the file's own icon** — remove the character after the name |
| Project view | `●` in the node's location string via `MarginalisProjectViewDecorator` | **overlay badge on the file's own icon** — remove the location-string glyph |
| Tool window title count | `content.displayName = "2 awaiting you"` (`MarginalisToolWindowFactory.kt` ~line 106) | **`2 ✉`** — the number followed by the text form (plain-string surface) |
| Tool window file rows | `●2 ○1` colored text (~line 730–731) | icon form: envelope + count, plane + count |
| Tool window thread rows | trailing `●`/`○` (~line 766) | trailing icon form |
| Tool window stripe badge | `BadgeIconSupplier.getInfoIcon(awaiting > 0)` — a blue dot | the **your-move envelope** as the badge when something awaits you; the **red error badge still wins for an open blocker** (keep that precedence) |

**Overlay badge spec (round 3 defaults, chosen explicitly):** the **bare
glyph** (no halo, no colored chip), **8px**, at the **bottom-right** corner of
the file icon, using the **same drawing scaled down** (no separate simplified
small drawing). If the 8px outline is illegible in the real IDE, don't
redesign — show it in the walkthrough and let the user decide (the round-3
playground has halo/chip/simplified alternatives ready).

**Platform fact you must resolve — the icon-layer extension point.** Tabs and
the Project view take their icon from the file's icon, so the overlay has to
be applied where file icons are computed, the way VCS and read-only marks
decorate them. Candidates to investigate: `com.intellij.iconLayerProvider`
(`IconLayerProvider`), `com.intellij.fileIconPatcher` / `FileIconPatcher`,
`com.intellij.fileIconProvider`. Requirements: **public API** (CI's Plugin
Verifier fails on internal / scheduled-for-removal usage), available on the
**2025.2 floor** (`since-build="252"`), and it must refresh when a file's turn
changes (the store listener in `MarginalisStartup` already calls
`FileEditorManager.updateFilePresentation(vFile)` and
`ProjectView…currentProjectViewPane?.updateFrom(vFile, false, false)` — confirm
those actually re-fetch the icon). Inspect the platform classes in the local
IDE's jars (`javap` against `/Users/Shared/ides/IntelliJ IDEA.app/Contents/lib`)
before choosing. If no public mechanism covers both tabs and the Project view,
**stop and ask** — do not fall back to internal API.

**Inline icons in tree rows.** `ColoredTreeCellRenderer` gives each row one
leading icon; icons *mid-line* (the counts and the trailing turn signal) may
need a custom renderer. If that's disproportionate, use the **text forms
(✉ / ✈) in violet / blue** for the row counts and trailing signals and say so
in the walkthrough — but try the icon form first.

## 3. File vs project threads

Settled in round 2: file-level threads carry the **folded corner**, project
threads the **arc on top** (geometry in §1). Same marks in the gutter (file
threads, beside line 1) and in tool window rows (file and project threads).
The project section node in the tool window tree (~line 710, `Nodes.Module`)
should use the project mark too.

## 4. Tool window icon

A **miniature of the plugin icon** — violet margin bar, violet front bubble,
blue back bubble — as a custom SVG, colored (theme-neutral palette), replacing
`icon="AllIcons.Toolwindows.ToolWindowMessages"` in `plugin.xml` (~line 98)
and the `STRIPE_ICON` base in `MarginalisToolWindowFactory.kt` (~line 122).
Geometry (40-unit viewBox, scale to the new-UI tool window size — check the
platform's current size convention for tool window icons):

```svg
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 40 40">
  <rect x="4" y="4" width="3.5" height="32" rx="1.75" fill="#A35BD6"/>
  <rect x="22" y="17" width="13" height="9.5" rx="4.75" fill="#3D8BD9"/>
  <polygon points="29.5,25 34,25 32,29.5" fill="#3D8BD9"/>
  <rect x="10" y="13" width="15" height="10.5" rx="5.25" fill="#A35BD6"/>
  <polygon points="13,22.5 18.5,22.5 14,27.5" fill="#A35BD6"/>
</svg>
```

(The full `pluginIcon.svg` also has grey code lines; they're dropped here — too
fine at tool window size.) The stripe badges (envelope / red blocker) must
still compose on top of it.

## 5. Out of scope — don't touch

- Whose-turn logic (`Turn`, `CommentThread.turn()/awaitsUser()`),
  `AggregateState` precedence, thread semantics, the HTTP API, the served
  guide's contract.
- The Hand Back toolbar button's own icon (stays grey, with `_dark`).
- Intent/severity **words** in rows and panel headers (they stay).
- `docs/BACKLOG.md` history (append-only record — don't rewrite old entries).

## 6. Docs to update

- `README.md` ~line 47 ("A stripe badge and "N awaiting you"…") — describe
  the new signals (✉ / ✈, the `2 ✉` count, the brand marks).
- `CHANGELOG.md` — add an `## [Unreleased]` section above `[0.1.28]` with a
  `### Changed` entry referencing #25.
- `docs/screenshots.md` references the old look; screenshots will be stale.
  Note it in your final report — don't fabricate new images.

## 7. How to work (the user's standing process)

Read `CLAUDE.md` and `CLAUDE.local.md` first. `CLAUDE.local.md` has the
**mandatory sandbox build setup**: every Gradle call needs
`JAVA_HOME`, `PATH` and `GRADLE_OPTS` exported exactly as in
`~/.zshrc.local`; `./gradlew buildPlugin` builds; `scripts/test-core.sh` is
the only runnable test loop (plugin-module UI has no runnable tests — push
every decision into pure `core/` and test it there; `core/` never imports the
plugin module).

The user's build instructions, verbatim in spirit:
- Use the **TDD workflow** (red → green → refactor); **don't wait for approval
  of the test plan**.
- **Do not commit** (and don't push). The user commits after reviewing.
- After each change set, spawn an **independent review subagent**, fix its
  findings and re-review — **up to 3 round-trips**; then a final review of the
  whole diff from two angles (correctness, code quality).
- **No new comments or doc strings unless absolutely needed.** Treat the urge
  to comment as a code smell pointing at a rename or refactor; a comment is
  the last resort for a genuinely non-obvious platform quirk. Update comments
  your change makes false; don't strip others.
- Keep CI green: Plugin Verifier against the 2025.2 floor (no internal API).
  Local builds may compile against a newer IDE, so floor violations only show
  in CI — prefer long-stable public APIs and say which ones you relied on.

**Dogfooding the margin** (the IDE runs Marginalis on `127.0.0.1:63342`;
RustRover, where the user installs dev builds, is on `:63343`):
- Identity on every call: `author_name="Claude · builder"`,
  `author_id="claude-builder"`, `project=marginalis`.
- Fetch `GET /api/marginalis/agent_guide` and follow it (sweep first; never
  edit a file with open threads not authored by you).
- When done: build a **dev zip** by temporarily changing
  `version = "0.1.28"` in `build.gradle.kts` to `0.1.29-dev1`, running
  `./gradlew buildPlugin`, then restoring with `git checkout build.gradle.kts`.
  Post a **review walkthrough** in the margin (one `comment_add_batch` call,
  walkthrough label `B25`, ordered by code structure, each step anchored with
  exact `anchor_text`) with "To test:" steps the user can do in the IDE.
- The user judges icons **in the running IDE, dark theme first** — expect a
  revision round after they install the zip (uninstall the old version, then
  Install Plugin from Disk; no restart needed).

## 8. Acceptance checklist

- [ ] Gutter: brand-bubble outline marks, intent glyph + hue, file fold on
      line 1, statuses as specified; PR-review balloon no longer lookalike.
- [ ] Tool window rows use the same marks; project threads show the arc.
- [ ] Turn signals: ✉ / ✈ overlays on file icons in tabs and the Project view;
      no characters after file names; icon form in tool window rows.
- [ ] Tool window title shows `N ✉`; stripe badge is the envelope (red wins
      for an open blocker); tool window icon is the mini plugin icon.
- [ ] Action icons (Add Comment, Comment on File, Comment on Project, Reopen)
      use the ordinary marks.
- [ ] Single theme-neutral SVGs, no new `_dark` files; readable in light too.
- [ ] Core tests for any new decision logic; `scripts/test-core.sh` and
      `./gradlew buildPlugin` green; CI guide check still passes.
- [ ] README + CHANGELOG updated; nothing committed; dev zip + B25
      walkthrough posted; a final report listing the platform APIs used, any
      spec point you couldn't meet, and what the user should check first.
