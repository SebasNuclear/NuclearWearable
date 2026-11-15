import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.0.21"
    id("org.jetbrains.compose") version "1.7.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
}

group = "com.nuclear.soundconnect"
version = "1.1.0"

repositories {
    mavenCentral()
    google()
}

val targetOs = (findProperty("targetOs") as String?) ?: "current"

dependencies {
    val desktopRuntime = when (targetOs) {
        "linux_x64" -> compose.desktop.linux_x64
        "linux_arm64" -> compose.desktop.linux_arm64
        else -> compose.desktop.currentOs
    }
    implementation(desktopRuntime)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
    implementation("net.java.dev.jna:jna:5.14.0")
    implementation("net.java.dev.jna:jna-platform:5.14.0")
    implementation("org.json:json:20240303")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.0.21")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}

kotlin {
    jvmToolchain(17)
}

compose.desktop {
    application {
        mainClass = "com.nuclear.soundconnect.MainKt"
        jvmArgs += listOf(
            "-Djava.awt.headless=false",
            "-Dapple.awt.application.name=NuclearWearable"
        )
        nativeDistributions {
            targetFormats(
                TargetFormat.Deb,
                TargetFormat.Rpm,
                TargetFormat.AppImage
            )
            packageName = "NuclearWearable"
            packageVersion = "1.1.0"
            description = "NuclearWearable for Linux Desktop - Sony SoundConnect Controller v1.1.0"
            vendor = "Nuclear Ecosystem"

            modules(
                "java.desktop",
                "java.logging",
                "jdk.unsupported"
            )

            linux {
                packageName = "nuclearwearable"
                iconFile.set(project.file("src/main/resources/drawable/ic_app_icon.png"))
                debMaintainer = "Nuclear Ecosystem <local@nuclear.soundconnect>"
                menuGroup = "AudioVideo;Audio;"
                appCategory = "AudioVideo"
                shortcut = true
            }
        }
    }
}
