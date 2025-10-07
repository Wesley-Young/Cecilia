plugins {
    kotlin("jvm")
    application
    id("com.gradleup.shadow") version "9.2.2"
}

dependencies {
    implementation(project(":composeApp"))
}

application {
    mainClass = "org.ntqqrev.cecilia.Cecilia"
}