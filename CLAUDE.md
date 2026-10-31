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

L’elenco delle cartelle musicali consentite è salvato in DataStore (`drive_folders`) ed è una restrizione lato app: il token resta `drive.readonly` sull’intero account. Il menu Drive parte da quell’elenco; `drivepick` sfoglia Drive solo per sceglierne di nuove (senza salvare brani) e la ricerca è limitata alla cartella aperta. La modifica compila e supera test JVM e Lint su GitHub Actions (ottobre 2026), ma non è stata provata su telefono né con un account Google.

Accesso reale, consenso, cartelle remote, streaming e download con un account Google non sono stati verificati. Il test `drive://` offline non prova OAuth o rete. I metadati cloud attuali derivano dal nome del file e dalla cartella; non vengono estratti ID3/copertine remote. I download richiedono il processo attivo e non sono job WorkManager persistenti.

## Spotify

Dalla 1.1 è integrato con l’SDK ufficiale App Remote 0.8.0 (AAR in `app/libs/`, più Gson): `spotify/SpotifyBridge.kt` (connessione, `PlayerState`, copertina via ImagesApi, comandi, spiegazione degli errori), `spotify/SpotifyLinks.kt` (parsing di link/URI, con test JVM) e le route `spotify` e `spotifyplaying` in `ui/JarvisUI.kt`. Il Client ID si inserisce in Impostazioni app (DataStore `spotify_client_id`); redirect URI `jarvisipod://spotify-callback`; la guida in-app mostra package e SHA-1 reali della firma. L’audio resta nell’app Spotify; la connessione usa il contesto dell’Activity e viene chiusa con la UI (Spotify continua a suonare). La ruota e i tasti controllano Spotify solo nella route `spotifyplaying`.

Compilazione in CI superata, ma **connessione, autorizzazione, comandi e avvio da link con un account reale non sono stati verificati** (serve Premium per i brani singoli e la registrazione nel Dashboard con SHA-1 della firma installata, che con la chiave debug del workflow cambia a ogni build). Solo integrazioni ufficiali; nessuno scraping o aggiramento DRM/autenticazione. MENU esce dalla sezione.

## Grafica, comandi e vibrazione (1.1)

Nuova interfaccia (`ui/Screens.kt`, `ui/Hardware.kt`): schermata iniziale con ora, menu a pillola blu-viola con miniature, griglie album e libreria, lettore con barra trascinabile. `Settings.controlMode` = `wheel` (ruota e tasti) o `buttons` (rotazione disattivata; MENU in alto e il tasto in basso spostano la selezione); `playerWheel` = `track` (ogni 3 scatti cambia brano) o `seek` (5 s a scatto). `ui/Haptics.kt` usa gli effetti predefiniti TICK/CLICK/HEAVY_CLICK (API 29+) con livelli 0–3 e, da Android 13, `USAGE_TOUCH` (segue «Feedback tattile» di sistema). Compilato in CI; **non visto né provato su telefono**. Il riferimento grafico originale non è nei file: non presentare questa resa come fedele finché non è confrontata con l’immagine.

Aggiunte successive alla 1.1 (compilate in CI, non viste su telefono):
- `ui/ShellPalette.kt` (generato) contiene i colori grafite campionati dal riferimento: `GraphiteGrid` 18×30 e `GraphiteRim` per il bordo. Midnight Black è il tema predefinito e usa questa tavolozza; Silver Classic resta com'era. `Hardware.kt` la disegna una volta sola come bitmap.
- L'alone di ruota e tasti usa `GlowOptions` (`glow` in DataStore, blu `0xFF45B0FF` di base, «Spento» = nessun alone); si cambia in Impostazioni > Colore alone.
- La scocca occupa tutta l'altezza disponibile (larghezza = altezza / 1.6, limitata dallo schermo). `IPodShell` forza `fontScale = 1` per evitare testo tagliato (barra del mini-player) con caratteri grandi.
- Tasti (in `JarvisUI.onButton`): MENU in alto = su e tasto in basso = giù nelle liste (in modalità ruota e solo tasti); fuori dalle liste MENU = indietro e il tasto in basso = play/pausa. MENU tenuto premuto (`Hardware.kt`, una sola volta) = indietro. Centro: su un brano lo avvia o, se è già in ascolto, play/pausa; sul lettore play/pausa; sulle altre voci le apre. ◀◀/▶▶ = brano precedente/successivo, tenuti premuti ±5 s. La libreria ha anche Google Drive e Spotify.
- Drive senza account: `Settings.driveApiKey` (DataStore `drive_api_key`, nel backup). `DriveRepository.access()` usa il token se l'account è collegato, altrimenti la chiave API con `X-Android-Package`/`X-Android-Cert`; lo stesso vale per streaming (`PlaybackService`) e download. Nel menu Drive (`"drive"` root in `JarvisUI.kt`) i pulsanti «API di Google Drive» e «Cartella condivisa» sono sempre in cima; le cartelle salvate restano in `drive_folders`. `drive/DriveLinks.kt` ricava l'ID dal link; `PlayerViewModel.addDriveFolderFromLink` verifica che sia una cartella raggiungibile. Con la sola chiave si leggono solo cartelle condivise con link. Non provato con una chiave né un account reali; nella cronologia git non esisteva una versione precedente di questa funzione, è stata ricostruita dalla descrizione dell'utente.
- `data/Backup.kt` serializza Client ID Spotify, cartelle Drive e preferenze in JSON (senza token, email e URI del backup). `PlayerViewModel.keepBackupUpToDate` riscrive il file scelto (permesso persistente SAF) 1,5 s dopo ogni modifica; Impostazioni > Backup impostazioni salva/ripristina. Reinstallare con un'altra firma cancella comunque DataStore: serve ripristinare dal file.

L'icona adattiva (`res/mipmap-anydpi`, `drawable-nodpi/ic_launcher_*`) è ricavata dall’immagine fornita, con il contenuto entro la zona sicura di 66 dp.

## Firma e distribuzione

`dist/Jarvis-iPod-debug.apk` è l’APK iniziale firmato e verificato; `dist/SHA256SUMS` ne consente il controllo. La chiave privata non è nel repository né nello ZIP. `create-debug-key.sh` genera una chiave locale se manca: una nuova chiave produce uno SHA-1 diverso e non permette di aggiornare l’APK già installato con altra firma. Registrare il nuovo SHA-1 nel client OAuth; conservare separatamente la chiave originale se servono aggiornamenti con la stessa firma.

Il workflow `.github/workflows/android.yml` genera una chiave debug sul runner; non garantisce una firma stabile tra esecuzioni. La release richiede le variabili `JARVIS_KEYSTORE`, `JARVIS_STORE_PASSWORD`, `JARVIS_KEY_ALIAS` e `JARVIS_KEY_PASSWORD`, come descritto nel README. Non inserire keystore, token, password o credenziali nel repository.

`dist/` è una copia della consegna, non l’output automatico di ogni build. Dopo modifiche e verifiche, aggiornare deliberatamente APK, archivio sorgenti e checksum insieme e documentare quali test sono stati eseguiti. Evitare archivi ricorsivi: lo ZIP dei sorgenti esclude `.git/`, `.gradle/`, `.kotlin/`, `build/`, `dist/`, `signing/` e file locali/credenziali.

## Verifiche ancora necessarie

Pixel fisico: fedeltà visiva al riferimento, fluidità, aptica, Bluetooth, schermata di blocco, chiamate e gestione energetica. Account reali: OAuth/streaming/rete Drive e login/ascolto Spotify. Mantenere questi limiti espliciti finché non vengono verificati.
