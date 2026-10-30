# Jarvis iPod — indicazioni per continuare lo sviluppo

## Obiettivo del proprietario

App Android nativa, installabile su Google Pixel 9 Pro XL e adattabile ad altri smartphone. Deve mantenere l’aspetto di un iPod Classic con scocca metallica, display incassato e vera Click Wheel circolare. Priorità alla fedeltà del riferimento fornito dal proprietario, alla musica locale funzionante e all’accesso ufficiale a Google Drive. Conservare i temi Silver Classic e Midnight Black e la navigazione sia tramite ruota sia tramite touch.

Le proporzioni misurate del riferimento sono documentate in `docs/DESIGN.md`. L’immagine originale era allegata alla conversazione e non è disponibile nei file di questo repository: prima di confronti visivi puntuali, chiedere al proprietario di fornirla nuovamente. Non presentare disegni o screenshot di emulatori non renderizzati come prova della fedeltà grafica.

## Leggere prima di modificare

1. `README.md`: installazione, build, OAuth Google, firma, comportamento e limiti di Spotify.
2. `docs/VERIFICA.md`: prove effettivamente eseguite e funzionalità ancora da verificare.
3. `docs/DESIGN.md` e `docs/DIPENDENZE.md`.

Il progetto è Kotlin / Jetpack Compose con singolo modulo `app`, package `it.jarvis.ipod`, minSdk 26 e compile/targetSdk 35. Versioni fissate: Gradle 8.9, AGP 8.7.3, Kotlin 1.9.25, Media3 1.5.1 e Room 2.6.1. Non aggiornare tutte le dipendenze insieme senza una ragione e senza verificare la compatibilità.

## Mappa del codice

Percorsi relativi ad `app/src/main/java/it/jarvis/ipod/`:

| File | Responsabilità |
|---|---|
| `MainActivity.kt` | Permessi audio, risoluzione autorizzazione Google, ingresso Compose e barre di sistema |
| `JarvisApp.kt` | Dipendenze condivise, Room, DataStore, cache e scope applicativo per persistenza |
| `PlayerViewModel.kt` | Stato Flow, navigazione e comandi verso MediaController |
| `ui/Hardware.kt` | Scocca e ruota Canvas, texture metallica, gesture, aptica e fallback copertina |
| `ui/WheelMath.kt` | Accumulatore angolare puro e geometria della ruota |
| `ui/JarvisUI.kt` | Menu, libreria, lettore, coda, ricerca, playlist e impostazioni |
| `data/Library.kt` | Room e scansione MediaStore; metadati, preferiti, recenti e playlist |
| `data/Preferences.kt` | DataStore per preferenze e ripristino della riproduzione |
| `playback/PlaybackService.kt` | ExoPlayer, MediaSession, audio focus, notifica, risoluzione Drive e retry |
| `drive/DriveAuth.kt` | AuthorizationClient ufficiale e token soltanto in memoria |
| `drive/DriveRepository.kt` | Drive API v3, cartelle, ricerca e download offline autorizzati |

## Compilare e verificare

Usare JDK 17, Android SDK Platform 35 e Build Tools 35.0.0. Impostare `ANDROID_HOME` oppure `sdk.dir` in un `local.properties` locale escluso da Git.

```bash
./scripts/create-debug-key.sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:lintDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

Con telefono/emulatore sbloccato, autorizzato in ADB:

```bash
./gradlew :app:connectedDebugAndroidTest
```

I test JVM verificano la matematica della ruota. I test strumentali verificano gesto circolare, selezione, tema e persistenza, MediaStore con WAV sintetico, conservazione dei preferiti, avanzamento del player, evento reale `ON_STOP`, audio in background, snapshot della coda e file Drive già offline.

La consegna iniziale del 28 ottobre 2026 ha superato build, 6 test JVM, Lint (0 errori, 20 avvisi) e 2 test strumentali su Android 11 AOSP ATD. I log in `docs/reports/` sono prove storiche della consegna iniziale, non di modifiche future. Non dichiarare test eseguiti se non sono stati lanciati nell’ambiente corrente. Gli emulatori ATD disabilitano il rendering hardware: non bastano per valutare il design.

## Comportamenti delicati da preservare

- Non bloccare il thread principale con `runBlocking` in `PlaybackService.onDestroy`: ha già causato ANR. Acquisire lo stato del player sul main thread e delegare la scrittura allo scope applicativo su IO.
- Per `drive://`, il resolver deve precedere la scelta della sorgente: `ResolvingDataSource(DefaultDataSource(...), resolver)`. Deve poter risolvere sia HTTPS con bearer sia file privati offline.
- La scansione aggiorna i metadati senza sovrascrivere preferiti, recenti o percorso offline. Gli ID locali includono volume e ID MediaStore. Le rimozioni eliminano anche i riferimenti playlist, in blocchi compatibili con i limiti SQLite.
- Il ripristino di coda/posizione avviene in pausa.
- La texture metallica è generata una volta e riutilizzata: evitare migliaia di tratti Canvas a ogni ridisegno.
- La ruota distingue tap, drag, centro e pressione prolungata; un drag/hold non deve attivare un tap al rilascio. Non deformarla su schermi diversi.
- Richiedere solo `READ_MEDIA_AUDIO` da Android 13, `READ_EXTERNAL_STORAGE` limitato alle versioni precedenti. Non introdurre accesso completo ai file.

## Google Drive

È implementato con Google Identity AuthorizationClient e Drive API v3, scope `drive.readonly`, necessario per esplorare file esistenti e leggerne i contenuti. La configurazione richiede un client OAuth Android associato a package e SHA-1 della firma, non un client secret nell’app. Le istruzioni sono nel README.

Accesso reale, consenso, cartelle remote, streaming e download con un account Google non sono stati verificati. Il test `drive://` offline non prova OAuth o rete. I metadati cloud attuali derivano dal nome del file e dalla cartella; non vengono estratti ID3/copertine remote. I download richiedono il processo attivo e non sono job WorkManager persistenti.

### Drive con chiave API (nov 2026)

`DriveRepository.prepare()` sceglie OAuth (se `driveEmail` è impostata) oppure chiave API (`Preferences.apiKey`, query `key=`) più header `X-Goog-Drive-Resource-Keys`. Le cartelle condivise sono salvate come JSON in `Preferences.folders` (`drive/DriveLinks.kt`, con test JVM). Nello streaming il resolver usa `DriveRepository.streamRequest()`. La chiave non va mai committata né loggata. Non verificato con un Drive reale; i resource key dei file figli sono tenuti solo in memoria.

## Spotify

Si apre l’app ufficiale o una Custom Tab del browser. Login e ascolto con un account reale non sono stati verificati. Non esiste riproduzione WebView dichiarata funzionante, non è incluso App Remote e la ruota non controlla Spotify. Conservare MENU per uscire dalla sezione e usare solo integrazioni ufficiali; nessuno scraping o aggiramento DRM/autenticazione.

## Firma e distribuzione

`dist/Jarvis-iPod-debug.apk` è l’APK iniziale firmato e verificato; `dist/SHA256SUMS` ne consente il controllo. La chiave privata non è nel repository né nello ZIP. `create-debug-key.sh` genera una chiave locale se manca: una nuova chiave produce uno SHA-1 diverso e non permette di aggiornare l’APK già installato con altra firma. Registrare il nuovo SHA-1 nel client OAuth; conservare separatamente la chiave originale se servono aggiornamenti con la stessa firma.

Il workflow `.github/workflows/android.yml` genera una chiave debug sul runner; non garantisce una firma stabile tra esecuzioni. La release richiede le variabili `JARVIS_KEYSTORE`, `JARVIS_STORE_PASSWORD`, `JARVIS_KEY_ALIAS` e `JARVIS_KEY_PASSWORD`, come descritto nel README. Non inserire keystore, token, password o credenziali nel repository.

`dist/` è una copia della consegna, non l’output automatico di ogni build. Dopo modifiche e verifiche, aggiornare deliberatamente APK, archivio sorgenti e checksum insieme e documentare quali test sono stati eseguiti. Evitare archivi ricorsivi: lo ZIP dei sorgenti esclude `.git/`, `.gradle/`, `.kotlin/`, `build/`, `dist/`, `signing/` e file locali/credenziali.

## Verifiche ancora necessarie

Pixel fisico: fedeltà visiva al riferimento, fluidità, aptica, Bluetooth, schermata di blocco, chiamate e gestione energetica. Account reali: OAuth/streaming/rete Drive e login/ascolto Spotify. Mantenere questi limiti espliciti finché non vengono verificati.
