package com.whispersong.jetbrains.filedesc.config

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

data class ImportedFileDescriptionSettings(
    val customHeaderComment: String,
    val ignorePaths: String,
    val timeFormat: String,
    val compactComment: Boolean
)

object SettingsConfigIO {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
    }

    fun exportProjectConfig(
        customHeaderComment: String,
        ignorePaths: String,
        timeFormat: String,
        compactComment: Boolean
    ): String {
        val headerConfig = ProjectConfig.parseHeaderJson(customHeaderComment)
            ?: throw IllegalArgumentException("自定义头部注释配置 JSON 格式不正确")
        val customMade = headerConfig.toMutableMap()
        val copyright = (customMade.remove("Copyright") as? JsonPrimitive)?.content.orEmpty()

        val root = FileDescriptionRoot(
            fileheader = FileHeaderConfig(
                customMade = customMade.mapValues { (_, value) -> value.jsonPrimitive.content },
                copyright = copyright,
                ignorePaths = splitIgnorePaths(ignorePaths),
                timeFormat = timeFormat.ifBlank { FileHeaderConfig().timeFormat },
                compactComment = compactComment
            )
        )

        return json.encodeToString(root)
    }

    fun importProjectConfig(content: String): ImportedFileDescriptionSettings {
        val root = json.decodeFromString<FileDescriptionRoot>(content)
        val fileheader = root.fileheader
        val headerFields = mutableMapOf<String, JsonPrimitive>()

        val defaultHeader = ProjectConfig.parseHeaderJson(FileDescSettings.DEFAULT_HEADER).orEmpty()
        for ((key, value) in defaultHeader) {
            headerFields[key] = value.jsonPrimitive
        }
        for ((key, value) in fileheader.customMade) {
            headerFields[key] = JsonPrimitive(value)
        }
        if (fileheader.copyright.isNotEmpty()) {
            headerFields["Copyright"] = JsonPrimitive(fileheader.copyright)
        }

        return ImportedFileDescriptionSettings(
            customHeaderComment = json.encodeToString(JsonObject(headerFields)),
            ignorePaths = fileheader.ignorePaths.joinToString("\n"),
            timeFormat = fileheader.timeFormat,
            compactComment = fileheader.compactComment
        )
    }

    private fun splitIgnorePaths(ignorePaths: String): List<String> {
        return ignorePaths
            .split(Regex("[,\r\n]"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }
}
