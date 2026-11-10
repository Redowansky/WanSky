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
        // NewPipeExtractor (native YouTube player, v0.2.8) lives on JitPack.
        maven { url = uri("https://jitpack.io") }
    }
}
rootProject.name = "Wansky"
include(":app")
