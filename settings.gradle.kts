pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "GalleryOrganizer"
include(":app")
include(":core:contract")
include(":domain")
include(":data")
include(":presentation")
include(":spikes:s01-saf")
include(":spikes:s02-mediastore")
include(":spikes:s02-fixture-contract")
include(":spikes:s02-fixture-helper")
include(":spikes:s04-manifest")
include(":spikes:probe-suite")
