// app/build.gradle.kts — configuration de compilation de l'appli.

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    // "namespace" = paquet Kotlin du code (sert à générer la classe R des ressources)
    namespace = "fr.baiji.plexauto"
    compileSdk = 34 // version d'Android utilisée pour COMPILER (Android 14)

    defaultConfig {
        applicationId = "fr.baiji.plexauto" // identifiant unique de l'appli sur la TV
        minSdk = 26      // Android 8 minimum : largement suffisant pour une Google TV 2025
        targetSdk = 34   // l'appli déclare respecter les règles d'Android 14

        // ⚠️ À incrémenter à chaque mise à jour :
        //   versionCode = entier qui DOIT augmenter pour que la TV accepte la mise à jour
        //   versionName = numéro lisible affiché à l'utilisateur
        versionCode = 4
        versionName = "1.3.0" // 1.3.0 : écran de rappel stylisé avec les tâches du jour
    }

    buildTypes {
        release {
            isMinifyEnabled = false // pas d'obfuscation : l'appli est minuscule
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

// Aucune dépendance externe : on n'utilise que les API Android de base.
// (La bibliothèque standard Kotlin est ajoutée automatiquement par le plugin.)
