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

// Root build script for the standalone scout-kmp/ Gradle project. This project is
// fully independent of ../scout/'s Gradle build -- see .ai/README.md
// ("Physical code location" decision). Do not add a cross-project Gradle dependency
// on ../scout/; code is copied and adapted per phase instead.

plugins {
    kotlin("multiplatform") version "2.4.10" apply false
}

allprojects {
    group = "io.github.gekkothedolphin"
    version = "0.1.0-SNAPSHOT"
}
