// Copyright 2026 gekkothedolphin@gmail.com
//
// Licensed under the Apache License, Version 2.0 (see LICENSE at repo root).
//
// This is a bootstrap-only build script: it establishes the jvm + iOS target set and
// Kotlin version for scout-kmp/core so the standalone project builds end-to-end before
// any real source code is copied over. The actual Scout core logic (Scope, Component,
// Definition, InstanceFactory, etc.) is migrated from ../../scout/scout/core in the
// next Phase 2 step -- see .ai/migration_plan/phase_2_refactoring_shared.md.

plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm()

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        val commonMain by getting
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
