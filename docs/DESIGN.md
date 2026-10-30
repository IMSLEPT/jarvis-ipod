# Ricostruzione del riferimento

Il riferimento allegato mostra tre viste dello stesso hardware. È stato analizzato direttamente prima dell’implementazione. Misure approssimate sulla prima scocca:

| Elemento | Rapporto rispetto alla larghezza della scocca |
|---|---:|
| Altezza | 1,82 |
| Margine laterale display | 0,066 |
| Altezza display | 0,974 |
| Diametro Click Wheel | 0,608 |
| Diametro pulsante centrale / ruota | 0,387 |

`IPodShell` usa questi rapporti senza deformare la ruota. Bordi arrotondati, incisione del display, riflessi e micrograna sono disegnati in Canvas. Il display ha una barra di stato propria con ora e batteria reali. Il menu principale riprende lista chiara a sinistra e copertina a destra. Libreria a sei riquadri e lettore scuro riprendono le altre due viste. Le copertine reali provengono da MediaStore; in assenza di copertina compare un paesaggio disegnato proceduralmente.

La scocca viene centrata nell’area sicura del telefono e mantiene le proporzioni del riferimento: gli spazi esterni dipendono dal rapporto del display Android. Non sono copiate risorse grafiche Apple né immagini commerciali di album.
