pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(org.gradle.api.initialization.resolve.RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "todo-devops"

include(
    "core:domain",
    "core:application",
    "adapters:out:persistence-jpa",
    "adapters:in:web-rest",
    "apps:web-app"
)