# Jarvis iPod

App Android nativa in Kotlin e Jetpack Compose. Interfaccia ispirata **all’immagine fornita**: scocca satinata, display incassato e Click Wheel, nelle varianti Silver Classic e Midnight Black. Nessuna immagine Apple o copertina commerciale è inclusa nell’app: lo sfondo iniziale è disegnato proceduralmente.

## File e continuazione dello sviluppo

- [APK debug installabile](dist/Jarvis-iPod-debug.apk) — su GitHub apri il file e scegli **Download raw file**.
- [Archivio dei sorgenti](dist/Jarvis-iPod-sorgenti.zip).
- [Checksum SHA-256 degli allegati](dist/SHA256SUMS).
- [Indicazioni per Claude](CLAUDE.md): architettura, comandi, vincoli di design e verifiche ancora necessarie.
- [Risultati delle verifiche eseguite](docs/VERIFICA.md).

Per modificare l’app usa i file sorgente del repository. `dist/` conserva la consegna verificata; una nuova compilazione produce il proprio APK in `app/build/outputs/apk/debug/`. Il workflow GitHub Actions compila, esegue i test JVM e Lint e conserva l’APK tra gli artifact della singola esecuzione. I test su dispositivo richiedono un telefono o un emulatore e non vengono eseguiti dal workflow corrente.

Il workflow manuale **Consegna APK** pubblica una prerelease con i file già presenti in `dist/`, dopo averne verificato i checksum. Non ricompila l’app. Prima di nuove consegne aggiornare deliberatamente gli allegati e usare un nuovo tag; una release esistente non viene sovrascritta.

## Installazione sul Pixel

1. Trasferisci `Jarvis-iPod-debug.apk` sul telefono.
2. Aprilo da Files e abilita, se richiesto, «Installa app sconosciute» per Files.
3. Installa e apri **Jarvis iPod**. Android minimo: 8.0 / API 26.
4. Seleziona **Musica locale** e autorizza l’accesso ai file audio. Su Android 13+ viene richiesto solo `READ_MEDIA_AUDIO`; sui sistemi precedenti `READ_EXTERNAL_STORAGE`, limitato ad Android 12L.
5. Per Drive completa prima la configurazione OAuth descritta sotto. Spotify si apre nell’app ufficiale o nel browser.

Con USB e debug USB attivo:

```bash
adb install -r dist/Jarvis-iPod-debug.apk
```

L’APK debug è firmato per sviluppo ed è installabile. Per aggiornare senza perdere dati occorre conservare la stessa chiave. La firma debug **non va usata per pubblicare su Play Store**.

## Comandi

| Comando | Azione |
|---|---|
| Giro orario / antiorario | Sposta la selezione nei menu |
| Centro | Conferma; nel lettore apre le opzioni del brano |
| MENU | Torna alla schermata precedente |
| Sinistra / destra | Brano precedente / successivo |
| Pressione prolungata laterale | Cerca nel brano a passi di 5 secondi |
| Play / pausa | Controlla il lettore locale e Drive |
| Ruota nel lettore | Cerca nel brano a passi di 5 secondi |
| Tocca display | Seleziona direttamente; ⋮ apre le opzioni |

In **Impostazioni → Aspetto iPod** il colore cambia immediatamente. Tema, sensibilità, feedback e ordinamento vengono salvati con DataStore. La ripresa della coda avviene **in pausa**, per evitare audio inatteso.

Le playlist possono contenere file locali e Drive. Le opzioni dei brani permettono di gestire preferiti, coda, playlist e download. Nella coda, ↑ sposta un brano verso l’inizio e × lo rimuove. I brani originali non vengono modificati.

## Configurazione Google Drive, senza segreti nell’app

Questa app usa **Google Identity Services `AuthorizationClient`** e Drive REST API v3. La registrazione OAuth Android è associata a **package e certificato di firma**: non occorrono un client secret, un backend, una API key o `google-services.json`.

1. Apri [Google Cloud Console](https://console.cloud.google.com/) e crea/seleziona un progetto.
2. In **API e servizi → Libreria**, abilita **Google Drive API**.
3. In **Google Auth Platform** configura Branding, Audience e Data Access (nelle interfacce precedenti: schermata di consenso OAuth). Per uso personale scegli pubblico esterno e modalità **Testing**, aggiungendo il tuo indirizzo tra i test user. Completa i campi obbligatori del progetto.
4. Aggiungi lo scope `https://www.googleapis.com/auth/drive.readonly` ai permessi richiesti. L’app non richiede accesso in scrittura.
5. In **Client → Crea client → Android**, inserisci:
   - nome a scelta, ad esempio «Jarvis Pixel debug»;
   - package **`it.jarvis.ipod`**;
   - SHA-1 della firma dell’APK che installerai.
6. Per **l’APK debug consegnato**, lo SHA-1 è:

   ```text
   09:5A:82:51:D3:7F:98:79:FA:D5:9C:D4:C1:9D:59:E2:EE:EC:3D:BC
   ```

7. Attendi la propagazione della configurazione; sul telefono con Google Play Services aggiornati apri **Google Drive → Collega Google Drive** e scegli l’account test autorizzato.
8. Naviga nelle cartelle e seleziona un file audio. La coda comprende i file audio della cartella aperta (non attraversa ricorsivamente le sottocartelle). La ricerca Drive cerca per nome in tutti i file audio e le cartelle accessibili nell’unità dell’utente.

Se ricompili con una chiave diversa devi registrare il nuovo SHA-1, ottenibile con:

```bash
./gradlew signingReport
```

Se Google mostra `DEVELOPER_ERROR` / codice 10, verifica che package, SHA-1, progetto OAuth, API abilitata e account test coincidano. Un client Web non sostituisce il client Android. Se l’accesso è scaduto, usa **Account Google Drive → Collega / cambia account**. Scollegare revoca il consenso; i download già autorizzati restano sul dispositivo finché non li rimuovi.

### Perché `drive.readonly`

`drive.file` limita l’accesso ai file creati o condivisi esplicitamente con l’app tramite Picker: non abilita l’esplorazione libera della libreria Drive preesistente richiesta. `drive.metadata.readonly` non permette di ascoltare i contenuti. `drive.readonly` è il permesso di sola lettura compatibile con entrambe le operazioni, ma è classificato da Google come **restricted**. Non esiste uno scope limitato ai soli file musicali.

La modalità Testing è adatta alla verifica personale; il consenso può scadere e richiedere un nuovo collegamento. Distribuzione pubblica e idoneità all’uso di scope restricted richiedono valutazione/verifica Google secondo le loro regole. Il codice non trasmette file o token a un server dell’autore. Il token resta in memoria e viene rinnovato tramite Play Services; non è scritto nel database o nei log.

### Streaming e offline

I file passano da Drive direttamente al lettore tramite HTTPS autorizzato; le richieste Range consentono il seek se supportato dalla risposta. La cache temporanea LRU ha un limite di 256 MB. **Gestione cache** permette di pulirla senza cancellare i download. In caso di interruzione Media3 ritenta; al ritorno della rete il servizio riavvia la preparazione se la riproduzione era richiesta e il lettore è in errore. Quando serve un nuovo consenso compare un errore e occorre ricollegare Drive.

I download sono facoltativi, controllano `capabilities.canDownload`, usano file temporanei e vengono promossi a file offline solo quando completi. Richiedono che il processo resti attivo; non sono download WorkManager persistenti. Se il processo viene terminato, ripeti il download. Lo spazio è privato all’app; disinstallarla rimuove libreria, cache e offline. Dalle opzioni puoi eliminare ogni download.

Drive non espone tag ID3 né copertine audio nelle normali risposte Files: viene mostrato il nome del file, «Google Drive» come artista e il nome della cartella come album. I tag incorporati dei file cloud non vengono estratti. I formati riproducibili dipendono da Android/Media3 e dal contenitore.

## Spotify: scelta e limiti verificabili

La sezione offre **Apri app Spotify** (con fallback al sito) e **Apri Spotify nel browser** tramite Android Custom Tabs. Il lettore locale viene messo in pausa prima dell’apertura. Indietro dal browser/app riporta a Jarvis; MENU esce dalla sezione. La sessione è gestita da Spotify e dal browser, non da cookie copiati nell’app.

La [documentazione del Web Player](https://support.spotify.com/us/article/web-player-help/) elenca Chrome, Samsung Internet e Firefox su Android; non garantisce WebView. Non è stato verificato login + riproduzione Spotify in WebView con un account reale, quindi **non viene presentata una WebView come lettore funzionante**. Il supporto Widevine del telefono, da solo, non garantisce quello del player in un browser incorporato.

[App Remote](https://developer.spotify.com/documentation/android) controlla l’app Spotify installata e richiede registrazione sviluppatore e consenso; la documentazione lo descrive come SDK beta. Il [Web Playback SDK](https://developer.spotify.com/documentation/web-playback-sdk) richiede OAuth e un piano Premium compatibile. Nessuno dei due è necessario per aprire il servizio ufficiale e non è incluso in questa versione. **La Click Wheel non controlla Spotify**. Non sono usati scraping, API private o aggiramenti DRM. Login e riproduzione con account Spotify restano da verificare sul telefono; le funzionalità del servizio dipendono da browser, account e piano.

## Compilazione

Richiede JDK 17 o 21, Android SDK Platform 35, Build Tools 35.0.0 e accesso a Google Maven/Maven Central. Gradle 8.9 è gestito dal wrapper; AGP 8.7.3, Kotlin 1.9.25, Compose BOM 2024.12.01 e Media3 1.5.1 sono versioni stabili fissate nel progetto.

```bash
./scripts/create-debug-key.sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Apri il progetto in Android Studio e imposta il percorso SDK con `local.properties` o `ANDROID_HOME`. L’APK viene creato in `app/build/outputs/apk/debug/app-debug.apk`. Il wrapper verifica il checksum della distribuzione Gradle.

L’archivio dei sorgenti esclude i keystore. Lo script genera una nuova firma debug: registra in Google Cloud lo SHA-1 della tua build. La chiave dell’APK consegnato rimane nel workspace in `signing/debug.keystore`, esclusa da Git; conservala separatamente se desideri ricompilare aggiornamenti firmati allo stesso modo. Android richiede la stessa firma per aggiornare un’app già installata senza rimuoverne i dati.

Per una release con **tua chiave privata** (non inclusa), configura le variabili e compila:

```bash
export JARVIS_KEYSTORE=/percorso/assoluto/release.jks
export JARVIS_STORE_PASSWORD='password-del-tuo-store'
export JARVIS_KEY_ALIAS='alias-della-tua-chiave'
export JARVIS_KEY_PASSWORD='password-della-tua-chiave'
./gradlew :app:assembleRelease
```

Senza queste variabili `assembleRelease` produce un APK **non firmato**, non installabile direttamente. Registra in Google Cloud anche lo SHA-1 della tua firma release. Non conservare password reali nel repository.

## Architettura e verifiche

- `ui/`: scocca Canvas, ruota, geometria pura, schermate Compose.
- `PlayerViewModel`: stato Flow e comandi verso MediaController.
- `data/`: Room per brani, playlist e preferiti; DataStore per preferenze e ripresa.
- `playback/PlaybackService`: ExoPlayer, MediaSession, audio focus, notifica multimediale e coda.
- `drive/`: autorizzazione ufficiale, elenco paginato, streaming autorizzato e download.
- `app/src/test/`: test JVM della ruota.
- `app/src/androidTest/`: test dispositivo con audio WAV sintetico per scansione e avanzamento reale del lettore; test UI ruota/tema.

I risultati effettivi, le immagini dell’emulatore e le verifiche ancora necessarie sono riportati in [docs/VERIFICA.md](docs/VERIFICA.md). Non sono inclusi dati, musica commerciale, account o credenziali di test.

L’aspetto è ottimizzato per un display verticale con proporzioni del Pixel 9 Pro XL. Scocca e ruota si riducono proporzionalmente su schermi piccoli o orizzontali; il display rispetta gli inset del sistema. Test su Pixel fisico, Bluetooth, chiamate e politiche energetiche dei diversi produttori rimangono necessari.


## Drive senza account: chiave API e cartelle condivise

In alternativa a OAuth, dal menu *Impostazioni → Drive con chiave API* (o *Google Drive → Usa chiave API…*) si può leggere soltanto ciò che è condiviso con «Chiunque abbia il link».

1. Abilita la Drive API: https://console.cloud.google.com/apis/library/drive.googleapis.com
2. Crea una chiave API nello stesso progetto: https://console.cloud.google.com/apis/credentials (limitala alla sola Drive API; una restrizione «app Android» fa rifiutare le richieste).
3. Incolla la chiave nell'app. Resta solo nello spazio privato del telefono (`allowBackup=false`) e non viene committata.
4. Su Drive condividi la cartella con «Chiunque abbia il link» (Visualizzatore) e incolla il link nell'app. I link con `resourcekey` sono gestiti tramite l'header `X-Goog-Drive-Resource-Keys`.
5. *Verifica collegamento* elenca la prima cartella usando soltanto la chiave.

Se è collegato anche un account Google, l'app usa l'account. Non verificato con un Drive reale: la lettura di file condivisi con chiave API segue il comportamento documentato delle API Google, ma l'esito va controllato con *Verifica collegamento*.
