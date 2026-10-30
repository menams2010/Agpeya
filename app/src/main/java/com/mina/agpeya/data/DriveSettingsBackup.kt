package com.mina.agpeya.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class DriveSettingsBackup internal constructor(
    private val openConnection: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
) {
    suspend fun upload(token: String, settings: String) = withContext(Dispatchers.IO) {
        val id = findFile(token)
        if (id == null) {
            val boundary = UUID.randomUUID().toString()
            val metadata = JSONObject()
                .put("name", FILE_NAME)
                .put("parents", org.json.JSONArray().put("appDataFolder"))
            val body = "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n" +
                "$metadata\r\n--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n" +
                "$settings\r\n--$boundary--"
            request(
                token, "POST", "$UPLOAD?uploadType=multipart",
                body, "multipart/related; boundary=$boundary",
            )
        } else {
            request(token, "PATCH", "$UPLOAD/$id?uploadType=media", settings)
        }
        Unit
    }

    suspend fun download(token: String): String = withContext(Dispatchers.IO) {
        val id = findFile(token) ?: throw IOException("No settings backup found in this Google account")
        request(token, "GET", "$FILES/$id?alt=media")
    }

    private fun findFile(token: String): String? {
        val query = java.net.URLEncoder.encode("name = '$FILE_NAME' and trashed = false", "UTF-8")
        val response = request(
            token, "GET",
            "$FILES?spaces=appDataFolder&q=$query&fields=files(id)&orderBy=modifiedTime%20desc&pageSize=1",
        )
        val files = JSONObject(response).getJSONArray("files")
        return if (files.length() == 0) null else files.getJSONObject(0).getString("id").also {
            require(it.matches(Regex("[A-Za-z0-9_-]+"))) { "Invalid Drive file ID" }
        }
    }

    private fun request(
        token: String,
        method: String,
        url: String,
        body: String? = null,
        contentType: String = "application/json; charset=UTF-8",
    ): String {
        val connection = openConnection(URL(url))
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", contentType)
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            if (status !in 200..299) {
                throw DriveHttpException(status, when (status) {
                    401 -> "Google authorization expired. Please try again."
                    403 -> "Google Drive access denied. Check consent and Drive API configuration."
                    else -> "Google Drive request failed (HTTP $status)"
                })
            }

            return connection.inputStream.use { stream ->
                val bytes = stream.readNBytesCompat(SettingsBackup.MAX_BYTES + 1)
                require(bytes.size <= SettingsBackup.MAX_BYTES) { "Settings backup is too large" }
                bytes.toString(Charsets.UTF_8)
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (output.size() < limit) {
            val read = read(buffer, 0, minOf(buffer.size, limit - output.size()))
            if (read == -1) break
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    companion object {
        const val SCOPE = "https://www.googleapis.com/auth/drive.appdata"
        private const val FILE_NAME = "agpeya-settings-v1.json"
        private const val FILES = "https://www.googleapis.com/drive/v3/files"
        private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3/files"
    }
}

class DriveHttpException(val status: Int, message: String) : IOException(message)
