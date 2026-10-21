# Upgrading what the build depends on

Every tool and library the build uses has a pinned version: a commit verifies, tests and releases the same way on
any day. Nothing resolves "latest" while CI runs. A dynamic version makes the verdict depend on the day instead of
the commit: an undeclared Plugin Verifier, for one, is looked up in JetBrains' repository on every run, and a hiccup
there fails `verify`.

So a newer version is a deliberate bump: one item per pull request, with CI as the proof. This page lists every
pin, where it lives, how to see that a newer one exists, and what has to stay green.

## The pins

| What | Where | A newer one shows up | Proof it works |
|---|---|---|---|
| **Latest IntelliJ IDEA**: the Plugin Verifier's second IDE | `gradle.properties`: `marginalis.latestIde` | the weekly `latest-ide.yml` opens an issue with the line filled in | `verify` |
| **Platform floor** (2025.2, `since-build` 252): compile, platform tests | `build.gradle.kts`: `intellijIdeaCommunity("2025.2")`, `plugin.xml` | a product decision, not a routine bump | `build`, `verify` |
| **Kotlin API and language version** (2.2, the floor's stdlib) | `build.gradle.kts`: `compilerOptions.apiVersion` / `languageVersion` | moves with the floor: the stdlib of the oldest supported IDE | `build`, `verify` |
| **Plugin Verifier** | `build.gradle.kts`: `pluginVerifier("…")` | `gh release view --repo JetBrains/intellij-plugin-verifier` | `verify` |
| **Marketplace zip signer** | `build.gradle.kts`: `zipSigner("…")` | `gh release view --repo JetBrains/marketplace-zip-signer` | a `v*` release (`release.yml`) signs with it |
| **IntelliJ Platform Gradle Plugin** | `build.gradle.kts`, `plugins {}` | the build prints "IntelliJ Platform Gradle Plugin is outdated" | the whole CI run; the checked IDE index may need a refresh (`ci.yml`) |
| **IDE Starter** (the `uiTest` framework) | `build.gradle.kts`: `ideStarterVersion` | it follows the IDE builds; take the one matching `latestIde` | `./gradlew uiTest` |
| **Kotlin, Kover, Changelog, PIT** (Gradle plugins) | `build.gradle.kts`, `plugins {}` | the Gradle plugin portal | the whole CI run |
| **Gradle** | `gradle/wrapper/gradle-wrapper.properties` (with its checksum); bump with `./gradlew wrapper --gradle-version <v> --gradle-distribution-sha256-sum <sum>` | the build's "incompatible with Gradle N" notice | the whole CI run |
| **JDK** (25) | `.github/workflows/*.yml`: `setup-java` `java-version` | Temurin releases | the whole CI run |
| **CI runner image** (`ubuntu-24.04`) | `.github/workflows/*.yml`: `runs-on` | GitHub moves `ubuntu-latest` to a newer Ubuntu | the whole CI run |
| **GitHub Actions** (`actions/*`, `gradle/actions`, `softprops/action-gh-release`) | `.github/workflows/*.yml`: `uses: …@vN` | GitHub's deprecation notices in the run's annotations | the whole CI run, a release for the release actions |
| **Libraries** (JUnit, ArchUnit, Kodein, coroutines, …) | `build.gradle.kts` and `core/build.gradle.kts`, `dependencies {}` | the build's own warnings, or a need | `build` |

**Still dynamic:** `recommended()` in `pluginVerification.ides`, the floor's IDE set. The Gradle plugin asks
JetBrains which IDEs to verify the `since-build` range against on every run.

## How to bump

1. One pin per pull request, so a red run names its cause.
2. Change the version where the table says, and anything that must move with it.
3. `./gradlew dependencies --configuration <configuration>` shows what resolves, where that helps.
4. Open the PR: its CI is the proof, with the checks in the table green. A bump often brings new deprecation notices
   in the run's annotations; each one is worth an issue.
