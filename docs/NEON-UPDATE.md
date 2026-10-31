# Jarvis iPod 1.1.1 — Alone neon e firma permanente

Base funzionale e grafica: commit `dc98099` (APK v1.1.0-build5).

- Alone multicolore continuo attorno a ruota, centro e quattro tasti; impulsi sui bassi, sull'energia musicale e sui comandi. Intensità proporzionale anche al volume multimediale del telefono. Il disegno è limitato all'area della ruota e non invade il display.
- Interruttore **Alone neon**, attivo per impostazione iniziale e salvato in DataStore. Nessun permesso microfono. In pausa resta l'alone di base; spegnendolo si arrestano animazione e analisi.
- Stesso package `it.jarvis.ipod`, archivio dati, icona, temi, schermate e codice Drive della versione base. Firma ripristinata sempre dal Secret `JARVIS_KEYSTORE_B64`; senza Secret o con certificato diverso la pubblicazione si ferma. VersionCode crescente a ogni compilazione pubblicata, anche rieseguendo il workflow.

## Prima installazione di questa serie

La chiave privata disponibile ha certificato SHA-256 `ce1795e9a6411a90731a6c31be2c9d6604ae4ff6e151c5a5859d85f74208edb5`.
L'APK build5 ha certificato SHA-256 `dad8af012eaaf3a0a1760ff767a1fd2772559751ef75f109cda09b47ee45d1e7`.
Sono firme diverse: **per passare da build5 a questa serie è necessaria una disinstallazione iniziale**, salvo recuperare la chiave privata originale. La disinstallazione cancella impostazioni, playlist, preferiti e download privati; la musica nei file condivisi e su Drive rimane. Annotare prima chiave API e link cartelle e riscaricare gli offline. Non è previsto un recupero automatico dei dati disinstallati.
Da questa serie in poi gli APK pubblicati dallo stesso workflow si installano sopra quelli precedenti mantenendo i dati, finché il Secret permanente viene conservato.
Per OAuth Google, il nuovo SHA-1 va autorizzato nel client Android Google Cloud; la modalità Drive con chiave API conserva il codice originale.

## Controlli della pubblicazione

La Release viene pubblicata solo dopo compilazione APK e test strumentali, test JVM, Android Lint, verifica crittografica di firma/package e test su emulatore Android 11. I risultati effettivi sono nel relativo run GitHub Actions; non confondere questi controlli con i vecchi report del 28 ottobre.

Non provato su un telefono fisico: resa del neon nei due temi, fluidità/consumi, allineamento percepito dei bassi con cuffie Bluetooth, risposta al volume dei diversi dispositivi, aggiornamento sul Pixel con i dati personali, accesso Drive reale e OAuth. Il rilevamento usa il PCM del lettore interno: non ascolta audio prodotto dall'app Spotify esterna. I buffer di riproduzione e Bluetooth possono introdurre ritardo e la sensibilità ai bassi dipende dal brano.

Chiavi private e contenuto dei Secrets non sono inclusi nei sorgenti, negli APK o negli allegati dei test.

## Esito effettivo — 31 ottobre 2026

APK 1.1.1, versionCode **215630330**, commit applicativo `ef81eec229f19b85e67db43b2f4f33af7170156a`.

[GitHub Actions: esecuzione completa riuscita](https://github.com/IMSLEPT/jarvis-ipod/actions/runs/41222493615).

| Controllo | Esito reale |
| --- | --- |
| Compilazione APK e APK test | Superata |
| Test JVM (bassi/PCM, inviluppo/volume/colore, rotella, link Drive) | 27 test, 0 errori, 0 fallimenti |
| Android Lint | 0 errori, 21 avvisi; nessun baseline aggiunto |
| Emulatore Android 11 AOSP ATD x86_64, KVM | 3 test superati in 10,277 secondi |
| UI emulata | Gesto circolare, selezione, tema, interruttore neon e persistenza dopo ricreazione |
| Audio emulato | PCM effettivamente letto dal meter, pausa confermata dal servizio, background e Drive offline |
| Aggiornamento reale con `adb install -r` | Da versionCode 1000 a 215630330, con la stessa firma; installazione riuscita |
| Conservazione dati emulatore | Preferenze DataStore e database byte per byte identici dopo aggiornamento; file sentinella conservato |
| Firma APK | Verificata; certificato permanente corrispondente al Secret |
| Confronto sorgenti con `dc98099` | Icona/risorse, manifest, intero codice Drive, archivio della libreria, ViewModel e MainActivity invariati |

Le prime esecuzioni hanno fallito nei controlli UI: il ridisegno dello scrollbar leggeva lo stato del layout durante la composizione, provocando ricomposizioni continue. Lo stato ora viene letto soltanto durante il disegno, mantenendo geometria e colori. È stato inoltre corretto il test della pausa per attendere la conferma asincrona del servizio; il meter non viene dichiarato fermo prima di quella conferma. Il run sopra è quello finale, completamente riuscito.

Il certificato permanente ha SHA-1 `C7:B9:F3:29:D4:3E:4F:0E:5F:76:69:FB:A5:55:9A:54:D9:34:E8:5A`, da autorizzare in Google Cloud soltanto se si usa OAuth con account Google.

I test ATD non costituiscono una verifica visiva della resa grafica e non sono prove su un telefono fisico. Restano validi i limiti indicati sopra. I dati dell'emulatore sono sintetici, non i dati personali del proprietario.
