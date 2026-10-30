package com.mina.agpeya.data

import com.mina.agpeya.SettingsDraft
import org.json.JSONArray
import org.json.JSONObject

object SettingsBackup {
    const val MAX_BYTES = 256 * 1024

    fun encode(settings: SettingsDraft): String {
        val font = settings.font
        return JSONObject()
            .put("version", 1)
            .put("pagedReading", settings.pagedReading)
            .put("font", JSONObject()
                .put("scale", font.scale)
                .put("family", font.family.name)
                .put("style", font.style.name)
                .put("color", font.color)
                .put("shadowColor", font.shadowColor ?: JSONObject.NULL)
                .put("background", font.background.name)
                .put("backgroundTransparency", font.backgroundTransparency)
                .put("backgroundContrast", font.backgroundContrast))
            .put("prayers", JSONArray().apply {
                settings.prayers.forEach { prayer ->
                    put(JSONObject()
                        .put("key", prayer.key)
                        .put("enabled", prayer.enabled)
                        .put("time", prayer.time)
                        .put("volume", prayer.volume)
                        .put("psalmMode", prayer.psalmMode.name)
                        .put("psalmCount", prayer.psalmCount)
                        .put("fixedPsalmKeys", JSONArray(prayer.fixedPsalmKeys.sorted()))
                        .put("sequentialOffset", prayer.sequentialOffset))
                }
            }).toString().also {
                require(it.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Settings backup is too large" }
            }
    }

    fun decode(json: String, local: SettingsDraft): SettingsDraft {
        require(json.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Settings backup is too large" }
        val root = JSONObject(json)
        require(root.getInt("version") == 1) { "Unsupported settings backup version" }
        val font = root.getJSONObject("font")
        val scale = font.getDouble("scale").toFloat()
        val transparency = font.getDouble("backgroundTransparency").toFloat()
        val contrast = font.getDouble("backgroundContrast").toFloat()
        require(scale.isFinite() && scale in 0.8f..1.6f) { "Invalid font scale" }
        require(transparency.isFinite() && transparency in 0f..1f) { "Invalid transparency" }
        require(contrast.isFinite() && contrast in 0.5f..2f) { "Invalid contrast" }
        val prayers = root.getJSONArray("prayers")
        val restored = (0 until prayers.length()).map { index ->
            val prayer = prayers.getJSONObject(index)
            val key = prayer.getString("key")
            val current = local.prayers.firstOrNull { it.key == key }
                ?: error("Unknown prayer in settings backup")
            val time = prayer.getString("time")
            val volume = prayer.getDouble("volume").toFloat()
            val count = prayer.getInt("psalmCount")
            val offset = prayer.getInt("sequentialOffset")
            require(Regex("([01][0-9]|2[0-3]):[0-5][0-9]").matches(time)) { "Invalid prayer time" }
            require(volume.isFinite() && volume in 0f..1f) { "Invalid alarm volume" }
            require(count >= 1 && offset >= 0) { "Invalid psalm selection" }
            val fixed = prayer.getJSONArray("fixedPsalmKeys")
            current.copy(
                enabled = prayer.getBoolean("enabled"),
                time = time,
                volume = volume,
                psalmMode = PsalmMode.valueOf(prayer.getString("psalmMode")),
                psalmCount = count,
                fixedPsalmKeys = (0 until fixed.length()).map { fixed.getString(it) }.toSet(),
                sequentialOffset = offset,
            )
        }
        require(restored.size == local.prayers.size &&
            restored.map { it.key }.toSet() == local.prayers.map { it.key }.toSet()) {
            "Settings backup has missing or duplicate prayers"
        }
        return SettingsDraft(
            prayers = local.prayers.map { prayer -> restored.first { it.key == prayer.key } },
            font = ReaderFontSettings(
                scale = scale,
                family = ReaderFontFamily.valueOf(font.getString("family")),
                style = ReaderFontStyle.valueOf(font.getString("style")),
                color = font.getInt("color"),
                shadowColor = if (font.isNull("shadowColor")) null else font.getInt("shadowColor"),
                background = ReaderBackground.valueOf(font.getString("background")),
                backgroundTransparency = transparency,
                backgroundContrast = contrast,
            ),
            pagedReading = root.getBoolean("pagedReading"),
        )
    }
}
