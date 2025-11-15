plugins {
    kotlin("jvm")
    application
    alias(libs.plugins.composeMultiplatform)
    id("com.gradleup.shadow") version "9.2.2"
}

dependencies {
    implementation(project(":composeApp"))
    // Compose Desktop Native Binaries
    implementation(compose.desktop.windows_x64)
    implementation(compose.desktop.windows_arm64)
    implementation(compose.desktop.macos_x64)
    implementation(compose.desktop.macos_arm64)
    implementation(compose.desktop.linux_x64)
    implementation(compose.desktop.linux_arm64)
}

application {
    mainClass = "org.ntqqrev.cecilia.Cecilia"
}