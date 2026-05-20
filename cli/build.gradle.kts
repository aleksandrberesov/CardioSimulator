plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("com.example.cardiosimulator.cli.MainKt")
    applicationName = "cardiosim-test"
}

dependencies {
    implementation(project(":core"))
    implementation(libs.org.json)
    testImplementation(libs.junit)
}

// `jpackage` requires the runtime image and the distribution layout.
// On Windows JDK 21 ships with jpackage; on Linux build inside Linux/WSL.
tasks.register<Exec>("jpackageImage") {
    group = "distribution"
    description = "Build a self-contained native image (app-image) using jpackage."
    dependsOn("installDist")

    val distDir = layout.buildDirectory.dir("install/cardiosim-test").get().asFile
    val outDir = layout.buildDirectory.dir("jpackage").get().asFile

    doFirst {
        outDir.deleteRecursively()
        outDir.mkdirs()
    }

    val javaHome = System.getProperty("java.home")
    val jpackage = if (org.gradle.internal.os.OperatingSystem.current().isWindows) {
        "$javaHome\\bin\\jpackage.exe"
    } else {
        "$javaHome/bin/jpackage"
    }

    commandLine(
        jpackage,
        "--type", "app-image",
        "--name", "cardiosim-test",
        "--input", "$distDir/lib",
        "--main-jar", "cli.jar",
        "--main-class", "com.example.cardiosimulator.cli.MainKt",
        "--dest", outDir.absolutePath,
        "--app-version", "1.0.0",
        "--vendor", "CardioSimulator",
    )
}
