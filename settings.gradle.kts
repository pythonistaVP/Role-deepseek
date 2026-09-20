// Корневой settings-файл проекта "Role DeepSeek".
// Здесь подключаются репозитории (включая JitPack — он нужен для библиотеки обрезки uCrop)
// и объявляются модули сборки.
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
        // uCrop (обрезка аватара) живёт только на JitPack
        maven(url = "https://jitpack.io")
    }
}

rootProject.name = "Role-DeepSeek"
include(":app")
