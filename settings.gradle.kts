pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
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

rootProject.name = "MyHealth"
include(":app")

// ✅ JDK 툴체인 자동 다운로드 저장소 등록 (툴체인 오류 해결 포인트)
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
