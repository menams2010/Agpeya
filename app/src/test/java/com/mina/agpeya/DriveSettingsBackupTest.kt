package com.mina.agpeya

import com.mina.agpeya.data.DriveSettingsBackup
import com.mina.agpeya.data.SettingsBackup
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class DriveSettingsBackupTest {
    @Test
    fun createsBackupInPrivateAppDataFolder() = runTest {
        val lookup = Response("""{"files":[]}""")
        val upload = Response("""{"id":"new-backup"}""")
        val responses = ArrayDeque(listOf(lookup, upload))
        val drive = DriveSettingsBackup { url -> responses.removeFirst().apply { requestedUrl = url } }
        drive.upload("test-token", """{"version":1}""")

        assertTrue(lookup.requestedUrl.toString().contains("spaces=appDataFolder"))
        assertEquals("POST", upload.requestMethod)
        assertTrue(upload.body.toString("UTF-8").contains("\"parents\":[\"appDataFolder\"]"))
        assertTrue(upload.body.toString("UTF-8").contains("""{"version":1}"""))
        assertEquals("Bearer test-token", upload.getRequestProperty("Authorization"))
        assertTrue(upload.disconnected)
    }

    @Test
    fun updatesExistingBackupRatherThanCreatingAnother() = runTest {
        val upload = Response("{}")
        val responses = ArrayDeque(listOf(Response("""{"files":[{"id":"backup-1"}]}"""), upload))
        val drive = DriveSettingsBackup { url -> responses.removeFirst().apply { requestedUrl = url } }
        drive.upload("test-token", "settings")

        assertEquals("PATCH", upload.requestMethod)
        assertTrue(upload.requestedUrl.toString().endsWith("/backup-1?uploadType=media"))
        assertEquals("settings", upload.body.toString("UTF-8"))
    }

    @Test
    fun downloadsExistingBackup() = runTest {
        val download = Response("settings")
        val responses = ArrayDeque(listOf(Response("""{"files":[{"id":"backup-1"}]}"""), download))
        val drive = DriveSettingsBackup { url -> responses.removeFirst().apply { requestedUrl = url } }
        assertEquals("settings", drive.download("test-token"))
        assertTrue(download.requestedUrl.toString().endsWith("/backup-1?alt=media"))
    }

    @Test
    fun missingBackupIsAnError() = runTest {
        val drive = DriveSettingsBackup { Response("""{"files":[]}""") }
        try {
            drive.download("test-token")
            fail("Missing backup must not report success")
        } catch (error: IOException) {
            assertTrue(error.message!!.contains("No settings backup"))
        }
    }

    @Test
    fun rejectsHttpErrorsAndDisconnects() = runTest {
        for (status in listOf(401, 403, 500)) {
            val response = Response("private response", status)
            val drive = DriveSettingsBackup { response }
            try {
                drive.download("test-token")
                fail("HTTP $status must not report success")
            } catch (error: IOException) {
                assertFalse(error.message!!.contains("test-token"))
                assertFalse(error.message!!.contains("private response"))
                assertTrue(response.disconnected)
            }
        }
    }

    @Test
    fun rejectsOversizedDownload() = runTest {
        val responses = ArrayDeque(listOf(
            Response("""{"files":[{"id":"backup-1"}]}"""),
            Response(" ".repeat(SettingsBackup.MAX_BYTES + 1)),
        ))
        val drive = DriveSettingsBackup { responses.removeFirst() }
        try {
            drive.download("test-token")
            fail("Oversized backup must be rejected")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message!!.contains("too large"))
        }
    }

    private class Response(private val content: String, private val status: Int = 200) :
        HttpURLConnection(URL("https://www.googleapis.com")) {
        val body = ByteArrayOutputStream()
        var disconnected = false
        lateinit var requestedUrl: URL
        private var verb = "GET"
        override fun setRequestMethod(method: String) { verb = method }
        override fun getRequestMethod() = verb
        override fun getResponseCode() = status
        override fun getInputStream() = content.byteInputStream()
        override fun getOutputStream() = body
        override fun disconnect() { disconnected = true }
        override fun usingProxy() = false
        override fun connect() = Unit
    }
}
