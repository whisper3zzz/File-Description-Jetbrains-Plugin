package com.whispersong.jetbrains.filedesc.config

import com.intellij.openapi.project.Project
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class FileHeaderConfig(
    val customMade: Map<String, String> = emptyMap(),
    val copyright: String = "",
    val ignorePaths: List<String> = emptyList(),
    val timeFormat: String = "yyyy-MM-dd HH:mm:ss",
    val compactComment: Boolean = false
)

@Serializable
data class FileDescriptionRoot(
    val fileheader: FileHeaderConfig = FileHeaderConfig()
)

object ProjectConfig {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val lineCommentRegex = Regex("//[^\n\r]+", RegexOption.MULTILINE)

    /** Cache project configs by project base path */
    private data class CachedConfig(val value: FileHeaderConfig?, val loadedAt: Long)

    private val configCache = ConcurrentHashMap<String, CachedConfig>()
    /** Cache TTL: 30 seconds */
    private const val CONFIG_CACHE_TTL = 30_000L

    fun loadProjectConfig(project: Project): FileHeaderConfig? {
        val basePath = project.basePath ?: return null
        return loadProjectConfig(Path.of(basePath))
    }

    fun loadProjectConfig(projectDir: Path): FileHeaderConfig? {
        val key = projectDir.toString()
        val now = System.currentTimeMillis()
        val cached = configCache[key]
        if (cached != null && now - cached.loadedAt < CONFIG_CACHE_TTL) {
            return cached.value
        }

        val result = loadProjectConfigFromDisk(projectDir)
        configCache[key] = CachedConfig(result, now)
        return result
    }

    private fun loadProjectConfigFromDisk(projectDir: Path): FileHeaderConfig? {
        val configFile = projectDir.resolve(".fileDescription.json")
        if (!Files.exists(configFile)) return null
        return try {
            val content = Files.readString(configFile)
            val root = json.decodeFromString<FileDescriptionRoot>(content)
            root.fileheader
        } catch (e: Exception) {
            null
        }
    }

    /** Clear project config cache, force reload on next access */
    fun clearCache() {
        configCache.clear()
    }

    fun parseHeaderJson(jsonStr: String): JsonObject? {
        return try {
            val element = json.parseToJsonElement(
                jsonStr.replace(lineCommentRegex, "").trim()
            )
            if (element is JsonObject) element else null
        } catch (e: Exception) {
            null
        }
    }

    fun getMergedHeaderConfig(project: Project?): JsonObject {
        val settings = FileDescSettings.getInstance()
        val settingsJson = parseHeaderJson(settings.customHeaderComment)
            ?: parseHeaderJson(FileDescSettings.DEFAULT_HEADER)!!

        if (project != null) {
            val projectConfig = loadProjectConfig(project)
            if (projectConfig != null && projectConfig.customMade.isNotEmpty()) {
                val merged = settingsJson.toMutableMap()
                for ((key, value) in projectConfig.customMade) {
                    merged[key] = JsonPrimitive(value)
                }
                return JsonObject(merged)
            }
        }

        return settingsJson
    }

    fun getMergedIgnorePaths(project: Project?): List<String> {
        val settings = FileDescSettings.getInstance()
        val basePaths = settings.ignorePaths.split(Regex("[,\r\n]")).filter { it.isNotEmpty() }

        if (project != null) {
            val projectConfig = loadProjectConfig(project)
            if (projectConfig != null && projectConfig.ignorePaths.isNotEmpty()) {
                return basePaths + projectConfig.ignorePaths
            }
        }

        return basePaths
    }

    fun getMergedTimeFormat(project: Project?): String {
        if (project != null) {
            val projectConfig = loadProjectConfig(project)
            if (projectConfig != null) {
                return projectConfig.timeFormat
            }
        }
        return FileDescSettings.getInstance().timeFormat
    }

    fun getMergedCopyright(project: Project?): String? {
        if (project != null) {
            val projectConfig = loadProjectConfig(project)
            if (projectConfig != null && projectConfig.copyright.isNotEmpty()) {
                return projectConfig.copyright
            }
        }
        return null
    }

    fun getMergedCompactComment(project: Project?): Boolean {
        if (project != null) {
            val projectConfig = loadProjectConfig(project)
            if (projectConfig != null) {
                return projectConfig.compactComment
            }
        }
        return FileDescSettings.getInstance().compactComment
    }
}
