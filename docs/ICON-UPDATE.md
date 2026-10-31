# Jarvis iPod — correzione icona Robot DJ

Questa build corregge l'icona del pacchetto Android: usa l'immagine originale del robot con le cuffie e l'iPod, corrispondente all'icona indicata dal proprietario. L'immagine viene soltanto ridimensionata per l'uso come risorsa Android, senza rigenerarla o ridisegnarla. Sono configurate sia l'icona standard sia quella circolare, con una risorsa adattiva da Android 8.

Il precedente APK conservava l'icona dell'iPod grigio presente nel commit di base `dc98099`; non conteneva l'immagine del robot. Questa versione la incorpora realmente nel pacchetto e non dipende da una personalizzazione del launcher.

Nessuna modifica a codice Kotlin, grafica interna, neon, musica o Google Drive rispetto alla precedente Release `v1.1.1-build215630330`. La firma permanente e il package sono gli stessi, con versionCode maggiore: si può aggiornare quella Release senza disinstallare e mantenendo i dati. Per installazioni ancora firmate con la vecchia chiave della build5 rimane valido l'avviso della [prima migrazione](https://github.com/IMSLEPT/jarvis-ipod/blob/main/docs/NEON-UPDATE.md).

La pubblicazione passa attraverso la stessa pipeline: compilazione, 27 test JVM, Lint, 3 test su emulatore Android 11 e installazione come aggiornamento con confronto dei dati. I risultati effettivi sono nell'esecuzione GitHub Actions associata al commit della Release. Non provata su un telefono fisico.
