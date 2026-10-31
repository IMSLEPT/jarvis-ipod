# Jarvis iPod 1.1.2 — dalla versione originale allegata dal proprietario

La base completa è il commit **7adfd7d1364f4a55e04c359279d058f618d74d06**, ramo `drive-cartelle-scelte`, build **115**. Il relativo APK di GitHub Actions è identico byte per byte all'APK `app-debug.apk` allegato dal proprietario: SHA-256 `1102878644d1f312fb708a5ae922e2b1c73ac4da6909cd3237621609e4fee24d`.

Sono mantenuti i file originali dell'icona adattiva, senza ritagli, ridimensionamenti, rigenerazioni o sostituzioni. Tutte le risorse e il manifest sono identici a quella base. Rimangono i suoi aggiornamenti a grafica, comandi, vibrazione, cartelle Drive, Spotify App Remote e backup delle impostazioni. Le precedenti Release 1.1.1 erano partite dalla base sbagliata e non sono il riferimento di questa consegna.

Le sole modifiche funzionali aggiunte sono:
- Alone neon multicolore continuo sulla ruota e sui tasti, che pulsa con i bassi e l'intensità dei campioni del lettore interno e con il volume multimediale del telefono; reagisce anche a rotazione e pressioni. Il disegno esterno è limitato all'area sotto il display. Le animazioni e i comandi già esistenti sono mantenuti.
- Interruttore **Alone neon**, attivo come impostazione iniziale e salvato anche nel backup. L'opzione preesistente «Spento» in Colore alone resta rispettata.
- Firma permanente obbligatoria dal Secret GitHub e numero versione crescente, senza generare nuove chiavi per le pubblicazioni.

L'APK originale build 115 è già firmato con lo stesso certificato permanente (SHA-256 `ce1795e9a6411a90731a6c31be2c9d6604ae4ff6e151c5a5859d85f74208edb5`). **Questa versione si aggiorna sopra quell'APK senza disinstallare.** Il precedente avviso relativo alla diversa firma della build5 non riguarda l'APK build115 ora fornito come riferimento.

La pipeline verifica compilazione, test JVM, Lint, test su emulatore Android 11, firma, risorse/manifest identici alla base e conservazione dei dati durante la reinstallazione. Quando disponibile scarica proprio l'APK originale build115 dall'artefatto storico, ne controlla l'hash e verifica l'aggiornamento diretto, conservando un file sentinella. I risultati effettivi sono nell'esecuzione GitHub Actions associata alla Release.

Non verificato su un telefono fisico: resa percepita del neon, latenza Bluetooth, consumi, risposta al volume dei dispositivi e integrazioni con account Drive/Spotify reali. L'analisi dei bassi legge il PCM del lettore interno (musica locale e Drive); l'audio prodotto dall'app Spotify esterna non attraversa quel meter, mentre i suoi controlli App Remote restano quelli della base.
