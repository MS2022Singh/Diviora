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
rootProject.name = "diviora-music"
// Desktop-only CI runners can skip the Android module: -Pdiviora.skipAndroid=true
if (providers.gradleProperty("diviora.skipAndroid").orNull != "true") include(":app")
include(":core:model")
include(":core:catalog")
include(":scheduler:recurrence")
include(":desktop")
