// settings.gradle.kts — décrit la structure du projet et où Gradle va chercher ses dépendances.

pluginManagement {
    // Dépôts où Gradle télécharge les PLUGINS (le plugin Android, le plugin Kotlin…)
    repositories {
        google()            // plugin Android (AGP) hébergé par Google
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // On interdit aux modules de déclarer leurs propres dépôts : tout est centralisé ici.
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "PlexAuto"

// Le projet ne contient qu'un seul module : l'appli elle-même, dans le dossier "app".
include(":app")
