plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

android {
    namespace = "com.galleryorganizer.data"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    sourceSets["test"].assets.srcDir("$projectDir/schemas")
}

room {
    schemaDirectory("$projectDir/schemas")
}

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    val robolectricHome = layout.buildDirectory.dir("robolectric-home").get().asFile
    doFirst {
        robolectricHome.mkdirs()
    }
    systemProperty(
        "user.home",
        robolectricHome.absolutePath,
    )
}

dependencies {
    implementation(project(":core:contract"))
    implementation(project(":domain"))
    implementation(libs.room.runtime)
    ksp(libs.room.compiler)
    testImplementation(kotlin("test"))
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.junit4)
    testImplementation(libs.konsist)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.room.testing)
}
