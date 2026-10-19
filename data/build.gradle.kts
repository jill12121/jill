plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core:contract"))
    implementation(project(":domain"))
    testImplementation(kotlin("test"))
    testImplementation(libs.konsist)
}
