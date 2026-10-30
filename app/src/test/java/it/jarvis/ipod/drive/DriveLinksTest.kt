package it.jarvis.ipod.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DriveLinksTest {
    private val id = "1AbCdEfGhIjKlMnOpQrStUvWxYz_-0123"
    @Test fun folderLink() = assertEquals(id to "", DriveLinks.parse("https://drive.google.com/drive/folders/$id?usp=sharing"))
    @Test fun folderLinkWithAccountIndex() = assertEquals(id to "", DriveLinks.parse("https://drive.google.com/drive/u/0/folders/$id"))
    @Test fun resourceKey() = assertEquals(id to "0-AbC_d", DriveLinks.parse("https://drive.google.com/drive/folders/$id?resourcekey=0-AbC_d&usp=sharing"))
    @Test fun openLink() = assertEquals(id to "", DriveLinks.parse("https://drive.google.com/open?id=$id"))
    @Test fun bareId() = assertEquals(id to "", DriveLinks.parse("  $id \n"))
    @Test fun rejectsGarbage() { assertNull(DriveLinks.parse("ciao")); assertNull(DriveLinks.parse("")); assertNull(DriveLinks.parse("https://example.com/")) }
    @Test fun headerSkipsEmptyKeys() = assertEquals("a/1,b/2", DriveLinks.resourceKeyHeader(mapOf("a" to "1", "c" to ""), mapOf("b" to "2", "a" to "9")))
}
