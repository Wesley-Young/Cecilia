import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    jvm()
    jvmToolchain(21)

    sourceSets {
        jvmTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}