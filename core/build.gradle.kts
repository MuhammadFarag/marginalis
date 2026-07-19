// The domain core: pure Kotlin, zero IntelliJ Platform imports. Everything
// protocol-shaped lives here — thread lifecycle, turn state, anchoring
// policy, persistence codec — so it can be unit-tested without an IDE and
// reused beyond this plugin. The dependency rule: core imports nothing from
// the plugin module, ever.
plugins {
    id("org.jetbrains.kotlin.jvm")
}

repositories {
    mavenCentral()
}

dependencies {
    // Provided by the IntelliJ Platform at runtime (root gradle.properties
    // disables the default stdlib dependency for the same reason).
    compileOnly(kotlin("stdlib"))
    compileOnly("com.google.code.gson:gson:2.11.0")

    testImplementation(kotlin("stdlib"))
    testImplementation(kotlin("test"))
    testImplementation("com.google.code.gson:gson:2.11.0")
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.test {
    useJUnitPlatform()
}
