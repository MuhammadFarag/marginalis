import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // Kotlin 2.3.x: first line with official Gradle 9 support (we run Gradle 9.1
    // because the shared JDK is Java 25, which needs Gradle 9.1+).
    id("org.jetbrains.kotlin.jvm") version "2.3.21"
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

group = "dev.marginalis"
version = "0.1.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
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
    // No settings UI yet; skips a slow headless-IDE fork during build.
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

    pluginVerification {
        // CI runs this against JetBrains' recommended IDE set for our
        // since-build range — the proof behind the declared 2025.2+ support.
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
    }
}
