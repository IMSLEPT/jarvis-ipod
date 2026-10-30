# APK debug e sorgenti

Jarvis iPod è un’app Android nativa Kotlin/Compose con Click Wheel, temi Silver Classic e Midnight Black, libreria locale, Media3/MediaSession e integrazione Google Drive configurabile.

Allegati:

- `Jarvis-iPod-debug.apk`: APK debug installabile, Android 8.0 o successivo.
- `Jarvis-iPod-sorgenti.zip`: sorgenti, wrapper Gradle, test e documentazione, incluso `CLAUDE.md`.
- `SHA256SUMS`: checksum dei due allegati.

Scaricare e aprire l’APK sul telefono, autorizzando Files a installare app se richiesto. La firma è debug; non è una release destinata a Play Store. Per compilare aggiornamenti consultare le indicazioni su firma e OAuth nel README.

Verifiche della consegna iniziale del 28 ottobre 2026: build Gradle, 6 test JVM, Lint con 0 errori e 2 test strumentali su Android 11 ATD superati. I log sono in `docs/reports/`; non costituiscono prove sul Pixel fisico. Per lo stato della compilazione su GitHub consultare le esecuzioni del workflow Android.

Google Drive richiede la configurazione OAuth Android descritta nel README; login e streaming con account reale restano da verificare. Spotify si apre nell’app ufficiale o nel browser, senza controllo dalla Click Wheel. Fedeltà grafica, Bluetooth e comportamento sul Pixel fisico restano da verificare.

Non sono inclusi keystore, credenziali, musica commerciale o account di test.
