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
        // sherpa-onnx (offline TTS) is only published as an AAR on JitPack; keep JitPack scoped to com.github.* groups
        maven("https://jitpack.io") { content { includeGroupByRegex("com\\.github\\..*") } }
    }
}
rootProject.name = "Mika"
include(":core", ":app")
