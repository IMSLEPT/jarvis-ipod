# Verifica della consegna

Data: 28 ottobre 2026. Questo documento distingue i controlli eseguiti dalle funzionalità implementate che richiedono ancora una prova sul dispositivo.

## Controlli eseguiti

| Controllo | Esito |
|---|---|
| Gradle `:app:assembleDebug` | Superato; APK debug prodotto |
| Gradle `:app:testDebugUnitTest` | 6 test superati, 0 errori |
| Gradle `:app:assembleDebugAndroidTest` | Superato; test strumentali compilati |
| Gradle `:app:lintDebug` | Superato; 0 errori, 20 avvisi |
| Verifica firma con `apksigner verify --print-certs` | Superata; firma debug RSA, SHA-1 coerente con README |
| Installazione APK tramite ADB | Riuscita su emulatori Android 11 e Android 15 |
| Test dispositivo Click Wheel e tema | Superato su Android 11 ATD: gesto circolare, apertura impostazioni, cambio colore e persistenza dopo ricreazione |
| Suite strumentale finale | **2 test superati, 0 errori**, Android 11 AOSP ATD, esecuzione reale di 201,606 secondi |
| Audio locale e background | Verificati avanzamento del player, passaggio effettivo a `ON_STOP`, prosecuzione audio e salvataggio di coda/posizione |
| Preferiti e nuova scansione | Verificata conservazione del preferito dopo una nuova scansione MediaStore |
| Percorso offline Drive | Riprodotto un WAV di test attraverso un URI `drive://` risolto sul file privato offline, senza autorizzazione Google |

Gli avvisi Lint riguardano soprattutto versioni più recenti delle dipendenze, suggerimento di passaggio da kapt a KSP e gestione dello spazio disco. Non è stato usato un baseline per nascondere errori. Le versioni stabili sono fissate per compatibilità.

I test JVM verificano direzione oraria e antioraria, attraversamento dell’angolo ±180°, sensibilità, rigetto di movimenti discontinui, stabilità ai piccoli movimenti e distinzione tra centro, anello e pulsanti.

Le prove sono state compilate con JDK 17, Gradle 8.9, Android SDK 35 e Build Tools 35.0.0. La verifica TLS dei repository è rimasta attiva, usando il certificato CA configurato nell’ambiente.

## Esecuzione su Android

I test in `app/src/androidTest` coprono navigazione tramite gesto circolare, cambio tema e ricreazione dell’attività, scansione di un WAV sintetico in MediaStore, avanzamento effettivo della posizione audio, riproduzione in background, salvataggio della posizione e lettura di un file offline attraverso l’URI Drive.

Gli emulatori sono eseguiti senza accelerazione KVM. I tentativi su Android 15 hanno incontrato crash di servizi Android, timeout di avvio e limiti di memoria; un tentativo strumentale è terminato con `Process crashed`. La traccia dell’app ha inoltre evidenziato un disegno della grana metallica troppo costoso: è stato sostituito con una texture procedurale generata una volta e riutilizzata.

La prova della ruota e della persistenza del tema è stata eseguita con successo su Android 11 AOSP ATD. La prova audio ha confermato scansione MediaStore, metadati, salvataggio dei preferiti e avanzamento effettivo della riproduzione. I primi tentativi di controllo del background sono falliti perché il file audio era terminato durante i lunghi passaggi di stato del framework di test. Il test definitivo usa il passaggio reale del task in background con verifica dell’evento `ON_STOP`.

Una successiva traccia ANR ha individuato un blocco in `PlaybackService.onDestroy`: un `runBlocking` sul thread principale poteva attendere una transazione DataStore già in coda sullo stesso thread. La chiusura del servizio ora acquisisce lo stato del player e delega la scrittura a una coroutine dell’applicazione, senza bloccare il ciclo di vita Android.

**Dopo la correzione, la suite completa è terminata con `OK (2 tests)`.** Il log finale è in `docs/reports/device-tests-final.log`. I tentativi precedenti conservati nei report descrivono le anomalie individuate durante lo sviluppo e non rappresentano l’esito della build consegnata.

**Le immagini ATD disabilitano il rendering hardware**, come documentato da [Android Developers](https://developer.android.com/studio/test/managed-devices). Le loro schermate nere non sono screenshot validi del design. La fedeltà grafica, la fluidità e l’aptica sul Pixel fisico restano da verificare. Non viene consegnata una finta schermata renderizzata come prova dell’apertura dell’app.

## Funzionalità implementate

- Interfaccia nativa con proporzioni del riferimento, due scocche e display moderno.
- Ruota circolare, pulsanti, seek su pressione prolungata, selezione touch, sensibilità e feedback configurabili.
- Libreria MediaStore, metadati e copertine locali, ricerca e ordinamento.
- Playlist miste, preferiti, recenti, shuffle, repeat, gestione della coda.
- Media3/MediaSession con servizio di riproduzione, audio focus, notifica e controlli multimediali di sistema.
- Ripristino della coda e della posizione in pausa.
- OAuth Google ufficiale; cartelle e ricerca Drive API v3, streaming, cache e download facoltativi.
- Spotify dentro Jarvis tramite App Remote SDK (codice compilato in CI; non provato con un account).
- Tema Midnight Black in grafite (colori campionati dal riferimento), scocca a tutta altezza, alone colorato di ruota e tasti (blu di base, colore a scelta in Impostazioni), tasti MENU (su) e basso (giù), centro che avvia/mette in pausa il brano, MENU tenuto premuto per tornare indietro, Drive con chiave API e link di cartella condivisa, scorciatoie Drive e Spotify nella libreria, interfaccia indipendente dalla dimensione carattere di sistema, backup su file di Client ID, cartelle Drive e preferenze (compilazione in CI; non visti né provati su telefono).
- Nuova grafica (barra di stato, menu, griglie, lettore, schermata iniziale), ruota con scia luminosa e animazioni, modalità «Solo tasti», vibrazione a 4 livelli (codice compilato in CI; non visto né provato su telefono).

L’elenco indica presenza del codice: per le integrazioni esterne non equivale a una verifica con account reali.

## Verifiche che richiedono account o hardware

- **Drive:** configurazione Google Cloud con package e SHA-1, consenso, elenco di cartelle reali, streaming, download remoto e rinnovo dei token. Non sono disponibili account o credenziali di test in questa sessione.
- **Spotify:** connessione App Remote, autorizzazione, comandi, copertina e avvio da link con un account reale (Premium per i brani singoli). Non verificati. La build 1.1 supera compilazione, test JVM e Lint su GitHub Actions, ma nessun test strumentale è stato eseguito.
- **Grafica e vibrazione:** resa visiva su schermo reale, fluidità delle animazioni della ruota, intensità e ritmo della vibrazione sul Pixel 9 Pro XL, comportamento con «Feedback tattile» di sistema disattivato.
- **Drive con chiave API e link:** nessuna prova con una chiave e una cartella condivisa reali; il test JVM verifica solo il riconoscimento dei link. Il test strumentale JarvisDeviceTest è stato adeguato (MENU non torna più indietro) ma compilato soltanto, non eseguito.
- **Fedeltà del colore grafite:** i valori sono stati campionati dall’immagine di riferimento (griglia 18×30 e bordo), ma il confronto visivo con il riferimento sul telefono non è stato eseguito: non si può dichiarare «identico».
- **Backup:** salvataggio automatico e ripristino con il selettore file di Android (anche su Google Drive) non provati su dispositivo; il test JVM verifica solo la serializzazione.
- **Pixel 9 Pro XL fisico:** qualità tattile, fluidità reale, cuffie Bluetooth, chiamate, lock screen, risparmio energetico e comportamento con diversi file/container.
- **Rete:** interruzioni e ripresa dello streaming Drive con un file remoto autorizzato.

I download richiedono che il processo rimanga attivo. I metadati dei file Drive si limitano a quanto descritto nel README; non viene effettuata estrazione remota dei tag ID3. La release è configurabile con una chiave privata del proprietario; l’APK consegnato è debug.

## Ripetere i controlli

```bash
./scripts/create-debug-key.sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

L’ultimo comando richiede un dispositivo/emulatore sbloccato con `adb devices` nello stato `device`. I test inseriscono un breve WAV sintetico e lo eliminano al termine. Non usano musica commerciale o dati di un account.

I log di build, il report Lint e i risultati JVM sono in `docs/reports/`.
