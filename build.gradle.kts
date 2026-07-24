import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // Kotlin 2.3.x: first line with official Gradle 9 support (we run Gradle 9.1
    // because the shared JDK is Java 25, which needs Gradle 9.1+).
    id("org.jetbrains.kotlin.jvm") version "2.3.21"
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

group = "dev.marginalis"
version = "0.1.9"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    // The domain core: model, lifecycle, anchoring policy, persistence codec.
    implementation(project(":core"))

    // Markdown parsing for message bodies (markdown-lite rendering scope).
    // The platform bundles the Kotlin stdlib — don't ship a second copy.
    implementation("org.jetbrains:markdown:0.7.7") {
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
    }

    intellijPlatform {
        // Default: compile against the oldest supported target (the honest
        // floor; CI uses this). Environments that can't reach the JetBrains
        // CDN — like the tenant sandbox — set `marginalis.localIde` in their
        // machine-local ~/.gradle/gradle.properties to point at an installed
        // IDE instead (see CLAUDE.md).
        val localIde = providers.gradleProperty("marginalis.localIde").orNull
        if (localIde != null) {
            local(localIde)
        } else {
            intellijIdeaCommunity("2025.2")
        }
    }
}

intellijPlatform {
    // We do have a settings page now, but this forks a headless IDE the
    // sandbox can't run; the one page is findable by name regardless.
    buildSearchableOptions = false

    // Needs java-compiler-ant-tasks from the blocked CDN, and we have no GUI
    // forms or @NotNull Java bytecode to instrument anyway.
    instrumentCode = false

    pluginConfiguration {
        id = "dev.marginalis.plugin"
        name = "Marginalis"
        version = project.version.toString()
        ideaVersion {
            sinceBuild = "252"
            // Omit until-build entirely: the host IDE that installs the zip may
            // be any version ≥ 2025.2, and JetBrains discourages upper bounds.
            untilBuild = provider { null }
        }
    }

    // Marketplace publishing: all four values arrive as CI secrets (see
    // release.yml); locally they're simply absent and the tasks are skipped.
    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }

    pluginVerification {
        // CI runs this against JetBrains' recommended IDE set for our
        // since-build range — the proof behind the declared 2025.2+ support.
        // Fail on real problems; deprecated/experimental/internal *usages*
        // are reported but advisory (and NOT_DYNAMIC is a known, accepted
        // limitation for now).
        failureLevel = listOf(
            org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask.FailureLevel.COMPATIBILITY_PROBLEMS,
            org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask.FailureLevel.MISSING_DEPENDENCIES,
            org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask.FailureLevel.INVALID_PLUGIN,
        )
        ides {
            recommended()
        }
    }
}

tasks.named<org.gradle.api.tasks.JavaExec>("runIde") {
    // Open the toy project so the M0 loop can be exercised immediately.
    args(layout.projectDirectory.dir("sample-project").asFile.absolutePath)
}

// IPGP auto-configures a Java *21* toolchain for the 2025.2 target, but the
// sandbox can't provision one (toolchain downloads are network-blocked). Pin
// the toolchain to the JDK share we actually have (25) and emit 21 bytecode
// via explicit source/target compatibility + jvmTarget.
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
        // Without this, implementing a Kotlin interface generates bridge
        // overrides for every default method — the Plugin Verifier then
        // reports us "overriding" deprecated/internal APIs we never wrote.
        // (-jvm-default=no-compatibility is the stable spelling of the old
        // -Xjvm-default=all.)
        freeCompilerArgs.add("-jvm-default=no-compatibility")
    }
}
