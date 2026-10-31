package it.jarvis.ipod.spotify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpotifyLinksTest {
    private val id = "4uLU6hMCjMI75M1A2tKUQC"

    @Test fun acceptsSpotifyUri() = assertEquals("spotify:track:$id", SpotifyLinks.parse("spotify:track:$id"))

    @Test fun acceptsShareLinkAndDropsTracking() =
        assertEquals("spotify:track:$id", SpotifyLinks.parse("  https://open.spotify.com/track/$id?si=abc123  "))

    @Test fun acceptsLocalisedLink() =
        assertEquals("spotify:album:$id", SpotifyLinks.parse("https://open.spotify.com/intl-it/album/$id"))

    @Test fun acceptsPlaylistAndOldUserPlaylistUri() {
        assertEquals("spotify:playlist:$id", SpotifyLinks.parse("https://open.spotify.com/playlist/$id"))
        assertEquals("spotify:playlist:$id", SpotifyLinks.parse("spotify:user:someone:playlist:$id"))
    }

    @Test fun rejectsOtherTextAndSites() {
        assertNull(SpotifyLinks.parse(""))
        assertNull(SpotifyLinks.parse("ciao"))
        assertNull(SpotifyLinks.parse("https://example.com/track/$id"))
        assertNull(SpotifyLinks.parse("spotify:track:corto"))
        assertNull(SpotifyLinks.parse("spotify:user:$id"))
    }
}
