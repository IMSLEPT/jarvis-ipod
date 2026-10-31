package it.jarvis.ipod.spotify

/** Converte link o URI Spotify incollati dall'utente nell'URI `spotify:tipo:id` che l'app Spotify sa riprodurre. */
object SpotifyLinks {
    private const val ID = "[A-Za-z0-9]{10,32}"
    private val types = setOf("track", "album", "playlist", "artist", "episode", "show")
    private val web = Regex("^https?://open\\.spotify\\.com/(?:intl-[a-z]{2}(?:-[A-Za-z]+)?/)?(track|album|playlist|artist|episode|show)/($ID)(?:[/?#].*)?$", RegexOption.IGNORE_CASE)
    private val idPattern = Regex("^$ID$")

    fun parse(input: String): String? {
        val text = input.trim()
        if (text.startsWith("spotify:", ignoreCase = true)) {
            val parts = text.split(":")
            val kind = parts.getOrNull(1)?.lowercase()
            return when {
                parts.size == 3 && kind in types && idPattern.matches(parts[2]) -> "spotify:$kind:${parts[2]}"
                parts.size == 5 && kind == "user" && parts[3].equals("playlist", true) && idPattern.matches(parts[4]) -> "spotify:playlist:${parts[4]}"
                else -> null
            }
        }
        val match = web.matchEntire(text) ?: return null
        return "spotify:${match.groupValues[1].lowercase()}:${match.groupValues[2]}"
    }
}
