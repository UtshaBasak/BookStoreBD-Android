pluginManagement {
    includeBuild("build-logic")
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
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "BookStoreBD"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")
include(":core:common")
include(":core:data")
include(":core:database")
include(":core:datastore")
include(":core:designsystem")
include(":core:model")
include(":core:network")
include(":core:testing")
include(":core:ui")
include(":feature:home")
include(":feature:catalogue")
include(":feature:book")
include(":feature:cart")
include(":feature:shop")
include(":feature:wishlist")
include(":feature:checkout")
include(":feature:auth")
include(":feature:settings")
include(":feature:profile")
include(":feature:orders")
include(":feature:reviews")
include(":feature:wanted")
include(":feature:notifications")
include(":feature:chat")
include(":feature:selling")
