/*
 * Copyright 2026 gekkothedolphin@gmail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
