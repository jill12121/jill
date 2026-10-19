plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core:contract"))
    testImplementation(kotlin("test"))
    testImplementation(libs.konsist)
}
