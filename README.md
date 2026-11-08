# Démarrage Auto (ex-Plex Auto)

Mini-appli Android TV / Google TV qui ouvre automatiquement **Plex** ou le **dashboard famille**
(en plein écran, sans barre d'adresse) :

- au **démarrage** de la TV (après une coupure de courant, une mise à jour…), au bout de 8 s ;
- à chaque **sortie de veille** (allumage à la télécommande), au bout de 2 s.

**Entre 17h00 et 19h30**, au lieu d'ouvrir directement la cible, la TV affiche d'abord
un écran stylisé « **Les tâches du jour sont faites ?** » avec les tâches du jour de chaque enfant
(lues dans le Google Sheet « Family tasks »), puis deux boutons : *Voir le dashboard* ou *Plex*.

Testée pour une Philips 55OLED910 (Google TV). Version actuelle : **1.4.2**.

> Pour l'instant le rappel s'affiche **à chaque allumage**, sans horaire (`REMINDER_ALL_DAY = true` dans `Prefs.kt`).
> Pour revenir au créneau 17h – 19h30 : passer cette constante à `false`.

## Comment ça marche

| Fichier | Rôle |
|---|---|
| `BootReceiver.kt` | Réveillé par Android au démarrage de la TV → démarre le service et programme la cible |
| `WatchService.kt` | Service permanent qui écoute « écran rallumé » (sortie de veille) → programme la cible |
| `PlexLauncher.kt` | Ouvre la cible : Plex (version TV en priorité) ou le dashboard |
| `DashboardActivity.kt` | Navigateur intégré plein écran (WebView) qui affiche le dashboard |
| `TaskCheckActivity.kt` | Écran de rappel : charge `baiji.fr/famille/rappel.html` (copie locale si hors ligne), y envoie les tâches du Sheet |
| `assets/rappel.html` | Copie de secours de l'écran de rappel (la vraie est sur le site, dépôt svaug12345.github.io) |
| `EventLog.kt` | Journal des 20 derniers événements, affiché en bas des réglages (diagnostic) |
| `Updater.kt` / `InstallReceiver.kt` | Mise à jour depuis la TV : dernière Release GitHub → téléchargement → installateur Android |
| `Prefs.kt` | Réglages : activé/désactivé, cible, adresse du dashboard, créneau du rappel, délais |
| `MainActivity.kt` | Écran de réglages : état, activer/désactiver, choix de la cible, adresse, test |

Pourquoi un service ? Quand on éteint à la télécommande, la TV passe en **veille** et ne redémarre pas
au rallumage. Le seul signal est « écran rallumé », qu'on ne peut écouter qu'avec un code déjà actif.

## Modifier l'écran de rappel (sans mettre à jour l'appli)

Le texte et le style de l'écran de rappel sont dans **`famille/rappel.html` du site baiji.fr**
(dépôt svaug12345.github.io). Une modification publiée sur le site est visible à l'allumage suivant.
Penser à recopier le fichier dans `app/src/main/assets/rappel.html` à la prochaine version de l'appli.

## Publier une nouvelle version de l'appli

1. Incrémenter `versionCode` (+1) et `versionName` dans `app/build.gradle.kts`, pousser sur `main`.
2. GitHub Actions compile, signe avec la clé fixe et crée la Release `v<versionName>` (fichier `DemarrageAuto.apk`).
3. Sur la TV : Démarrage Auto › **Installer la version X** › confirmer.

Prérequis (une fois) : dépôt **public** (la TV télécharge sans compte GitHub) et deux secrets dans
*Settings › Secrets and variables › Actions* : `KEYSTORE_B64` (la clé, encodée en base64) et
`KEYSTORE_PASSWORD`. La clé n'est **jamais** dans le dépôt. Sans ces secrets, l'APK est signé
avec une clé de debug et n'est pas publié. ⚠️ Garder une copie de la clé : perdue, il faudrait
désinstaller/réinstaller l'appli pour changer de clé.

## 1. Compiler l'APK

**Avec GitHub (recommandé)** : crée un dépôt `svaug12345/plex-auto`, pousse ce dossier sur `main`.
L'onglet **Actions** compile tout seul ; l'APK est dans l'artefact `PlexAuto-apk` du dernier run.

**Avec Android Studio** : ouvre le dossier, puis *Build › Build APK(s)*.
L'APK sort dans `app/build/outputs/apk/debug/`.

## 2. Installer sur la TV (depuis le PC)

Prérequis : mode développeur + débogage réseau activés sur la TV (Paramètres › Système › À propos,
7 clics sur la version de l'OS, puis Options pour les développeurs).

```bash
# Se connecter à la TV (remplace par son IP : Paramètres › Réseau)
adb connect 192.168.1.XX
# → accepter la demande qui s'affiche sur la TV la première fois

# Installer l'APK (-r = remplacer si déjà installé)
adb install -r app-debug.apk

# INDISPENSABLE : autoriser l'appli à ouvrir Plex ou le dashboard depuis l'arrière-plan
adb shell appops set fr.baiji.plexauto SYSTEM_ALERT_WINDOW allow

# v1.4.0 : autoriser l'appli à installer ses propres mises à jour
adb shell appops set fr.baiji.plexauto REQUEST_INSTALL_PACKAGES allow
```

Depuis un téléphone (Bugjaeger) : mêmes commandes, sans `adb shell` devant.
Dernier APK publié : https://github.com/svaug12345/plex-auto/releases/latest/download/DemarrageAuto.apk

## 3. Activer

Ouvre **Démarrage Auto** une fois sur la TV :
1. « Ouvrir plutôt le dashboard famille » pour choisir la cible ;
2. tape l'adresse du dashboard dans le champ (clavier de la TV), valide ;
3. « Tester » ouvre la cible immédiatement.

Ensuite, mets la TV en veille puis rallume-la : la cible doit s'ouvrir seule.

**Dans le dashboard** : Retour = page précédente puis fermeture ; touche Menu = recharger la page.
La page est rechargée à chaque sortie de veille (données fraîches) et réessaie toute seule
toutes les 10 s si le Wi-Fi n'est pas encore prêt au démarrage.

**Rappel des tâches** : activé par défaut, bouton dans les réglages pour le couper.
Sur l'écran de la question, la touche Retour est neutralisée (il faut choisir), et le focus
démarre sur « Voir le dashboard ». Pour changer le créneau : `REMINDER_START_MIN` /
`REMINDER_END_MIN` dans `Prefs.kt`.

⚠️ Si tu utilises Projectivy, **n'active pas** en plus son option « appli au démarrage »,
sinon les deux se marcheront dessus.

## Dépannage

```bash
# Voir ce que fait l'appli en direct
adb logcat -s PlexAuto
```

- Le dashboard reste noir → vérifier l'adresse ; une connexion Google (OAuth) directement dans la page
  est refusée par Google dans une WebView : les données doivent venir d'un Apps Script publié.

- « Écran rallumé » apparaît mais rien ne s'ouvre → la permission `appops` manque
  (elle peut sauter après une réinstallation complète : relancer la commande).
- Rien n'apparaît au rallumage → le service a été tué : ouvrir Démarrage Auto une fois le relance.

## Historique

- **1.4.2** — le journal des événements est accessible à la télécommande (flèche bas après le dernier bouton).
- **1.4.1** — journal des 20 derniers événements (démarrage, veille, rallumage, ouvertures) affiché dans les réglages, pour le diagnostic.
- **1.4.0** — écran de rappel chargé depuis baiji.fr (copie locale de secours) ; bouton discret « Menu TV » ; bouton « Mettre à jour » dans les réglages (Releases GitHub) ; clé de signature fixe (secrets GitHub).
- **1.3.1** — rappel affiché à chaque allumage (sans horaire) ; bouton « Oui, oui, je le jure ! → Plex ».
- **1.3.0** — écran de rappel stylisé (HTML) avec les tâches du jour par enfant ; adresse par défaut `baiji.fr/famille/?tv`.
- **1.2.0** — question « Est-ce que les tâches sont faites ?? » de 17h à 19h30, puis choix Plex / dashboard ; rappel désactivable.
- **1.1.0** — choix de la cible (Plex ou dashboard famille en plein écran), adresse réglable, appli renommée « Démarrage Auto ».
- **1.0.0** — première version : lancement au démarrage et à la sortie de veille, écran de réglages.
