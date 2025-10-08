pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

// In settings.gradle

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // This line is ESSENTIAL for finding the tesseract4android library
        maven { url = uri("https://jitpack.io") }
    }
}


rootProject.name = "SirimScanner"
include(":app")
