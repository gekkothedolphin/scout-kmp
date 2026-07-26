plugins {
    kotlin("multiplatform") version "2.0.21"
}

repositories {
    google()
    mavenCentral()
}

kotlin {
    jvm()

    iosSimulatorArm64 {
        binaries {
            executable {
                entryPoint = "main"
            }
        }
    }
}

tasks.register<JavaExec>("jvmRunBenchmark") {
    dependsOn("jvmMainClasses")
    val jvmCompilation = kotlin.targets.getByName("jvm").compilations.getByName("main")
    mainClass.set("MainKt")
    classpath = files(jvmCompilation.output.allOutputs, jvmCompilation.runtimeDependencyFiles)
}
