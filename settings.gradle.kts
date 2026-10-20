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
        // mochi-compose 通过 JitPack 发布
        maven("https://jitpack.io")
    }
}

rootProject.name = "WBHub"
include(":app")
