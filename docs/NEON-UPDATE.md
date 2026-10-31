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
