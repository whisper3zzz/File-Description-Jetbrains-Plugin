package com.whispersong.jetbrains.filedesc.utils

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.whispersong.jetbrains.filedesc.config.FileDescSettings
import com.whispersong.jetbrains.filedesc.config.ProjectConfig
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.ConcurrentHashMap

object CommentGenerator {

    private val FIELD_ORDER = listOf(
        "Copyright", "Author", "Date", "LastEditors", "LastEditTime", "FilePath", "Description"
    )
    private const val MAX_HEADER_SCAN_LENGTH = 64 * 1024
    private const val INTERNAL_UPDATE_SUPPRESS_MS = 1_000L
    private val activeInternalUpdates = ConcurrentHashMap.newKeySet<String>()
    private val recentInternalUpdates = ConcurrentHashMap<String, Long>()

    fun isInternalUpdate(file: VirtualFile): Boolean {
        val path = file.path
        if (activeInternalUpdates.contains(path)) return true

        val lastUpdate = recentInternalUpdates[path] ?: return false
        val isRecent = System.currentTimeMillis() - lastUpdate < INTERNAL_UPDATE_SUPPRESS_MS
        if (!isRecent) {
            recentInternalUpdates.remove(path, lastUpdate)
        }
        return isRecent
    }

    fun generateHeaderComment(file: VirtualFile, project: Project?): String {
        val style = FileUtil.getCommentStyle(file) ?: return ""
        val config = ProjectConfig.getMergedHeaderConfig(project)
        val timeFormat = ProjectConfig.getMergedTimeFormat(project)
        val copyrightField = ProjectConfig.getMergedCopyright(project)

        val curDate = SimpleDateFormat(timeFormat).format(Date())
        val createDate = try {
            FileUtil.getFileCreationTime(file, timeFormat)
        } catch (e: Exception) {
            curDate
        }
        val filePath = FileUtil.getRelativePath(file)
        val at = style.atSymbol

        val commentContent = StringBuilder()

        // 1. Copyright — always first, resolve placeholders
        val copyrightValue = if (!copyrightField.isNullOrEmpty()) {
            resolvePlaceholders(copyrightField, file)
        } else if (config.containsKey("Copyright")) {
            val raw = (config["Copyright"] as? JsonPrimitive)?.content ?: ""
            resolvePlaceholders(raw, file)
        } else ""
        if (copyrightValue.isNotEmpty()) {
            commentContent.append("${style.linePrefix}${at}Copyright: $copyrightValue\n")
        }

        // 2. Standard fields in fixed order
        for (key in FIELD_ORDER) {
            if (key == "Copyright") continue
            if (!config.containsKey(key)) continue
            val strValue = (config[key] as? JsonPrimitive)?.content ?: continue

            val line = when (key) {
                "Author" -> "${style.linePrefix}${at}Author: ${resolveValue(strValue, file).orEmpty()}\n"
                "Date" -> "${style.linePrefix}${at}Date: $createDate\n"
                "LastEditors" -> "${style.linePrefix}${at}LastEditors: ${resolveValue(strValue, file).orEmpty()}\n"
                "LastEditTime" -> "${style.linePrefix}${at}LastEditTime: $curDate\n"
                "FilePath" -> {
                    val formattedPath = "\\${filePath.replace("/", "\\")}"
                    "${style.linePrefix}${at}FilePath: $formattedPath\n"
                }
                "Description" -> "${style.linePrefix}${at}Description: $strValue\n"
                else -> continue
            }
            commentContent.append(line)
        }

        // 3. Custom uppercase fields not in FIELD_ORDER
        for ((key, value) in config) {
            if (key in FIELD_ORDER || key == "Copyright") continue
            if (key.firstOrNull()?.isUpperCase() != true) {
                val strValue = (value as? JsonPrimitive)?.content ?: continue
                if (strValue.isNotEmpty()) {
                    commentContent.append("${style.linePrefix}$strValue\n")
                }
                continue
            }
            val strValue = (value as? JsonPrimitive)?.content ?: continue
            val resolved = resolveValue(strValue, file).orEmpty()
            if (resolved.isNotEmpty()) {
                commentContent.append("${style.linePrefix}${at}$key: $resolved\n")
            }
        }

        if (commentContent.isEmpty()) return ""

        val start = style.start ?: ""
        val end = style.end ?: ""
        return start + commentContent + end
    }

    fun checkAndAddHeader(document: Document, file: VirtualFile, project: Project?) {
        val content = getHeaderScanText(document)
        if (findHeaderRange(file, content) != null) return

        val header = generateHeaderComment(file, project)
        if (header.isEmpty()) return

        val position = findHeaderInsertionOffset(file, content)
        val contentToInsert = if (FileUtil.getFileExtension(file) == "php" && !hasPhpOpeningTag(content)) {
            "<?php\n$header"
        } else {
            header
        }

        insertFileContent(file, document, position, contentToInsert)
    }

    fun updateHeader(document: Document, file: VirtualFile, project: Project?) {
        val content = getHeaderScanText(document)
        val style = FileUtil.getCommentStyle(file) ?: return
        val headerRange = findHeaderRange(file, content) ?: return

        val config = ProjectConfig.getMergedHeaderConfig(project)
        val timeFormat = ProjectConfig.getMergedTimeFormat(project)
        val curDate = SimpleDateFormat(timeFormat).format(Date())
        val filePath = FileUtil.getRelativePath(file)

        var newHeader = content.substring(headerRange.startOffset, headerRange.endOffset)

        // Update LastEditors
        if (config.containsKey("LastEditors")) {
            val value = (config["LastEditors"] as? JsonPrimitive)?.content ?: ""
            val resolved = resolveValue(value, file, cacheOnly = true)
            if (resolved != null) {
                newHeader = replaceHeaderField(newHeader, style, "LastEditors", resolved)
            }
        }

        // Update LastEditTime
        newHeader = replaceHeaderField(newHeader, style, "LastEditTime", curDate)

        // Update FilePath
        if (config.containsKey("FilePath")) {
            val formattedPath = "\\${filePath.replace("/", "\\")}"
            newHeader = replaceHeaderField(newHeader, style, "FilePath", formattedPath)
        }

        if (newHeader != content.substring(headerRange.startOffset, headerRange.endOffset)) {
            replaceFileContent(file, document, headerRange, newHeader)
        }
    }

    fun hasHeaderComment(file: VirtualFile, content: String): Boolean {
        return findHeaderRange(file, content) != null
    }

    private fun findHeaderRange(file: VirtualFile, content: String): TextRange? {
        val style = FileUtil.getCommentStyle(file) ?: return null
        val headerStart = findHeaderInsertionOffset(file, content)

        if (style.start == null || style.end == null) {
            return findLineCommentHeaderRange(content, headerStart, style)
        }

        val startToken = style.start.trim()
        if (!content.regionMatches(headerStart, startToken, 0, startToken.length)) return null

        val endToken = style.end.trim()
        val endIndex = content.indexOf(endToken, headerStart + startToken.length)
        if (endIndex < 0) return null

        val rangeEnd = consumeLineBreak(content, endIndex + endToken.length)
        return TextRange(headerStart, rangeEnd)
    }

    private fun findLineCommentHeaderRange(
        content: String,
        headerStart: Int,
        style: FileUtil.CommentStyle
    ): TextRange? {
        val marker = style.linePrefix.trimEnd()
        if (marker.isEmpty() || !content.startsWith(marker, headerStart)) return null

        var offset = headerStart
        var end = headerStart
        val text = StringBuilder()
        while (offset < content.length && content.startsWith(marker, offset)) {
            val lineEnd = findLineEnd(content, offset)
            val nextOffset = consumeLineBreak(content, lineEnd)
            text.append(content, offset, nextOffset)
            end = nextOffset
            offset = nextOffset
        }

        val headerText = text.toString()
        if (!looksLikeGeneratedHeader(headerText)) return null
        return TextRange(headerStart, end)
    }

    private fun looksLikeGeneratedHeader(text: String): Boolean {
        return FIELD_ORDER.any { text.contains("$it:") }
    }

    private fun replaceHeaderField(
        header: String,
        style: FileUtil.CommentStyle,
        key: String,
        value: String
    ): String {
        val pattern = Regex(
            "(?m)^${Regex.escape(style.linePrefix)}${Regex.escape(style.atSymbol)}${Regex.escape(key)}:[^\\r\\n]*(\\r\\n|\\n|\\r)?"
        )
        return pattern.replace(header) { match ->
            val newline = Regex("(\\r\\n|\\n|\\r)$").find(match.value)?.value ?: "\n"
            "${style.linePrefix}${style.atSymbol}$key: $value$newline"
        }
    }

    private fun findHeaderInsertionOffset(
        file: VirtualFile,
        content: String,
    ): Int {
        if (FileUtil.getFileExtension(file) == "php") {
            val phpTag = Regex("\\A\\s*<\\?php\\b[^\\r\\n]*(\\r\\n|\\n|\\r)?").find(content)
            if (phpTag != null) return phpTag.value.length
        }

        return firstSignificantOffset(content)
    }

    private fun hasPhpOpeningTag(content: String): Boolean {
        return Regex("\\A\\s*<\\?php\\b").containsMatchIn(content)
    }

    private fun firstSignificantOffset(content: String): Int {
        var offset = 0
        if (content.startsWith("\uFEFF")) offset = 1
        while (offset < content.length && content[offset].isWhitespace()) {
            offset++
        }
        return offset
    }

    private fun findLineEnd(content: String, offset: Int): Int {
        val n = content.indexOf('\n', offset).let { if (it == -1) content.length else it }
        val r = content.indexOf('\r', offset).let { if (it == -1) content.length else it }
        return minOf(n, r)
    }

    private fun consumeLineBreak(content: String, offset: Int): Int {
        if (offset >= content.length) return offset
        return when {
            content[offset] == '\r' && offset + 1 < content.length && content[offset + 1] == '\n' -> offset + 2
            content[offset] == '\r' || content[offset] == '\n' -> offset + 1
            else -> offset
        }
    }

    private fun getHeaderScanText(document: Document): String {
        val length = minOf(document.textLength, MAX_HEADER_SCAN_LENGTH)
        return document.getText(TextRange(0, length))
    }

    private fun resolveValue(value: String, file: VirtualFile, cacheOnly: Boolean = false): String? {
        return when {
            value == "auto:vcs" -> {
                val manual = FileDescSettings.getInstance().manualAuthor
                if (manual.isNotEmpty()) return manual

                val projectDir = FileUtil.getProjectDir(file)
                if (cacheOnly) {
                    if (projectDir == null) return VcsDetector.getVcsUsername(VcsType.NONE, null)

                    val vcs = VcsDetector.getCachedVcs(projectDir)
                    if (vcs == null) {
                        VcsDetector.warmUp(projectDir)
                        return null
                    }

                    val cached = VcsDetector.getCachedVcsUsernameEmail(vcs, projectDir)
                        ?: VcsDetector.getCachedVcsUsername(vcs, projectDir)
                    if (cached == null) {
                        VcsDetector.warmUp(projectDir)
                    }
                    cached
                } else {
                    val vcs = if (projectDir != null) VcsDetector.detectVcs(projectDir) else VcsType.NONE
                    VcsDetector.getVcsUsernameEmail(vcs, projectDir) ?: VcsDetector.getVcsUsername(vcs, projectDir) ?: "unknown"
                }
            }
            value.startsWith("git ") || value.startsWith("git\t") -> {
                if (cacheOnly) null else executeCommand(value, file).orEmpty()
            }
            value == "" -> ""
            else -> resolvePlaceholders(value, file)
        }
    }

    private fun resolvePlaceholders(text: String, file: VirtualFile): String {
        var result = text
        val year = SimpleDateFormat("yyyy").format(Date())

        val needsGitName = result.contains("\${git_name}") ||
                result.contains("\${git_email}") ||
                result.contains("\${git_name_email}")
        val needsGitNameEmail = result.contains("\${git_email}") ||
                result.contains("\${git_name_email}")

        var vcsName = ""
        var vcsNameEmail = ""
        if (needsGitName) {
            val projectDir = FileUtil.getProjectDir(file)
            val vcs = if (projectDir != null) VcsDetector.detectVcs(projectDir) else VcsType.NONE
            vcsName = VcsDetector.getVcsUsername(vcs, projectDir) ?: ""
            if (needsGitNameEmail) {
                vcsNameEmail = VcsDetector.getVcsUsernameEmail(vcs, projectDir) ?: ""
            }
        }
        val email = vcsNameEmail.removePrefix(vcsName).trim()

        result = result.replace("\${now_year}", year)
        result = result.replace("\${git_name}", vcsName)
        result = result.replace("\${git_email}", email)
        result = result.replace("\${git_name_email}", vcsNameEmail)
        return result
    }

    private fun executeCommand(command: String, file: VirtualFile): String? {
        val parts = command.trim().split(Regex("\\s+"))
        if (parts.isEmpty()) return null
        return VcsDetector.runCommand(FileUtil.getProjectDir(file), parts)
    }

    private fun insertFileContent(file: VirtualFile, document: Document, offset: Int, content: String) {
        val project = FileUtil.findProjectForFile(file) ?: return
        WriteCommandAction.runWriteCommandAction(project) {
            try {
                runInternalUpdate(file) {
                    document.insertString(offset, content)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun replaceFileContent(file: VirtualFile, document: Document, range: TextRange, content: String) {
        val project = FileUtil.findProjectForFile(file) ?: return
        WriteCommandAction.runWriteCommandAction(project) {
            try {
                runInternalUpdate(file) {
                    document.replaceString(range.startOffset, range.endOffset, content)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun runInternalUpdate(file: VirtualFile, action: () -> Unit) {
        val path = file.path
        activeInternalUpdates.add(path)
        try {
            action()
        } finally {
            recentInternalUpdates[path] = System.currentTimeMillis()
            activeInternalUpdates.remove(path)
        }
    }
}
