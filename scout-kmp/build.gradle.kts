// Copyright 2026 gekkothedolphin@gmail.com
//
// Licensed under the Apache License, Version 2.0 (see LICENSE at repo root).
//
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
