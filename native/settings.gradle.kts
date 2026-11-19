pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "AITravelAssistant"
include(":app", ":core:domain", ":core:data", ":core:ui", ":feature:home", ":feature:planner", ":feature:trips", ":feature:tools", ":feature:settings")
