rootProject.name = "scout-kmp"

pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

// Modules are added incrementally, one per migration phase.
// See .ai/migration_plan/ for the phase plan and .ai/README.md for the
// "Physical code location" decision (this project is fully standalone,
// independent of the frozen ../scout/ Gradle build).
include(":core")
