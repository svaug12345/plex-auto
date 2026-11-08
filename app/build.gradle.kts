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
        versionCode = 9
        versionName = "1.4.3" // 1.4.3 : rappel remis devant si le menu Google TV le recouvre au démarrage
    }

    // v1.4.0 : clé de signature FIXE. Android n'accepte une mise à jour que si elle est signée
    // avec la même clé que la version installée ; avant, chaque build GitHub avait une clé
    // différente, d'où les « installation refusée ».
    // La clé n'est JAMAIS dans le dépôt : le workflow GitHub la recrée à partir du secret
    // KEYSTORE_B64, l'écrit dans un fichier temporaire et donne son chemin (KEYSTORE_FILE)
    // et son mot de passe (secret KEYSTORE_PASSWORD) via des variables d'environnement.
    val keystoreFile: String? = System.getenv("KEYSTORE_FILE")
    val keystorePassword: String? = System.getenv("KEYSTORE_PASSWORD")
    val hasFixedKey = !keystoreFile.isNullOrBlank() && !keystorePassword.isNullOrBlank()

    signingConfigs {
        if (hasFixedKey) {
            create("fixe") {
                storeFile = file(keystoreFile!!)
                storePassword = keystorePassword
                keyAlias = "plexauto"
                keyPassword = keystorePassword
            }
        }
    }

    buildTypes {
        getByName("debug") {
            // Sans la clé (ex. build dans Android Studio), on garde la clé de debug habituelle
            if (hasFixedKey) signingConfig = signingConfigs.getByName("fixe")
        }
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
