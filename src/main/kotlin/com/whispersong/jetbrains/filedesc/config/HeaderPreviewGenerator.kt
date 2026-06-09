package com.whispersong.jetbrains.filedesc.config

import com.whispersong.jetbrains.filedesc.utils.FileUtil
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.text.SimpleDateFormat
import java.util.Date

object HeaderPreviewGenerator {
    private val fieldOrder = listOf(
        "Copyright", "Author", "Date", "LastEditors", "LastEditTime", "FilePath", "Description"
    )

    private const val sampleFileName = "Example.kt"
    private const val sampleExtension = "kt"
    private const val sampleFilePath = "\\src\\main\\kotlin\\Example.kt"
    private const val sampleEmail = "you@example.com"

    fun generate(
        headerJson: String,
        timeFormat: String,
        manualAuthor: String,
        compactComment: Boolean,
        now: Date = Date()
    ): String {
        val config = ProjectConfig.parseHeaderJson(headerJson)
            ?: return "JSON format is invalid."
        val style = FileUtil.getCommentStyle(sampleFileName, sampleExtension, compactComment)
            ?: return ""
        val formattedDate = formatDate(timeFormat, now)
        val year = SimpleDateFormat("yyyy").format(now)
        val gitName = manualAuthor.ifBlank { "your-name" }
        val gitNameEmail = "$gitName <$sampleEmail>"

        val content = StringBuilder()
        appendCopyright(config, content, style, gitName, gitNameEmail, year)

        for (key in fieldOrder) {
            if (key == "Copyright") continue
            val value = (config[key] as? JsonPrimitive)?.content ?: continue
            val line = when (key) {
                "Author" -> "${style.linePrefix}${style.atSymbol}Author: ${resolveValue(value, gitName, gitNameEmail, year)}\n"
                "Date" -> "${style.linePrefix}${style.atSymbol}Date: $formattedDate\n"
                "LastEditors" -> "${style.linePrefix}${style.atSymbol}LastEditors: ${resolveValue(value, gitName, gitNameEmail, year)}\n"
                "LastEditTime" -> "${style.linePrefix}${style.atSymbol}LastEditTime: $formattedDate\n"
                "FilePath" -> "${style.linePrefix}${style.atSymbol}FilePath: $sampleFilePath\n"
                "Description" -> "${style.linePrefix}${style.atSymbol}Description: $value\n"
                else -> continue
            }
            content.append(line)
        }

        for ((key, value) in config) {
            if (key in fieldOrder || key == "Copyright") continue
            val strValue = (value as? JsonPrimitive)?.content ?: continue
            if (key.firstOrNull()?.isUpperCase() == true) {
                val resolved = resolveValue(strValue, gitName, gitNameEmail, year)
                if (resolved.isNotEmpty()) {
                    content.append("${style.linePrefix}${style.atSymbol}$key: $resolved\n")
                }
            } else if (strValue.isNotEmpty()) {
                content.append("${style.linePrefix}$strValue\n")
            }
        }

        if (content.isEmpty()) return ""
        return (style.start ?: "") + content + (style.end ?: "")
    }

    private fun appendCopyright(
        config: JsonObject,
        content: StringBuilder,
        style: FileUtil.CommentStyle,
        gitName: String,
        gitNameEmail: String,
        year: String
    ) {
        val raw = (config["Copyright"] as? JsonPrimitive)?.content ?: return
        val resolved = resolveValue(raw, gitName, gitNameEmail, year)
        if (resolved.isNotEmpty()) {
            content.append("${style.linePrefix}${style.atSymbol}Copyright: $resolved\n")
        }
    }

    private fun resolveValue(value: String, gitName: String, gitNameEmail: String, year: String): String {
        if (value == "auto:vcs") return gitNameEmail
        if (value.startsWith("git ") || value.startsWith("git\t")) return "<git command output>"
        return value
            .replace("\${now_year}", year)
            .replace("\${git_name_email}", gitNameEmail)
            .replace("\${git_name}", gitName)
            .replace("\${git_email}", sampleEmail)
    }

    private fun formatDate(timeFormat: String, now: Date): String {
        return try {
            SimpleDateFormat(timeFormat.ifBlank { FileHeaderConfig().timeFormat }).format(now)
        } catch (e: IllegalArgumentException) {
            SimpleDateFormat(FileHeaderConfig().timeFormat).format(now)
        }
    }
}
