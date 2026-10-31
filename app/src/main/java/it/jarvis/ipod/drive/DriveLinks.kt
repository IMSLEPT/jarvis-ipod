package it.jarvis.ipod.drive

/** Ricava l'ID di una cartella Google Drive dal link «Condividi» (o da un ID incollato da solo). */
object DriveLinks {
    private val idPattern = Regex("[A-Za-z0-9_-]{15,}")
    private val inPath = Regex("/folders/([A-Za-z0-9_-]{15,})")
    private val inQuery = Regex("[?&]id=([A-Za-z0-9_-]{15,})")

    fun folderId(text: String): String? {
        val t = text.trim()
        if (t.isEmpty()) return null
        if (t.startsWith("http://") || t.startsWith("https://")) {
            val host = t.substringAfter("://").substringBefore('/').substringBefore('?').lowercase()
            if (host != "drive.google.com" && host != "docs.google.com") return null
            return inPath.find(t)?.groupValues?.get(1) ?: inQuery.find(t)?.groupValues?.get(1)
        }
        return if (idPattern.matchEntire(t) != null) t else null
    }
}
