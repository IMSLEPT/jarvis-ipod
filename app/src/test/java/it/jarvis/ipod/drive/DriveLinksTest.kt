package it.jarvis.ipod.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DriveLinksTest {
    private val id = "1AbCdEfGhIjKlMnOpQrStUvWxYz_-0123"

    @Test fun shareLinkWithTracking() = assertEquals(id, DriveLinks.folderId("  https://drive.google.com/drive/folders/$id?usp=sharing "))
    @Test fun linkWithAccountNumber() = assertEquals(id, DriveLinks.folderId("https://drive.google.com/drive/u/1/folders/$id"))
    @Test fun oldStyleLinks() {
        assertEquals(id, DriveLinks.folderId("https://drive.google.com/open?id=$id"))
        assertEquals(id, DriveLinks.folderId("https://drive.google.com/folderview?id=$id&usp=sharing"))
    }
    @Test fun bareId() = assertEquals(id, DriveLinks.folderId(id))
    @Test fun rejectsOtherTextAndSites() {
        assertNull(DriveLinks.folderId(""))
        assertNull(DriveLinks.folderId("ciao"))
        assertNull(DriveLinks.folderId("https://example.com/drive/folders/$id"))
        assertNull(DriveLinks.folderId("https://drive.google.com/file/d/$id/view"))
    }
}
