# Dipendenze e risorse

Il progetto usa esclusivamente repository ufficiali Google Maven e Maven Central, oltre alla distribuzione ufficiale Gradle. Le versioni sono fissate in `app/build.gradle.kts` e `build.gradle.kts`.

| Componente | Provenienza / licenza principale |
|---|---|
| Kotlin e Coroutines | JetBrains / Apache 2.0 |
| Compose, Material, Activity, Lifecycle | AndroidX / Apache 2.0 |
| Media3, Room, DataStore, Browser | AndroidX / Apache 2.0 |
| OkHttp | Square / Apache 2.0 |
| Coil | Coil OSS / Apache 2.0 |
| Google Play Services Auth | Google / termini Google Play Services SDK |
| Spotify App Remote SDK 0.8.0 (AAR locale in `app/libs/`) | Spotify / Apache 2.0, soggetto ai Termini per sviluppatori Spotify |
| Gson | Google / Apache 2.0 |
| JUnit | JUnit / Eclipse Public License 1.0 |

Gli avvisi e le licenze delle dipendenze restano nei loro pacchetti. Per una distribuzione pubblica, conservare gli avvisi applicabili anche alle dipendenze transitive. Gli elementi grafici della scocca e il paesaggio di fallback sono codice originale del progetto; le icone provengono da Material Icons. Non sono inclusi brani, copertine musicali commerciali, asset Apple o librerie ottenute da repository non ufficiali. L’AAR di Spotify proviene dal repository ufficiale github.com/spotify/android-sdk (licenza e NOTICE in `app/libs/`).

La chiave debug generata durante la build serve solo allo sviluppo. La release richiede una chiave privata scelta e conservata dal titolare dell’app.
