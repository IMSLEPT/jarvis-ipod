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
| JUnit | JUnit / Eclipse Public License 1.0 |

Gli avvisi e le licenze delle dipendenze restano nei loro pacchetti. Per una distribuzione pubblica, conservare gli avvisi applicabili anche alle dipendenze transitive. Gli elementi grafici della scocca e il paesaggio di fallback sono codice originale del progetto; le icone provengono da Material Icons. Non sono inclusi brani, copertine musicali commerciali, asset Apple, SDK Spotify o librerie ottenute da repository non ufficiali.

La chiave debug generata durante la build serve solo allo sviluppo. La release richiede una chiave privata scelta e conservata dal titolare dell’app.
