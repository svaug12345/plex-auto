// build.gradle.kts (racine) — déclare les versions des plugins, sans les appliquer ici.
// Chaque module (ici seulement "app") les applique ensuite dans son propre build.gradle.kts.

plugins {
    id("com.android.application") version "8.7.3" apply false   // outil de build Android (AGP)
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false // compilateur Kotlin pour Android
}
