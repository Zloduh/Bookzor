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

rootProject.name = "Bookzor"

include(":app")
include(":core:common")
include(":core:scraper")
include(":core:torrent")
include(":core:player")
include(":core:database")
include(":feature:catalog")
include(":feature:detail")
include(":feature:player")
include(":feature:library")
